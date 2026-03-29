package com.notevault.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Note {
    private long id;
    private String title;
    private String filePath;       // relative to vault root
    private String content;        // full file content including front-matter
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Tag> tags = new ArrayList<>();
    private Long bookId;
    private Integer bookOrder;
    private java.util.Map<String, String> metaExtra = new java.util.HashMap<>();

    public Note() {}

    public Note(String title, String filePath) {
        this.title = title;
        this.filePath = filePath;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public Integer getBookOrder() { return bookOrder; }
    public void setBookOrder(Integer bookOrder) { this.bookOrder = bookOrder; }

    public java.util.Map<String, String> getMetaExtra() { return metaExtra; }
    public void setMetaExtra(java.util.Map<String, String> metaExtra) { this.metaExtra = metaExtra; }

    /** Returns body text without YAML front-matter */
    public String getBodyContent() {
        if (content == null) return "";
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end != -1) {
                return content.substring(end + 4).stripLeading();
            }
        }
        return content;
    }

    @Override
    public String toString() {
        return title != null ? title : "(Untitled)";
    }
}