package Peer.Models;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Storage implements Serializable {

    private static final long serialVersionUID = 1L;

    private ConcurrentHashMap<String, List<String>> filesBackedUp;
    private List<Chunk> chunksBackedUp;
    private List<Chunk> storedChunks;
    private long maxDiskSpace;
    private List<Chunk> receivedChunkRestore;

    public Storage() {
        filesBackedUp = new ConcurrentHashMap<>();
        chunksBackedUp = Collections.synchronizedList(new ArrayList<>());
        storedChunks = Collections.synchronizedList(new ArrayList<>());
        maxDiskSpace = Long.MAX_VALUE;
        receivedChunkRestore = Collections.synchronizedList(new ArrayList<>());
    }

    public Storage(Storage s) {
        this.filesBackedUp = new ConcurrentHashMap<>(s.getBackedUpFiles());
        this.chunksBackedUp = Collections.synchronizedList(new ArrayList<>(s.getChunksBackedUp()));
        this.storedChunks = Collections.synchronizedList(new ArrayList<>(s.getStoredChunks()));
        this.maxDiskSpace = s.getMaxDiskSpace();
        receivedChunkRestore = Collections.synchronizedList(new ArrayList<>());

    }

    public Storage(ConcurrentHashMap<String, List<String>> filesBackedUp, List<Chunk> chunksBackedUp,
            ConcurrentHashMap<Integer, List<Integer>> confirmationsStoredChunk, List<Chunk> storedChunks,
            long maxDiskSpace) {
        this.filesBackedUp = new ConcurrentHashMap<>(filesBackedUp);
        this.chunksBackedUp = Collections.synchronizedList(new ArrayList<>(chunksBackedUp));
        this.storedChunks = Collections.synchronizedList(new ArrayList<>(storedChunks));
        this.maxDiskSpace = maxDiskSpace;
        receivedChunkRestore = Collections.synchronizedList(new ArrayList<>());
    }

    public List<Chunk> getChunksBackedUp() {
        return this.chunksBackedUp;
    }

    public List<Chunk> getStoredChunks() {
        return this.storedChunks;
    }

    public List<Chunk> getReceivedChunks() {
        return this.receivedChunkRestore;
    }

    public long getMaxDiskSpace() {
        return this.maxDiskSpace;
    }

    /* Saves Storage data into a specified file */
    public synchronized static void save(String filepath, Storage s) {
        try {
            FileOutputStream fileOut = new FileOutputStream(filepath);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(s);
            objectOut.close();
        } catch (IOException e) {
            System.err.println("Server save error: " + e.toString());
        }
    }

    /* Retrieves Storage data from a specified file */
    public synchronized static Storage load(String filepath) {
        try {
            FileInputStream fileIn = new FileInputStream(filepath);
            ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            Storage s = (Storage) objectIn.readObject();
            objectIn.close();
            System.out.println("Storage Loaded");
            return s;
        } catch (IOException e) {
            System.err.println("Server load error: " + e.toString());
            return null;
        } catch (ClassNotFoundException e) {
            System.err.println("Server load error: " + e.toString());
            return null;
        }
    }

    /* Set maxDiskSpace */

    public synchronized void updateDiskSpace(long newSize) {
        if (newSize == 0) {
            newSize = Long.MAX_VALUE;
        }
        this.maxDiskSpace = newSize;
    }

    /* Removes a chunk from storage and deletes chunk file from backup directory */
    public void removeChunk(Chunk c, String directoryPath) {
        if (storedChunks.contains(c)) {
            storedChunks.remove(c);

            File f = new File(directoryPath + "/File" + c.getFileId() + "Chunk" + c.getChunkNo());
            f.delete();
        }
    }

    /* Check if used space in a directory exceeds the maxDiskSpace */
    public synchronized boolean checkUsedSpaceAboveMax(String directoryPath) {
        long usedSpace = getUsedSpace(directoryPath);

        return (usedSpace / 1024) > this.maxDiskSpace;
    }

    /*
     * Gets a list of all chunks which replication degree exceeds file desired
     * replication degree
     */
    public synchronized List<Chunk> chunksAboveReplicationDegree() {
        List<Chunk> list = new ArrayList<>();
        for (Chunk c : this.storedChunks) {
            if (checkIfChunkReplDegreeAboveFileReplDegree(c)) {
                list.add(c);
            }
        }
        return list;
    }

    /*
     * Check if a chunk's replication degree exceeds file desired replication degree
     */
    public synchronized boolean checkIfChunkReplDegreeAboveFileReplDegree(Chunk c) {
        int desiredReplicationDegree = getDesiredReplicationDegreOfFile(c.getFileId());

        if (c.getReplDegree() > desiredReplicationDegree) {
            return true;
        }
        return false;
    }

    // Retorna o espaço ocupado pelos ficheiros de backup no diretório fornecido
    public synchronized long getUsedSpace(String directoryPath) {
        long usedSpace = 0;

        File dir = new File(directoryPath);
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            for (File f : files) {
                usedSpace += f.length();
            }
        }

        return usedSpace;

    }

    // Retorna o espaço total do diretório fornecido
    public synchronized long getTotalSpace(String directoryPath) {

        File file = new File(directoryPath);
        File dir = new File(file.getAbsolutePath());
        long usedSpace = (dir.getTotalSpace());
        return usedSpace;
    }

    // Retorna o espaço livre que pode ser usado no diretório fornecido
    public long getUsableSpace(String directoryPath) {

        File file = new File(directoryPath);
        File dir = new File(file.getAbsolutePath());
        long usedSpace = (dir.getUsableSpace());
        return usedSpace;
    }

    /* Returns a chunk given a fileId and a chunkNo */
    public Chunk getChunk(String fileId, String chunkNo) {
        for (Chunk c : storedChunks) {
            if (c.getChunkNo() == Integer.parseInt(chunkNo) && c.getFileId().equals(fileId)) {
                return c;
            }
        }
        return null;
    }

    /* Returns a chunk from chunksBackedUp given a fileId and a chunkNo */
    public Chunk getBackedUpChunk(String fileId, String chunkNo) {
        for (Chunk c : chunksBackedUp) {
            if (c.getChunkNo() == Integer.parseInt(chunkNo) && c.getFileId().equals(fileId)) {
                return c;
            }
        }
        return null;
    }

    /* Returns last chunk of chunksBackedUp list */
    public Chunk getLastChunk() {
        return storedChunks.get(storedChunks.size() - 1);
    }

    /* Decrements the chunk replication degree by 1 */
    public void decrementChunkReplicationDegree(Chunk c) {
        if (storedChunks.contains(c)) {
            c.decrementReplDegree();
        }
    }

    public synchronized void addnewBackedUpFile(String fileId, String filePath, int rDegree) {
        List<String> values = new ArrayList<>();
        values.add(filePath);
        values.add(String.valueOf(rDegree));
        filesBackedUp.put(fileId, values);
    }

    public ConcurrentHashMap<String, List<String>> getBackedUpFiles() {
        return filesBackedUp;
    }

    public synchronized void addBackedUpChunk(String fileId, int chunkNo, int repDegree) {
        Chunk chunk = new Chunk(fileId, chunkNo, repDegree);
        if (!chunksBackedUp.contains(chunk))
            chunksBackedUp.add(chunk);
    }

    public synchronized List<Chunk> getBackedUpChunks(String fileId) {
        List<Chunk> chunkInfo = new ArrayList<>();
        for (Chunk chunk : chunksBackedUp) {
            if (chunk.getFileId().equals(fileId)) {
                chunkInfo.add(chunk);
            }
        }
        return chunkInfo;
    }

    public synchronized void addStoredChunks(String fileId, String chunkNo, long size) {
        Chunk chunk = new Chunk(fileId, Integer.parseInt(chunkNo), size);
        if (!storedChunks.contains(chunk))
            storedChunks.add(chunk);
    }

    public synchronized List<Chunk> getStoredChunksPeer() {
        List<Chunk> chunkInfo = new ArrayList<>();
        for (Chunk chunk : storedChunks) {
            chunkInfo.add(chunk);
        }
        return chunkInfo;

    }

    /* Returns the replication degree of a file in filesBackedUp */
    public int getDesiredReplicationDegreOfFile(String fileId) {
        return Integer.parseInt(filesBackedUp.get(fileId).get(1));
    }

    public String getPathOfFile(String fileId) {
        return filesBackedUp.get(fileId).get(0);
    }

    public boolean containsLocalFile(String fileId) {
        List<String> file = this.filesBackedUp.get(fileId);
        return file != null;
    }

    public synchronized boolean hasReceivedChunkMessgage(String fileId, String chunkNo) {
        Chunk chunk = new Chunk(fileId, Integer.parseInt(chunkNo), 0);
        for (Chunk chunk1 : receivedChunkRestore) {
            if (chunk1.getID().equals(chunk.getID())) {
                return true;
            }
        }
        return false;

    }

    public synchronized void receivedChunkMessage(String fileID, String chunkNo) {
        Chunk chunk = new Chunk(fileID, Integer.parseInt(chunkNo), 0);
        for (Chunk chunk1 : receivedChunkRestore) {
            if (chunk1.getID().equals(chunk.getID())) {
                return;
            }
        }
        receivedChunkRestore.add(chunk);

    }

    public void removeAllChunksFromStored(String fileId) {
        List<Chunk> tmp = new ArrayList<>();
        for (Chunk c : storedChunks) {
            if (c.getFileId().equals(fileId)) {
                tmp.add(c);
            }
        }
        for (Chunk c : tmp) {
            storedChunks.remove(c);
        }
    }
}