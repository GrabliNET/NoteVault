package com.notevault.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Book {
    private long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private List<Note> notes = new ArrayList<>();

    public Book() {}

    public Book(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Note> getNotes() { return notes; }
    public void setNotes(List<Note> notes) { this.notes = notes; }

    @Override
    public String toString() { return name; }
}