package com.notevault.db;

import com.notevault.model.Attachment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public Attachment insert(Attachment att) throws SQLException {
        String sql = "INSERT INTO attachments (note_id, file_name, file_path) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, att.getNoteId());
            ps.setString(2, att.getFileName());
            ps.setString(3, att.getFilePath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) att.setId(rs.getLong(1));
            }
        }
        return att;
    }

    public List<Attachment> findByNote(long noteId) throws SQLException {
        List<Attachment> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM attachments WHERE note_id=? ORDER BY file_name")) {
            ps.setLong(1, noteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Attachment a = new Attachment(rs.getLong("note_id"),
                            rs.getString("file_name"), rs.getString("file_path"));
                    a.setId(rs.getLong("id"));
                    list.add(a);
                }
            }
        }
        return list;
    }

    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM attachments WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteByNote(long noteId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM attachments WHERE note_id=?")) {
            ps.setLong(1, noteId);
            ps.executeUpdate();
        }
    }
}