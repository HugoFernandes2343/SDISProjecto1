import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ResponseManagement implements Runnable{

    private byte[] msg;
    private String tag;
    private int repDegree;
    private int numberOfAtemtps;

    public ResponseManagement (int repDegree, String tag, byte[] msg){
        this.msg = msg;
        this.tag = tag;
        this.repDegree = repDegree;
        this.numberOfAtemtps = 1;
    }

    @Override
    public void run() {
        int i = 100;
        int chunkTimesInSystem = 0;
        do{
            try {
                Thread.sleep(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            chunkTimesInSystem = Peer.getStorage().getReplicationsInSystem(this.tag);
            if(chunkTimesInSystem >= repDegree ){
                return;
            }
            String s = new String(msg);
            Peer.getChannel(Channel.ChannelType.MDB).sendMessage(msg);

            i = i*2;
            numberOfAtemtps++;
        }while(chunkTimesInSystem < repDegree || numberOfAtemtps != 5);
    }
}
