package com.notevault.db;

import java.sql.*;
import java.util.logging.Logger;

/**
 * @brief Синглтон-менеджер соединения с базой данных SQLite.
 *
 * @details
 * Управляет жизненным циклом единственного {@link Connection} к файлу
 * {@code _db/notes.db} внутри выбранного vault.
 *
 * ## Схема БД
 * | Таблица        | Назначение                                        |
 * |----------------|---------------------------------------------------|
 * | `notes`        | Основные метаданные заметок                       |
 * | `tags`         | Глобальный список тегов                           |
 * | `note_tags`    | Связь M:N между заметками и тегами                |
 * | `books`        | Именованные коллекции заметок                     |
 * | `book_notes`   | Связь M:N с порядком для книг                     |
 * | `attachments`  | Ссылки на прикреплённые файлы (без бинарных данных)|
 *
 * ## Настройки SQLite
 * При открытии устанавливаются PRAGMA:
 * - **WAL** — Write-Ahead Logging, параллельные чтения без блокировок.
 * - **foreign_keys=ON** — каскадные удаления по FK.
 * - **synchronous=NORMAL** — баланс надёжности и скорости при WAL.
 * - **busy_timeout=5000** — ожидание до 5 с при заблокированном файле.
 *
 * ## Важно о порядке вызова PRAGMA
 * Каждый PRAGMA исполняется в **отдельном** {@link Statement} через
 * try-with-resources. Это необходимо из-за поведения sqlite-jdbc ≥ 3.45:
 * драйвер удерживает неявную транзакцию после первого {@code execute()},
 * и цепочка вызовов на одном Statement приводит к {@code SQLITE_BUSY}.
 *
 * \warning Не является потокобезопасным. Все операции должны выполняться
 *          из Event Dispatch Thread или с внешней синхронизацией.
 *
 * @see com.notevault.service.VaultService#openVault(java.nio.file.Path)
 */
public class DatabaseManager {
    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

    /** @brief Единственный экземпляр (паттерн Singleton). */
    private static DatabaseManager instance;

    /** @brief Активное соединение с БД. {@code null} до вызова {@link #openVault(String)}. */
    private Connection connection;

    /** @brief Абсолютный путь к файлу БД (для диагностических сообщений). */
    private String dbPath;

    /** @brief Приватный конструктор — использовать {@link #getInstance()}. */
    private DatabaseManager() {}

    /**
     * @brief Возвращает единственный экземпляр менеджера.
     * @return Экземпляр {@link DatabaseManager}.
     */
    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /**
     * @brief Вызывается при старте приложения до выбора vault.
     *
     * Метод-заглушка; реальная инициализация происходит в {@link #openVault(String)}.
     */
    public void initialize() {}

    /**
     * @brief Открывает (или создаёт) базу данных в указанном vault.
     *
     * @details
     * Алгоритм:
     * -# Закрывает предыдущее соединение, если оно открыто.
     * -# Создаёт директорию {@code <vaultPath>/_db/} если не существует.
     * -# Открывает соединение JDBC с файлом {@code notes.db}.
     * -# Применяет PRAGMA (по одному на Statement).
     * -# Создаёт схему БД если таблицы ещё не существуют.
     *
     * @param vaultPath Абсолютный путь к корневой директории vault.
     * @throws RuntimeException если соединение или создание схемы провалилось.
     */
    public void openVault(String vaultPath) {
        try {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) connection.close();
                } catch (SQLException ignored) {}
                connection = null;
            }

            java.io.File dbDir = new java.io.File(vaultPath, "_db");
            dbDir.mkdirs();
            this.dbPath = dbDir.getAbsolutePath() + java.io.File.separator + "notes.db";

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            executePragma("PRAGMA journal_mode=WAL");
            executePragma("PRAGMA foreign_keys=ON");
            executePragma("PRAGMA synchronous=NORMAL");
            executePragma("PRAGMA busy_timeout=5000");

            createSchema();
            LOG.info("Database opened: " + dbPath);

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to open database at " + dbPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * @brief Исполняет одиночный PRAGMA в изолированном Statement.
     *
     * @details
     * Try-with-resources гарантирует финализацию Statement (и связанной
     * с ним неявной транзакции sqlite-jdbc) до перехода к следующей операции.
     *
     * @param pragma SQL-строка вида {@code "PRAGMA journal_mode=WAL"}.
     * @throws SQLException при ошибке выполнения.
     */
    private void executePragma(String pragma) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(pragma);
        }
    }

    /**
     * @brief Создаёт все таблицы и индексы если они не существуют.
     *
     * @details
     * DDL выполняется в явной транзакции для атомарности: либо вся схема
     * создаётся, либо (при ошибке) откатывается полностью.
     * Идемпотентен благодаря {@code IF NOT EXISTS}.
     *
     * @throws SQLException при ошибке DDL (с автоматическим rollback).
     */
    private void createSchema() throws SQLException {
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

    /**
     * @brief Возвращает активное соединение с БД.
     *
     * @return Активный {@link Connection}.
     * @throws IllegalStateException если vault ещё не открыт.
     */
    public Connection getConnection() {
        if (connection == null)
            throw new IllegalStateException("Database not opened. Select a vault first.");
        return connection;
    }

    /**
     * @brief Проверяет, открыто ли соединение с БД.
     * @return {@code true} если соединение существует и не закрыто.
     */
    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * @brief Закрывает соединение с БД.
     *
     * Вызывается при завершении приложения из {@link com.notevault.ui.MainWindow}.
     */
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