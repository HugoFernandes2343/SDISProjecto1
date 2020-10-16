public class MCChannel extends Channel{
    public MCChannel(Peer parentPeer, String mcastAddr, String mcastPort) {
        super(parentPeer, mcastAddr, mcastPort);
        System.out.println("MCChannel initialized");
    }
}
