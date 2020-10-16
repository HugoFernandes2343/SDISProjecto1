import java.io.*;
import java.net.DatagramPacket;
import java.net.FileNameMap;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MessageManagement  implements Runnable{

    private DatagramPacket datagramPacket;
    private String[] msgHeader;

    public MessageManagement(DatagramPacket datagramPacket) {
        this.datagramPacket = datagramPacket;
    }

    @Override
    public void run() {
        String s = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
        msgHeader = s.split(" ");
        //verify messages from the same peer and null messages
        if (Integer.parseInt(msgHeader[2]) == Peer.getID() && !msgHeader[1].equals("DELETE")){
            return;
        }

        //get the type of message and execute the correct following method
        String msgType = msgHeader[1];

        switch (msgType){
            case "PUTCHUNK":
                putChunk();
                break;
            case "GETCHUNK":
                getChunk();
                break;
            case "CHUNK":
                chunk();
                break;
            case "STORED":
                stored();
                break;
            case "DELETE":
                delete();
                break;
            case "REMOVED":
                removed();
                break;
            default:
                break;

        }
    }

    private void putChunk() {

        for(File f: Peer.getStorage().getFiles()){
            String fileId = Util.sha256(f.getName() + f.lastModified());
            if(fileId.equals(msgHeader[3]));
            {
                return;
            }
        }
        //verifying if there is space for the chunk
        byte[] data = getDatagramData();
        if(!Peer.getStorage().checkForSpace(data.length/1024) || data.length/1024<1){
            return;
        }else{
            //save the data transmitted in the PUTCHUNK Message
            String version = msgHeader[0];
            String peerId = msgHeader[2];
            String fileId = msgHeader[3];
            int chunkNo = Integer.parseInt(msgHeader[4]);
            int repDegree = Integer.parseInt(msgHeader[5]);

            System.out.println("Received " + msgHeader[0] + " " + msgHeader[1] + " " + peerId + " " + fileId + " " + chunkNo + " " + repDegree);

            //creating the chunk and file of said chunk
            new File("./fileSystem/" + Peer.getID() + "/backup/" + fileId).mkdirs();
            try {
                FileOutputStream chunkFile = new FileOutputStream("./fileSystem/" + Peer.getID() + "/backup/" + fileId  + "/chunk" + chunkNo,false );
                ObjectOutputStream out =  new ObjectOutputStream(chunkFile);
                Chunk chk =  new Chunk(chunkNo,fileId,data,repDegree);

                out.writeObject(chk);
                out.close();
                chunkFile.close();

                //Handle  storage
                Peer.getStorage().addToSystemReplications(fileId + "_" + chunkNo );
                Peer.getStorage().saveChunk(chk);
                Peer.getStorage().addToMap(chk);
                Peer.getStorage().updateSpace();

            } catch (IOException e) {
                e.printStackTrace();
            }

            //Final part creating and sending the STORED message
            String msg = version + " " + "STORED" + " " + Peer.getID() + " " + fileId + " " + chunkNo + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

            //Waiting for the random amount of milliseconds
            try {
                Thread.sleep((long)(Math.random()*400));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Peer.getChannel(Channel.ChannelType.MC).sendMessage(msg.getBytes());

        }
    }

    private void getChunk() {
        String peerId = msgHeader[2];
        String fileId = msgHeader[3];
        int chunkNo = Integer.parseInt(msgHeader[4]);
        System.out.println("Received " + msgHeader[0] + " " + msgHeader[1] + " " + peerId + " " + fileId + " " + chunkNo);
        Chunk chunk=null;
        try {
            chunk = loadChunk(fileId, chunkNo);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        String header = Peer.getProtocolVersion() + " CHUNK" + " " + Peer.getID() + " " + fileId + " " +chunkNo + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;
        ByteArrayOutputStream outputMessageStream = new ByteArrayOutputStream();

        try {
            outputMessageStream.write(Arrays.copyOf(header.getBytes(), header.length()));
            outputMessageStream.write(Arrays.copyOf(chunk.getData(), chunk.getData().length));
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] msg = outputMessageStream.toByteArray();

        try {
            Thread.sleep((long)(Math.random() * 400));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Peer.getChannel(Channel.ChannelType.MDR).sendMessage(msg);
    }

    public static Chunk loadChunk(String fileId, int chunkNo) throws IOException, ClassNotFoundException {

        Chunk chunk = null;
        FileInputStream fileIn = new FileInputStream("./fileSystem/" + Peer.getID() + "/backup/" + fileId  + "/chunk" + chunkNo);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        chunk = (Chunk) in.readObject();
        in.close();
        fileIn.close();

        return chunk;
    }


    private void chunk() {
        String peerId = msgHeader[2];
        String fileId = msgHeader[3];
        int chunkNo = Integer.parseInt(msgHeader[4]);
        System.out.println("Received " + msgHeader[0] + " " + msgHeader[1] + " " + peerId + " " + fileId + " " + chunkNo);
        byte[] data = getDatagramData();
        Chunk chunk = new Chunk(chunkNo, fileId, data, 0);
        Peer.getStorage().receiveChunk(chunk);

    }

    public byte[] getDatagramData(){
        int headerLength = 0;
        for (int i = 0; i < datagramPacket.getData().length; i++) {
            if ((datagramPacket.getData()[i] == (char) 0xD) && (datagramPacket.getData()[i + 1] == (char) 0xA) &&
                    (datagramPacket.getData()[i+2] == (char) 0xD) && (datagramPacket.getData()[i + 3] == (char) 0xA)) {
                break;
            }
            headerLength++;
        }
    return Arrays.copyOfRange(datagramPacket.getData(), headerLength + 4, datagramPacket.getLength());
    }

    private void stored() {
        //data recieved
        String version = msgHeader[0];
        String senderPeerId = msgHeader[2];
        String fileId = msgHeader[3];
        int chunkNo = Integer.parseInt(msgHeader[4]);

        System.out.println("Received " + version + " " + "STORED " + senderPeerId + " " + fileId + " " + chunkNo);
        Peer.getStorage().addToSystemReplications(fileId + "_" + chunkNo);

    }

    private void delete() {

        //getting the id of the file
        String fileId = msgHeader[3];
        System.out.println("Received " + msgHeader[0] + " " + "DELETE " + msgHeader[2] + " " + fileId);

        //checking for the directory
        File dir = new File("fileSystem/" + Peer.getID() +"/backup/" + fileId);

        if(dir.exists()){

            //delete the file chunks in the storage
            Peer.getStorage().deleteChunksFromFile(fileId);

            //get all the content of the file directory
            for (File f: dir.listFiles()) {
                f.delete();
            }

            dir.delete();
        }else{
            System.out.println("Directory is not present");
        }

    }

    private void removed() {
        //data recieved
        String version = msgHeader[0];
        String senderPeerId = msgHeader[2];
        String fileId = msgHeader[3];
        int chunkNo = Integer.parseInt(msgHeader[4]);
        System.out.println("Received " + version + " " + "REMOVED " + senderPeerId + " " + fileId + " " + chunkNo);

        //If this peer has the chunk in its files already it will ignore the message and reduce the reps in the system
        File f = new File ("./fileSystem/" + Peer.getID() + "/backup/" + fileId  + "/chunk" + chunkNo);
        if(f.exists()){
            //reduce the reps in the system
            String tag = fileId+ "_"+chunkNo;
            Peer.getStorage().subtracToSystemReplications(tag);
            int currentReps = Peer.getStorage().getReplicationsInSystem(tag);
            if(currentReps < Peer.getStorage().getChunkRepDegreeMap().get(tag)){

                int desiredRepDegree = Peer.getStorage().getChunkRepDegreeMap().get(tag);

                FileInputStream st = null;
                try {
                    st = new FileInputStream(f);

                    BufferedInputStream bst = new BufferedInputStream(st);

                    byte[] buff = new byte[1000 * 64];
                    int i;
                    while ((i = bst.read(buff)) > 0) {
                        //getting variables to instantiate the chunk
                        byte[] data = Arrays.copyOf(buff, i);

                        //Create PUTCHUNK Message
                        String header = Peer.getProtocolVersion() + " " + "PUTCHUNK " + Peer.getID() + " " + fileId + " " + chunkNo + " " + desiredRepDegree + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

                        byte[] allByteArray = new byte[header.getBytes().length + data.length];

                        ByteBuffer byteBuffer = ByteBuffer.wrap(allByteArray);
                        byteBuffer.put(header.getBytes());
                        byteBuffer.put(data);

                        byte[] msg = byteBuffer.array();

                        try {
                            Thread.sleep((long)(Math.random() * 400));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //send PUTCHUNK
                        ResponseManagement thread = new ResponseManagement(desiredRepDegree, fileId + "_" + chunkNo, msg);
                        new Thread(thread).start();
                        buff =new byte[1000 * 64];

                    }
                }catch(IOException e){
                    e.printStackTrace();
                }

            }
        }
    }


}
