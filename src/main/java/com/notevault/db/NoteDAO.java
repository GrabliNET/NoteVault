package com.notevault.db;

import com.notevault.model.Note;
import com.notevault.model.Tag;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoteDAO {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public Note insert(Note note) throws SQLException {
        String sql = "INSERT INTO notes (title, file_path, created_at, updated_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, note.getTitle());
            ps.setString(2, note.getFilePath());
            ps.setString(3, note.getCreatedAt().toString());
            ps.setString(4, note.getUpdatedAt().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) note.setId(rs.getLong(1));
            }
        }
        syncTags(note);
        syncBook(note);
        return note;
    }

    public void update(Note note) throws SQLException {
        String sql = "UPDATE notes SET title=?, file_path=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, note.getTitle());
            ps.setString(2, note.getFilePath());
            ps.setString(3, LocalDateTime.now().toString());
            ps.setLong(4, note.getId());
            ps.executeUpdate();
        }
        syncTags(note);
        syncBook(note);
    }

    public void delete(long noteId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM notes WHERE id=?")) {
            ps.setLong(1, noteId);
            ps.executeUpdate();
        }
    }

    public Note findById(long id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM notes WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNote(rs);
            }
        }
        return null;
    }

    public Note findByPath(String filePath) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM notes WHERE file_path=?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNote(rs);
            }
        }
        return null;
    }

    public List<Note> findAll() throws SQLException {
        List<Note> notes = new ArrayList<>();
        try (ResultSet rs = conn().createStatement().executeQuery(
                "SELECT * FROM notes ORDER BY updated_at DESC")) {
            while (rs.next()) notes.add(mapNote(rs));
        }
        for (Note n : notes) n.setTags(findTagsForNote(n.getId()));
        return notes;
    }

    public List<Note> findByTag(long tagId) throws SQLException {
        List<Note> notes = new ArrayList<>();
        String sql = """
            SELECT n.* FROM notes n
            JOIN note_tags nt ON n.id = nt.note_id
            WHERE nt.tag_id = ?
            ORDER BY n.updated_at DESC
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) notes.add(mapNote(rs));
            }
        }
        for (Note n : notes) n.setTags(findTagsForNote(n.getId()));
        return notes;
    }

    public List<Note> findByBook(long bookId) throws SQLException {
        List<Note> notes = new ArrayList<>();
        String sql = """
            SELECT n.*, bn.order_index FROM notes n
            JOIN book_notes bn ON n.id = bn.note_id
            WHERE bn.book_id = ?
            ORDER BY bn.order_index ASC
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Note n = mapNote(rs);
                    n.setBookId(bookId);
                    n.setBookOrder(rs.getInt("order_index"));
                    notes.add(n);
                }
            }
        }
        for (Note n : notes) n.setTags(findTagsForNote(n.getId()));
        return notes;
    }

    public List<Note> search(String query) throws SQLException {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM notes WHERE title LIKE ? ORDER BY updated_at DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) notes.add(mapNote(rs));
            }
        }
        for (Note n : notes) n.setTags(findTagsForNote(n.getId()));
        return notes;
    }

    public List<Tag> findTagsForNote(long noteId) throws SQLException {
        List<Tag> tags = new ArrayList<>();
        String sql = """
            SELECT t.* FROM tags t
            JOIN note_tags nt ON t.id = nt.tag_id
            WHERE nt.note_id = ?
            ORDER BY t.name
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, noteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tag t = new Tag(rs.getString("name"));
                    t.setId(rs.getLong("id"));
                    tags.add(t);
                }
            }
        }
        return tags;
    }

    // ---- Tag sync ----

    private void syncTags(Note note) throws SQLException {
        // Remove all existing tag links
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM note_tags WHERE note_id=?")) {
            ps.setLong(1, note.getId());
            ps.executeUpdate();
        }
        if (note.getTags() == null) return;
        for (Tag tag : note.getTags()) {
            long tagId = getOrCreateTag(tag.getName());
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT OR IGNORE INTO note_tags (note_id, tag_id) VALUES (?, ?)")) {
                ps.setLong(1, note.getId());
                ps.setLong(2, tagId);
                ps.executeUpdate();
            }
        }
    }

    public long getOrCreateTag(String name) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT id FROM tags WHERE name=?")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO tags (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to create tag: " + name);
    }

    // ---- Book sync ----

    private void syncBook(Note note) throws SQLException {
        // Remove from all books first
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM book_notes WHERE note_id=?")) {
            ps.setLong(1, note.getId());
            ps.executeUpdate();
        }
        if (note.getBookId() != null) {
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT OR REPLACE INTO book_notes (book_id, note_id, order_index) VALUES (?, ?, ?)")) {
                ps.setLong(1, note.getBookId());
                ps.setLong(2, note.getId());
                ps.setInt(3, note.getBookOrder() != null ? note.getBookOrder() : 0);
                ps.executeUpdate();
            }
        }
    }

    private Note mapNote(ResultSet rs) throws SQLException {
        Note note = new Note();
        note.setId(rs.getLong("id"));
        note.setTitle(rs.getString("title"));
        note.setFilePath(rs.getString("file_path"));
        String createdAt = rs.getString("created_at");
        String updatedAt = rs.getString("updated_at");
        note.setCreatedAt(createdAt != null ? LocalDateTime.parse(createdAt) : LocalDateTime.now());
        note.setUpdatedAt(updatedAt != null ? LocalDateTime.parse(updatedAt) : LocalDateTime.now());
        return note;
    }
}