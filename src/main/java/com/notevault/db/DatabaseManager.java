package com.notevault.db;

import java.sql.*;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private static DatabaseManager instance;
    private Connection connection;
    private String dbPath;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public void initialize() {
        // Called at startup before vault is chosen — nothing to do yet.
    }

    public void openVault(String vaultPath) {
        try {
            // Close any previously open connection cleanly
            if (connection != null) {
                try {
                    if (!connection.isClosed()) connection.close();
                } catch (SQLException ignored) {}
                connection = null;
            }

            java.io.File dbDir = new java.io.File(vaultPath, "_db");
            dbDir.mkdirs();
            this.dbPath = dbDir.getAbsolutePath() + java.io.File.separator + "notes.db";

            // Open connection; autocommit stays ON for now
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Apply PRAGMAs one at a time, each in its own statement.
            // Do NOT chain them in autocommit mode — sqlite-jdbc 3.45 keeps an
            // implicit transaction open after the first execute() and the second
            // execute() tries to commit it, hitting SQLITE_BUSY.
            executePragma("PRAGMA journal_mode=WAL");
            executePragma("PRAGMA foreign_keys=ON");
            executePragma("PRAGMA synchronous=NORMAL");  // safe with WAL
            executePragma("PRAGMA busy_timeout=5000");   // wait up to 5 s on lock

            createSchema();
            LOG.info("Database opened: " + dbPath);

        } catch (SQLException e) {
            // Wrap with the path so the caller can show a useful message
            throw new RuntimeException("Failed to open database at " + dbPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Execute a single PRAGMA statement and immediately close the Statement.
     * Using try-with-resources guarantees the statement (and any implicit
     * SQLite transaction it holds) is finalised before we move to the next one.
     */
    private void executePragma(String pragma) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(pragma);
        }
    }

    private void createSchema() throws SQLException {
        // Run all DDL inside an explicit transaction — faster and atomic.
        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    title      TEXT    NOT NULL,
                    file_path  TEXT    NOT NULL UNIQUE,
                    created_at TEXT    NOT NULL,
                    updated_at TEXT    NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tags (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT    NOT NULL UNIQUE COLLATE NOCASE
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS note_tags (
                    note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                    tag_id  INTEGER NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
                    PRIMARY KEY (note_id, tag_id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT NOT NULL UNIQUE,
                    description TEXT,
                    created_at  TEXT NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS book_notes (
                    book_id     INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                    note_id     INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                    order_index INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (book_id, note_id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attachments (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    note_id   INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                    file_name TEXT    NOT NULL,
                    file_path TEXT    NOT NULL
                )""");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notes_title    ON notes(title)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_note_tags_tag  ON note_tags(tag_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_book_notes_ord ON book_notes(book_id, order_index)");

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public Connection getConnection() {
        if (connection == null)
            throw new IllegalStateException("Database not opened. Select a vault first.");
        return connection;
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOG.info("Database closed.");
            }
        } catch (SQLException e) {
            LOG.warning("Error closing database: " + e.getMessage());
        } finally {
            connection = null;
        }
    }
}