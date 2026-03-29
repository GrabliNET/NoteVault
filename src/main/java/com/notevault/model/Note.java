package com.notevault.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @brief Модель заметки — центральная сущность приложения.
 *
 * @details
 * Каждая заметка соответствует одному файлу в формате Markdown внутри
 * хранилища (vault). Путь к файлу хранится в поле {@link #filePath}
 * относительно корня vault, что обеспечивает переносимость хранилища.
 *
 * ## Структура файла заметки
 * Файл начинается с YAML front-matter блока, за которым следует тело заметки:
 * @code{.yaml}
 * ---
 * tags:
 *   - java
 * book:
 *   - java_guide
 * order: 3
 * ---
 * # Заголовок заметки
 *
 * Текст заметки...
 * @endcode
 *
 * ## Размещение файла в vault
 * Физическое расположение файла определяется количеством тегов:
 * - **0 тегов** → `_unsorted/<slug>.md`
 * - **1 тег**   → `<tag>/<slug>.md`
 * - **2+ тегов** → `_multi/<slug>.md`
 *
 * @see com.notevault.util.VaultLayout
 * @see com.notevault.util.FrontMatterParser
 * @see com.notevault.db.NoteDAO
 */
public class Note {

    /** @brief Первичный ключ в таблице {@code notes} базы данных. 0 — заметка ещё не сохранена. */
    private long id;

    /** @brief Заголовок заметки (отображается в списке и используется как имя файла). */
    private String title;

    /**
     * @brief Путь к MD-файлу относительно корня vault.
     *
     * Пример: {@code "java/_my-note.md"} или {@code "_unsorted/untitled.md"}.
     * Разделитель всегда {@code '/'} независимо от ОС.
     */
    private String filePath;

    /**
     * @brief Полное содержимое файла, включая YAML front-matter.
     *
     * \note Поле заполняется лениво — только после вызова
     *       {@link com.notevault.service.VaultService#loadNoteContent(Note)}.
     *       При получении заметок из БД это поле равно {@code null}.
     */
    private String content;

    /** @brief Дата и время создания заметки (UTC, сохраняется в БД как ISO-строка). */
    private LocalDateTime createdAt;

    /** @brief Дата и время последнего изменения (обновляется при каждом сохранении). */
    private LocalDateTime updatedAt;

    /**
     * @brief Список тегов, привязанных к заметке.
     *
     * Синхронизируется с таблицей {@code note_tags} через
     * {@link com.notevault.db.NoteDAO#syncTags(Note)} (private).
     * Также отражается в YAML front-matter файла.
     */
    private List<Tag> tags = new ArrayList<>();

    /**
     * @brief Идентификатор книги, к которой принадлежит заметка.
     *
     * {@code null} означает, что заметка не входит ни в одну книгу.
     * @see com.notevault.model.Book
     */
    private Long bookId;

    /**
     * @brief Порядковый номер заметки внутри книги (поле {@code order} в front-matter).
     *
     * Определяет последовательность при склейке заметок в книгу.
     * Нумерация с нуля; {@code null} если заметка не входит в книгу.
     */
    private Integer bookOrder;

    /**
     * @brief Дополнительные метаданные, не хранящиеся в отдельных полях.
     *
     * @details
     * Используется как временное хранилище при парсинге front-matter.
     * На данный момент содержит единственный ключ:
     * - {@code "bookName"} — имя книги из front-matter до разрешения в {@link #bookId}.
     *
     * \warning Не персистируется напрямую; является вспомогательным полем
     *          для передачи данных между {@link com.notevault.util.FrontMatterParser}
     *          и {@link com.notevault.service.VaultService}.
     */
    private Map<String, String> metaExtra = new HashMap<>();

    /** @brief Конструктор по умолчанию (требуется для маппинга из ResultSet). */
    public Note() {}

    /**
     * @brief Создаёт новую заметку с заданным заголовком и путём к файлу.
     *
     * @param title    Заголовок заметки.
     * @param filePath Относительный путь к MD-файлу (может быть пустым до первого сохранения).
     */
    public Note(String title, String filePath) {
        this.title = title;
        this.filePath = filePath;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    /** @return Первичный ключ в БД. */
    public long getId() { return id; }
    /** @param id Первичный ключ. */
    public void setId(long id) { this.id = id; }

    /** @return Заголовок заметки. */
    public String getTitle() { return title; }
    /** @param title Заголовок заметки. */
    public void setTitle(String title) { this.title = title; }

    /** @return Относительный путь к файлу заметки внутри vault. */
    public String getFilePath() { return filePath; }
    /** @param filePath Относительный путь к файлу. */
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /** @return Полное содержимое файла (front-matter + тело), или {@code null} если не загружено. */
    public String getContent() { return content; }
    /** @param content Полное содержимое файла. */
    public void setContent(String content) { this.content = content; }

    /** @return Дата создания. */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt Дата создания. */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** @return Дата последнего изменения. */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** @param updatedAt Дата изменения. */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** @return Список тегов заметки (не {@code null}, может быть пустым). */
    public List<Tag> getTags() { return tags; }
    /** @param tags Новый список тегов. */
    public void setTags(List<Tag> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    /** @return ID книги или {@code null}. */
    public Long getBookId() { return bookId; }
    /** @param bookId ID книги или {@code null}. */
    public void setBookId(Long bookId) { this.bookId = bookId; }

    /** @return Порядковый номер в книге или {@code null}. */
    public Integer getBookOrder() { return bookOrder; }
    /** @param bookOrder Порядковый номер. */
    public void setBookOrder(Integer bookOrder) { this.bookOrder = bookOrder; }

    /** @return Карта дополнительных метаданных (не {@code null}). */
    public Map<String, String> getMetaExtra() { return metaExtra; }
    /** @param metaExtra Карта доп. метаданных. */
    public void setMetaExtra(Map<String, String> metaExtra) { this.metaExtra = metaExtra; }

    // ── Utility ───────────────────────────────────────────────────────────

    /**
     * @brief Возвращает тело заметки без YAML front-matter блока.
     *
     * @details
     * Если файл начинается с {@code ---}, ищет закрывающий {@code ---} и
     * возвращает всё после него. Если front-matter отсутствует — возвращает
     * {@link #content} целиком.
     *
     * @return Текст заметки без front-matter, или пустая строка если {@link #content} равен {@code null}.
     */
    public String getBodyContent() {
        if (content == null) return "";
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end != -1) return content.substring(end + 4).stripLeading();
        }
        return content;
    }

    /**
     * @return Заголовок заметки, или {@code "(Untitled)"} если заголовок не задан.
     */
    @Override
    public String toString() {
        return title != null ? title : "(Untitled)";
    }
}