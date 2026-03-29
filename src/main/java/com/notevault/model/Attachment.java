package com.notevault.model;

public class Attachment {
    private long id;
    private long noteId;
    private String fileName;
    private String filePath; // relative to vault root

    public Attachment() {}

    public Attachment(long noteId, String fileName, String filePath) {
        this.noteId = noteId;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getNoteId() { return noteId; }
    public void setNoteId(long noteId) { this.noteId = noteId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public String toString() { return fileName; }
}