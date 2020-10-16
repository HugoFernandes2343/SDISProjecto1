import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Channel implements Runnable{

    public enum ChannelType {
        MC, MDB, MDR
    }

    private static final int MAX_MESSAGE_SIZE = 65000;
    private MulticastSocket socket;
    private InetAddress mcastAddr;
    private int mcastPort;
    private Peer parentPeer;

    public Channel(Peer parentPeer, String mcastAddr, String mcastPort) {
        this.parentPeer = parentPeer;

        try {
            this.mcastAddr = InetAddress.getByName(mcastAddr);
            this.mcastPort = Integer.parseInt(mcastPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        initialize();
    }

    private void initialize() {
        try {
            socket = new MulticastSocket(mcastPort);
            socket.setTimeToLive(1);
            socket.joinGroup(mcastAddr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        byte[] rbuf;
        while (true) {
            try {
                rbuf = new byte[MAX_MESSAGE_SIZE];
                DatagramPacket packet = new DatagramPacket(rbuf, rbuf.length);
                this.socket.receive(packet);
                MessageManagement messageManagement = new MessageManagement(packet);
                new Thread(messageManagement).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    synchronized public void sendMessage(byte[] message){
        DatagramPacket packet = new DatagramPacket(message, message.length, mcastAddr, mcastPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        socket.close();
    }

}
