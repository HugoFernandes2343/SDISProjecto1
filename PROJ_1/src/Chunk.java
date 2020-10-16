import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Chunk implements Serializable {

    private byte[] data;
    private String fileID;
    private int number;
    private int replicationDegree;
    private int size;
    private Set<Integer> mirrors;


    public static final int SIZE_LIMIT = 64000;

    public Chunk (int number, String fileID, byte[] data, int replicationDegree){
        this.data = data;
        this.size=data.length / 1024;
        this.fileID = fileID;
        this.number = number;
        this.replicationDegree = replicationDegree;
        this.mirrors = new HashSet<>();
    }

    public byte[] getData() {
        return data;
    }

    public String getFileID() {
        return fileID;
    }

    public int getNumber() {
        return number;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }

    public boolean removeMirror(Integer peerID) {
        return mirrors.remove(peerID);
    }

    public boolean addMirror(Integer peerID) {
        return mirrors.add(peerID);
    }

    public int getNumMirrors() {
        return mirrors.size();
    }

    public int getSize() {
        return size;
    }

    public void setData(byte[] chunkData) {
        this.data=chunkData;
    }
}
