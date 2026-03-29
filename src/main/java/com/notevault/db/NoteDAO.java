package com.notevault.db;

import com.notevault.model.Note;
import com.notevault.model.Tag;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @brief DAO (Data Access Object) для работы с таблицей {@code notes}.
 *
 * @details
 * Инкапсулирует все SQL-операции над заметками. Соединение получает
 * через {@link DatabaseManager#getConnection()} при каждом обращении —
 * объект не хранит ссылку на Connection, что позволяет безопасно
 * переоткрывать vault.
 *
 * ## Синхронизация тегов и книг
 * Методы {@link #insert(Note)} и {@link #update(Note)} автоматически
 * вызывают {@code syncTags()} и {@code syncBook()}, которые приводят
 * промежуточные таблицы {@code note_tags} и {@code book_notes} в
 * соответствие с текущим состоянием объекта {@link Note}.
 *
 * \note DAO не читает содержимое MD-файлов и не пишет в них.
 *       Синхронизация файловой системы — ответственность
 *       {@link com.notevault.service.VaultService}.
 *
 * @see com.notevault.service.VaultService
 */
public class NoteDAO {

    /**
     * @brief Вспомогательный метод получения соединения.
     * @return Активное соединение с БД.
     */
    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    /**
     * @brief Сохраняет новую заметку в БД и присваивает ей сгенерированный ID.
     *
     * @details
     * После INSERT синхронизирует теги ({@code note_tags}) и книгу ({@code book_notes}).
     *
     * @param note Объект заметки с заполненными {@code title}, {@code filePath},
     *             {@code createdAt}, {@code updatedAt}.
     * @return Тот же объект {@code note} с заполненным {@link Note#getId()}.
     * @throws SQLException при ошибке вставки.
     */
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

    /**
     * @brief Обновляет существующую заметку в БД.
     *
     * @details
     * Обновляет {@code title}, {@code file_path}, {@code updated_at}.
     * Также синхронизирует теги и книгу. Поле {@code created_at} не изменяется.
     *
     * @param note Заметка с актуальными данными и корректным {@link Note#getId()}.
     * @throws SQLException при ошибке обновления.
     */
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

    /**
     * @brief Удаляет заметку из БД по ID.
     *
     * @details
     * Благодаря {@code ON DELETE CASCADE} в схеме, связанные записи в
     * {@code note_tags}, {@code book_notes} и {@code attachments} удаляются автоматически.
     *
     * \warning Физический MD-файл при этом **не удаляется**.
     *          Удаление файла — ответственность вызывающего кода
     *          ({@link com.notevault.service.VaultService#deleteNote}).
     *
     * @param noteId Первичный ключ удаляемой заметки.
     * @throws SQLException при ошибке удаления.
     */
    public void delete(long noteId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM notes WHERE id=?")) {
            ps.setLong(1, noteId);
            ps.executeUpdate();
        }
    }

    /**
     * @brief Находит заметку по первичному ключу.
     *
     * @param id Первичный ключ.
     * @return Объект {@link Note} или {@code null} если не найдена.
     * @throws SQLException при ошибке запроса.
     */
    public Note findById(long id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM notes WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNote(rs);
            }
        }
        return null;
    }

    /**
     * @brief Находит заметку по относительному пути к файлу.
     *
     * Используется при импорте и синхронизации файловой системы с БД.
     *
     * @param filePath Относительный путь (например {@code "java/my-note.md"}).
     * @return Объект {@link Note} или {@code null} если не найдена.
     * @throws SQLException при ошибке запроса.
     */
    public Note findByPath(String filePath) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM notes WHERE file_path=?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNote(rs);
            }
        }
        return null;
    }

    /**
     * @brief Возвращает все заметки, отсортированные по дате изменения (новые первыми).
     *
     * @details
     * Для каждой заметки дополнительно выполняется запрос тегов через
     * {@link #findTagsForNote(long)}. При большом количестве заметок
     * это N+1 запросов — допустимо для локального SQLite.
     *
     * @return Список всех заметок с заполненными тегами.
     * @throws SQLException при ошибке запроса.
     */
    public List<Note> findAll() throws SQLException {
        List<Note> notes = new ArrayList<>();
        try (ResultSet rs = conn().createStatement().executeQuery(
                "SELECT * FROM notes ORDER BY updated_at DESC")) {
            while (rs.next()) notes.add(mapNote(rs));
        }
        for (Note n : notes) n.setTags(findTagsForNote(n.getId()));
        return notes;
    }

    /**
     * @brief Возвращает все заметки с указанным тегом.
     *
     * @param tagId Первичный ключ тега.
     * @return Список заметок с тегом, отсортированных по дате изменения.
     * @throws SQLException при ошибке запроса.
     */
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

    /**
     * @brief Возвращает заметки книги в порядке {@code order_index}.
     *
     * @param bookId Первичный ключ книги.
     * @return Список заметок книги, упорядоченных по позиции.
     * @throws SQLException при ошибке запроса.
     */
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

    /**
     * @brief Полнотекстовый поиск заметок по заголовку (LIKE).
     *
     * @param query Поисковый запрос (без подстановочных символов — они добавляются внутри).
     * @return Список заметок, заголовок которых содержит {@code query}.
     * @throws SQLException при ошибке запроса.
     */
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

    /**
     * @brief Возвращает теги конкретной заметки.
     *
     * @param noteId Первичный ключ заметки.
     * @return Список тегов, отсортированных по имени.
     * @throws SQLException при ошибке запроса.
     */
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

    // ── Internal sync helpers ─────────────────────────────────────────────

    /**
     * @brief Синхронизирует теги заметки с таблицей {@code note_tags}.
     *
     * @details
     * Стратегия — «удалить всё и вставить заново» (delete-and-reinsert):
     * -# Удаляем все строки {@code note_tags} для данной заметки.
     * -# Для каждого тега из {@link Note#getTags()} вызываем
     *    {@link #getOrCreateTag(String)} и вставляем связь.
     *
     * \note Простая стратегия работает нормально при небольшом числе тегов
     *       (обычно < 10 на заметку). При необходимости можно оптимизировать
     *       до диффа множеств.
     *
     * @param note Заметка с актуальным списком тегов и корректным ID.
     * @throws SQLException при ошибке синхронизации.
     */
    private void syncTags(Note note) throws SQLException {
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

    /**
     * @brief Возвращает ID тега по имени, создавая его при необходимости.
     *
     * @param name Имя тега (будет обрезан {@code trim()}).
     * @return ID существующего или только что созданного тега.
     * @throws SQLException при ошибке чтения или вставки.
     */
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

    /**
     * @brief Синхронизирует принадлежность заметки к книге ({@code book_notes}).
     *
     * @details
     * Удаляет все текущие записи {@code book_notes} для заметки, затем
     * вставляет новую если {@link Note#getBookId()} не {@code null}.
     *
     * @param note Заметка с актуальными {@code bookId} и {@code bookOrder}.
     * @throws SQLException при ошибке синхронизации.
     */
    private void syncBook(Note note) throws SQLException {
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

    /**
     * @brief Маппит строку ResultSet в объект {@link Note}.
     *
     * @details
     * Читает колонки {@code id}, {@code title}, {@code file_path},
     * {@code created_at}, {@code updated_at}. Теги и книга не читаются здесь —
     * они загружаются отдельными запросами выше.
     *
     * @param rs ResultSet, позиционированный на нужной строке.
     * @return Заполненный объект {@link Note} (без тегов и содержимого файла).
     * @throws SQLException при ошибке чтения колонок.
     */
    private Note mapNote(ResultSet rs) throws SQLException {
        Note note = new Note();
        note.setId(rs.getLong("id"));
        note.setTitle(rs.getString("title"));
        note.setFilePath(rs.getString("file_path"));
        String ca = rs.getString("created_at");
        String ua = rs.getString("updated_at");
        note.setCreatedAt(ca != null ? LocalDateTime.parse(ca) : LocalDateTime.now());
        note.setUpdatedAt(ua != null ? LocalDateTime.parse(ua) : LocalDateTime.now());
        return note;
    }
}