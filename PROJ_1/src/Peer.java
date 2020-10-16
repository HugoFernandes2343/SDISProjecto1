import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class Peer implements RMIInterface {

    public static final int MAX_SYSTEM_MEMORY = 1000000;

    private static String protocolVersion;
    private static int id;
    private static String serverAccessPoint;
    private static Map<Channel.ChannelType, Channel> channels;
    private static Storage storage;


    public Peer(String protocolVersion, int id, String serverAccessPoint, String[] mcAddress, String[] mdbAddress, String[] mdrAddress) throws IOException {
        this.protocolVersion = protocolVersion;
        this.id = id;
        this.serverAccessPoint = serverAccessPoint;

        if(!readStorageSave()) storage = new Storage();

        setupChannels(mcAddress, mdbAddress, mdrAddress);

        //setup directory
        new File("./fileSystem/" + id +"/backup").mkdirs();
        new File("./fileSystem/" + id +"/restore").mkdirs();

        System.out.println("Peer " + id + " connected");
    }



    public static void main(String args[]) throws IOException {
        if (args.length != 6) {
            System.out.println("Usage: java -classpath bin Peer" +
                    " <protocol_version> <server_id> <service_access_point>" +
                    " <mc:port> <mdb:port> <mdr:port>");
            return;
        }

        String protocolVersion = args[0];
        int serverID = Integer.parseInt(args[1]);

        String serviceAccessPoint = args[2];
        if (serviceAccessPoint == null) {
            return;
        }

        String[] mcAddress = args[3].split(":");
        String[] mdbAddress = args[4].split(":");
        String[] mdrAddress = args[5].split(":");

        // Flag needed for systems that use IPv6 by default
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            Peer obj = new Peer(protocolVersion, serverID, serviceAccessPoint, mcAddress, mdbAddress, mdrAddress);
            RMIInterface stub = (RMIInterface) UnicastRemoteObject.exportObject(obj, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(serviceAccessPoint, stub);
            System.out.println("Server ready!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void setupChannels(String[] mcAddress, String[] mdbAddress, String[] mdrAddress) {
        Channel mc = new MCChannel(this, mcAddress[0], mcAddress[1]);
        Channel mdb = new MDBChannel(this, mdbAddress[0], mdbAddress[1]);
        Channel mdr = new MDRChannel(this, mdrAddress[0], mdrAddress[1]);

        new Thread(mc).start();
        new Thread(mdb).start();
        new Thread(mdr).start();

        channels = new HashMap<>();
        channels.put(Channel.ChannelType.MC, mc);
        channels.put(Channel.ChannelType.MDB, mdb);
        channels.put(Channel.ChannelType.MDR, mdr);
    }


    public static Channel getChannel(Channel.ChannelType channelType) {
        return channels.get(channelType);
    }

    public static int getID() {
        return id;
    }

    public static Storage getStorage() {
        return storage;
    }

    public static String getProtocolVersion(){
        return protocolVersion;
    }

    @Override
    public void backupProtocol(String pathname, int replicationDegree) {
        File f = new File("files/"+ pathname);
        String fileId = Util.sha256(f.getName() + f.lastModified());
        //file reading
        FileInputStream st = null;
        try {
            st = new FileInputStream(f);

            BufferedInputStream bst = new BufferedInputStream(st);

            byte[] buff = new byte[1000 * 64];
            int i;
            int chunkNo = 1;
            while(( i = bst.read(buff)) > 0){
                //getting variables to instantiate the chunk
                byte[] data = Arrays.copyOf(buff,i);
                Chunk chk = new Chunk(chunkNo,fileId,data,replicationDegree);

                storage.addToMap(chk);
                storage.saveChunk(chk);

                //Create PUTCHUNK Message
                String header = protocolVersion + " " + "PUTCHUNK " + id + " " + fileId + " " + chunkNo + " " + replicationDegree + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

                byte[] allByteArray = new byte[header.getBytes().length + data.length];

                ByteBuffer byteBuffer = ByteBuffer.wrap(allByteArray);
                byteBuffer.put(header.getBytes());
                byteBuffer.put(data);

                byte[] msg = byteBuffer.array();


                //send PUTCHUNK
                ResponseManagement thread = new ResponseManagement(replicationDegree,fileId + "_" + chunkNo,msg);
                new Thread(thread).start();
                chunkNo++;
            }

            //handle a specific case
            if (f.length() % (64*1000) == 0) {
                //ADD EMPTY CHUNK
                //getting variables to instantiate the chunk

                Chunk chk = new Chunk(chunkNo, fileId, null, replicationDegree);
                storage.addToMap(chk);
                storage.saveChunk(chk);

                //Create PUTCHUNK Message
                String header = protocolVersion + " " + "PUTCHUNK " + id + " " + fileId + " " + chunkNo + " " + replicationDegree + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

                byte[] allByteArray = new byte[header.getBytes().length];

                ByteBuffer byteBuffer = ByteBuffer.wrap(allByteArray);
                byteBuffer.put(header.getBytes());

                byte[] msg = byteBuffer.array();

                //send PUTCHUNK
                ResponseManagement thread = new ResponseManagement(replicationDegree, fileId + "_" + chunkNo, msg);
                new Thread(thread).start();
            }

            storage.files.add(f);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void restoreProtocol(String fileName){
        File f = new File("files/"+ fileName);
        System.out.println();
        byte[] buff = new byte[1000 * 64];
        String fileId = Util.sha256(f.getName() + f.lastModified());

        ArrayList<Integer> fileChunks = storage.getMyChunks(fileId);

        for (Integer c : fileChunks){
            byte[] data = Arrays.copyOf(buff,c);
            //Create Message
            String header = protocolVersion + " " + "GETCHUNK " + id + " " + fileId + " " + c + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

            byte[] allByteArray = new byte[header.getBytes().length + data.length];

            ByteBuffer byteBuffer = ByteBuffer.wrap(allByteArray);
            byteBuffer.put(header.getBytes());
            byteBuffer.put(data);
            byte[] msg = byteBuffer.array();
            getChannel(Channel.ChannelType.MC).sendMessage(msg);
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Util.CreateFile(fileName);
    }

    @Override
    public void deleteProtocol(String pathName) {
        File f = new File("files/" + pathName);
        String fileId = Util.sha256(f.getName() + f.lastModified());

        storage.files.removeIf(file -> file.getName().equals(f.getName()));
        storage.deleteChunksFromFile(fileId);

        String header = protocolVersion + " DELETE " + id + " " + fileId + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;

        byte[] msg = header.getBytes();

        getChannel(Channel.ChannelType.MC).sendMessage(msg);
    }

    public static void removedMessage(String fileId, int chunkNo){

        String header = protocolVersion + " REMOVED " + id + " " + fileId + " " + chunkNo + " " + (char) 0xD + (char) 0xA + (char) 0xD + (char) 0xA;
        byte[] msg = header.getBytes();

        getChannel(Channel.ChannelType.MC).sendMessage(msg);
    }

    @Override
    public void reclaimProtocol(int newStorageSpace) {
        int currentStorageSpace = storage.getTotalSpace();

        //checks if the current space is smaller then the new storage space
        if(newStorageSpace >= currentStorageSpace){
            storage.setTotalSpace(newStorageSpace);
            storage.updateSpace();

        }else{
            int spaceNotAvailable = currentStorageSpace - storage.getSpaceAvailable();
            if(newStorageSpace > spaceNotAvailable){
                storage.setTotalSpace(newStorageSpace);
            }else{
                int spaceNeeded = spaceNotAvailable - newStorageSpace;
                System.out.println("spaceNeeded: " + spaceNeeded);
                //in case of needing to remove files the storage will remove the oldest files first until there is enough space
                int spaceCleared =0;

                Set<String> map = storage.myChunks.keySet();

                for(String fileId : map) {
                    if(spaceCleared> spaceNeeded){
                        break;
                    }else{
                        int spaceBefore = storage.getSpaceAvailable();
                        System.out.println("spaceBefore: " + spaceBefore);

                        storage.reclaimFileSpace(fileId);
                        storage.updateSpace();

                        int spaceAfter = storage.getSpaceAvailable();
                        System.out.println("spaceAfter: " + spaceAfter);
                        spaceCleared = spaceAfter - spaceBefore;
                    }

                }

                storage.setTotalSpace(newStorageSpace);
                storage.updateSpace();

            }

        }

        System.out.println("New storage size is:" + storage.getTotalSpace());

    }

    @Override
    public void stateProtocol() {
        System.out.println();
        String state = "Backed up Files: \n";

        //For each file whose backup it has initiated:
        for(int i = 0; i < storage.getFiles().size(); i++) {
            state += "File pathname : " + storage.getFiles().get(i).getAbsolutePath() + "\n";
            String fileId = Util.sha256(storage.getFiles().get(i).getName() + storage.getFiles().get(i).lastModified());
            state += "File ID : " + fileId + "\n";
            state += "Desired Replication degree : " + storage.getChunkRepDegreeMap().get(fileId+"_"+"1") + "\n";

            File fileChunks = new File("./files/"+storage.getFiles().get(i).getName());
            for(int j = 0; j < storage.getMyChunks(fileId).size(); j++) {
                state = state + "   Chunk ID : " + storage.getMyChunks(fileId).get(j) + "\n";
                state = state + "   Chunk perceived replication degree : " + storage.getChunkRepDegreeMap().get(fileId+"_"+(j+1)) + "\n";
            }
        }
        //For each chunk it stores:
        state+="Stored Chunks: \n";
        File peerFolder = new File("./fileSystem/" + id + "/backup");

        for(int i = 0; i < peerFolder.listFiles().length; i++) {
            state+="(File " +  peerFolder.listFiles()[i].getName()+")\n";
            for(int j = 0; j < peerFolder.listFiles()[i].listFiles().length; j++) {
                state = state + "   Chunk ID: " + peerFolder.listFiles()[i].listFiles()[j].getName() + "\n";
                state = state + "   Chunk size: " + peerFolder.listFiles()[i].listFiles()[j].length()/1024 + "KB\n";
                state = state + "   Perceived Rep Degree: " + storage.getReplicationsInSystem(peerFolder.listFiles()[i].getName()+"_"+(j+1)) + "\n";
                state+=peerFolder.listFiles()[i].getName()+"_"+(j+1)+"\n";
            }
        }
        state += "Space Reserved: " + storage.getSpaceAvailable() + "KB\n"
                + "Space occupied by chunks: " + storage.getSpaceOccupied() + "KB\n";

        System.out.println(state);
    }

    @Override
    public void saveProtocol(){
        saveStorage();
    }

    /**
     * read from serializable file
     */
    private boolean readStorageSave() {

        String filepath = "fileSystem/" + id + "/storageSavefile.ser";
        File saveFile = new File("fileSystem/" + id + "/storageSavefile.ser");

        //if the savefile doesnt exist just let the storage stay as empty
        if(!saveFile.exists()){
            return false;
        }

        try {
            FileInputStream fileInputStream = new FileInputStream(filepath);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            storage = (Storage) objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
            System.out.println("The Object  was successfully read from a file");
            return true;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;

    }

    /**
     * Save serializable object
     */
    private static void saveStorage() {
        try {
            String filepath = "./fileSystem/" + id + "/storageSavefile.ser";

            FileOutputStream fileOut = new FileOutputStream(filepath);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(storage);
            objectOut.close();
            fileOut.close();
            System.out.println("The Object  was succesfully written to a file");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}