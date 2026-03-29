package com.notevault.db;

import com.notevault.model.Book;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookDAO {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public Book insert(Book book) throws SQLException {
        String sql = "INSERT INTO books (name, description, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, book.getName());
            ps.setString(2, book.getDescription());
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) book.setId(rs.getLong(1));
            }
        }
        return book;
    }

    public void update(Book book) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE books SET name=?, description=? WHERE id=?")) {
            ps.setString(1, book.getName());
            ps.setString(2, book.getDescription());
            ps.setLong(3, book.getId());
            ps.executeUpdate();
        }
    }

    public void delete(long bookId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM books WHERE id=?")) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        }
    }

    public List<Book> findAll() throws SQLException {
        List<Book> books = new ArrayList<>();
        try (ResultSet rs = conn().createStatement().executeQuery(
                "SELECT * FROM books ORDER BY name")) {
            while (rs.next()) {
                Book b = new Book(rs.getString("name"));
                b.setId(rs.getLong("id"));
                b.setDescription(rs.getString("description"));
                String ca = rs.getString("created_at");
                b.setCreatedAt(ca != null ? LocalDateTime.parse(ca) : LocalDateTime.now());
                books.add(b);
            }
        }
        return books;
    }

    public Book findById(long id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM books WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Book b = new Book(rs.getString("name"));
                    b.setId(rs.getLong("id"));
                    b.setDescription(rs.getString("description"));
                    return b;
                }
            }
        }
        return null;
    }

    public void updateNoteOrder(long bookId, long noteId, int order) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE book_notes SET order_index=? WHERE book_id=? AND note_id=?")) {
            ps.setInt(1, order);
            ps.setLong(2, bookId);
            ps.setLong(3, noteId);
            ps.executeUpdate();
        }
    }

    public int getNoteCount(long bookId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM book_notes WHERE book_id=?")) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}