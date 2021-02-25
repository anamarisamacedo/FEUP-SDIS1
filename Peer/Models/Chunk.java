package Peer.Models;

import java.io.Serializable;

public class Chunk implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileId;
    private int chunkNo;
    private int rDegree = 0;
    private String id;
    private byte[] body;
    private long size = 0;

    public Chunk(String fileId, int chunkNo, int rDegree) {
        this.id = new String("(" + fileId + ", " + chunkNo + ")");
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.rDegree = rDegree;
    }

    public Chunk(String fileId, int chunkNo, long size) {
        this.id = new String("(" + fileId + ", " + chunkNo + ")");
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.size = size;
    }

    public Chunk(Chunk c) {
        this.id = c.getID();
        this.fileId = c.getFileId();
        this.chunkNo = c.getChunkNo();
        this.rDegree = c.getReplDegree();
    }

    public String getID() {
        return this.id;
    }

    public long getSize() {
        return this.size;
    }

    public String getFileId() {
        return this.fileId;
    }

    public int getChunkNo() {
        return this.chunkNo;
    }

    public int getReplDegree() {
        return this.rDegree;
    }

    public int getRepById(String Id) {
        if (this.id == Id)
            return this.rDegree;
        else
            return 0;
    }

    public void decrementReplDegree() {
        this.rDegree--;
    }

    public void addBody(byte[] body) {
        this.body = body;
    }
}
