package com.notevault.db;

import com.notevault.model.Tag;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TagDAO {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Tag> findAll() throws SQLException {
        List<Tag> tags = new ArrayList<>();
        String sql = """
            SELECT t.*, COUNT(nt.note_id) as note_count
            FROM tags t
            LEFT JOIN note_tags nt ON t.id = nt.tag_id
            GROUP BY t.id
            ORDER BY t.name
            """;
        try (ResultSet rs = conn().createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Tag t = new Tag(rs.getString("name"));
                t.setId(rs.getLong("id"));
                t.setNoteCount(rs.getInt("note_count"));
                tags.add(t);
            }
        }
        return tags;
    }

    public Tag findByName(String name) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM tags WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Tag t = new Tag(rs.getString("name"));
                    t.setId(rs.getLong("id"));
                    return t;
                }
            }
        }
        return null;
    }

    public void rename(long tagId, String newName) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE tags SET name=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }

    public void delete(long tagId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM tags WHERE id=?")) {
            ps.setLong(1, tagId);
            ps.executeUpdate();
        }
    }

    public void pruneUnused() throws SQLException {
        conn().createStatement().executeUpdate(
                "DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM note_tags)");
    }
}