import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Storage implements Serializable {


    public ArrayList<Chunk> receivedChunks;
    public ArrayList<File> files;

    /**
     * Key fileID
     * value chunk numbers from this file in this peer
     */
    public Map<String,ArrayList<Integer>> myChunks;
    /**
     * key fileID_ChunkNo
     * value is wanted replication degree
     */
    private Map<String,Integer> chunkRepDegreeMap;


    /*
     * key = fileID_ChunkNo
     * value = number of chunk replications in system
     */
    private Map<String, Integer> replicationsInSystem;

    private int spaceAvailable;
    private int totalSpace;


    public Storage(){
        this.totalSpace = 1000000;
        this.spaceAvailable = totalSpace;
        updateSpace();
        myChunks = new HashMap<>();
        receivedChunks = new ArrayList<>();
        files = new ArrayList<>();
        chunkRepDegreeMap = new HashMap<>();
        replicationsInSystem = new HashMap<>();
    }

    public int getSpaceAvailable() {
        updateSpace();
        return this.spaceAvailable;
    }

    public void setSpaceAvailable(int spaceAvailable) {
        this.spaceAvailable = spaceAvailable;
    }

    public int getSpaceOccupied(){
        int spaceOccupied = this.totalSpace - this.spaceAvailable;
        return spaceOccupied;
    }

    public void addToMap(Chunk chk) {
        chunkRepDegreeMap.put(chk.getFileID() + "_" +chk.getNumber(), chk.getReplicationDegree());
    }

    public void saveChunk(Chunk chk) {
        ArrayList<Integer> temp=new ArrayList<>();
        if(myChunks.containsKey(chk.getFileID())){
            temp = myChunks.get(chk.getFileID());
        }
        temp.add(chk.getNumber());
        myChunks.put(chk.getFileID(),temp);
    }

    public void updateSpace(){
        File folder = new File("fileSystem/"+ Peer.getID() +"/backup");
        int size = 0;
        if(folder.exists()) {
            for (int i = 0; i < folder.listFiles().length; i++) {
                try {
                    for (File f : folder.listFiles()[i].listFiles()) {
                        size = (int) (size + f.length());
                    }
                } catch (NullPointerException e) {
                    System.out.println("folder has no files");
                }
            }

            this.spaceAvailable = (totalSpace - (size / 1024));
        }
    }

    public boolean checkForSpace(int sizeOfChunk){
        if (spaceAvailable - sizeOfChunk >= 0){
            return true;
        }else {
            return false;
        }
    }

    public int getTotalSpace() {
        return totalSpace;
    }

    public void setTotalSpace(int totalSpace) {
        this.totalSpace = totalSpace;
    }


    public ArrayList<Integer> getMyChunks(String fileID) {
        return myChunks.get(fileID);
    }

    public ArrayList<Chunk> getReceivedChunks() {
        return receivedChunks;
    }

    public void resetReceivedChunks(){
        receivedChunks=new ArrayList<>();
    }

    public ArrayList<File> getFiles() {
        return files;
    }

    public int getReplicationsInSystem(String tag) {
        if(replicationsInSystem.containsKey(tag)){
            return replicationsInSystem.get(tag);
        }
        return 0;
    }

    public Map<String, Integer> getChunkRepDegreeMap() {
        return chunkRepDegreeMap;
    }

    public void addToSystemReplications(String tag) {
        if(!replicationsInSystem.containsKey(tag)){
            replicationsInSystem.put(tag,1);
        }else{
            int current = replicationsInSystem.get(tag);
            replicationsInSystem.replace(tag,current+1);
        }
    }

    public void subtracToSystemReplications(String tag) {
        if(replicationsInSystem.containsKey(tag)){
            int current = replicationsInSystem.get(tag);
            replicationsInSystem.replace(tag,current-1);
        }
    }

    public void receiveChunk(Chunk c){
        boolean found=false;
        for(Chunk chunk : receivedChunks){
            if(chunk.getFileID().equals(c.getFileID()) && chunk.getNumber()==c.getNumber()) {
                found=true;
                break;
            }
        }
        if(!found) receivedChunks.add(c);
    }

    public void deleteChunksFromFile(String fileId) {

        //check if the peer has any chunks of this file
        if(myChunks.containsKey(fileId)){
            ArrayList<Integer>  chunksOfFile = myChunks.get(fileId);

            for (int i = 0; i < chunksOfFile.size() ; i++) {
                String tag = fileId + "_" +chunksOfFile.get(i);
                if(replicationsInSystem.containsKey(tag)){
                    replicationsInSystem.remove(tag);
                }
                if(chunkRepDegreeMap.containsKey(tag)){
                    chunkRepDegreeMap.remove(tag);
                }

            }

            myChunks.remove(fileId);
        }

    }

    public void reclaimFileSpace(String fileId){

        //checking for the directory
        File dir = new File("fileSystem/" + Peer.getID() +"/backup/" + fileId);

        if(dir.exists()){
            //delete the file chunks in this storage, this code has to be repeated due to needing to use more steps here
            //check if the peer has any chunks of this file
            if(myChunks.containsKey(fileId)){
                ArrayList<Integer>  chunksOfFile = myChunks.get(fileId);
                for (int i = 0; i < chunksOfFile.size() ; i++) {
                    String tag = fileId + "_" +chunksOfFile.get(i);
                    if(replicationsInSystem.containsKey(tag)){
                        subtracToSystemReplications(tag);
                    }
                    if(chunkRepDegreeMap.containsKey(tag)){
                        chunkRepDegreeMap.remove(tag);
                    }

                    Peer.removedMessage(fileId,chunksOfFile.get(i));
                }

                myChunks.remove(fileId);
            }

            //get all the content of the file directory
            for (File f: dir.listFiles()) {
                f.delete();
            }

            dir.delete();
            System.out.println("Directory was deleted: "+ fileId);


        }else{
            System.out.println("Directory is not present");
        }
    }

    //TODO implement methods to delete a file and all of its chunks and implement purging of the storage folder(yet to be set)

}
