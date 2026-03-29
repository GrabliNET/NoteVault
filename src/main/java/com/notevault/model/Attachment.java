package com.notevault.model;

/**
 * @brief Прикреплённый файл, связанный с конкретной заметкой.
 *
 * @details
 * Вложения хранятся **физически** в папке
 * {@code _attachments/<note-slug>/} внутри vault, а в БД (таблица
 * {@code attachments}) хранится только ссылка на файл — имя и
 * относительный путь. Сам бинарный контент в БД не попадает.
 *
 * ## Ссылка в Markdown
 * В тексте заметки вложение вставляется синтаксисом Obsidian-style:
 * @code{.md}
 * ![[photo.png]]
 * ![[document.pdf]]
 * @endcode
 * При рендере превью {@link com.notevault.service.MarkdownRenderer}
 * подставляет абсолютный URI файла в тег {@code <img>} или {@code <a>}.
 *
 * ## Удаление
 * При удалении заметки связанные записи в таблице {@code attachments}
 * каскадно удаляются (FK {@code ON DELETE CASCADE}). Физические файлы
 * удаляются отдельно в {@link com.notevault.service.VaultService#deleteNote}.
 *
 * @see com.notevault.db.AttachmentDAO
 * @see com.notevault.service.VaultService#addAttachment
 */
public class Attachment {

    /** @brief Первичный ключ в таблице {@code attachments}. */
    private long id;

    /** @brief Внешний ключ на таблицу {@code notes}. */
    private long noteId;

    /**
     * @brief Имя файла (без пути).
     *
     * Используется в wiki-ссылках {@code ![[fileName]]} внутри заметки.
     * Пример: {@code "diagram.png"}.
     */
    private String fileName;

    /**
     * @brief Путь к файлу относительно корня vault.
     *
     * Пример: {@code "_attachments/my-note/diagram.png"}.
     * Разделитель — {@code '/'}.
     */
    private String filePath;

    /** @brief Конструктор по умолчанию. */
    public Attachment() {}

    /**
     * @brief Создаёт запись о вложении.
     *
     * @param noteId   ID заметки-владельца.
     * @param fileName Имя файла.
     * @param filePath Относительный путь к файлу внутри vault.
     */
    public Attachment(long noteId, String fileName, String filePath) {
        this.noteId = noteId;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    /** @return Первичный ключ. */
    public long getId() { return id; }
    /** @param id Первичный ключ. */
    public void setId(long id) { this.id = id; }

    /** @return ID заметки-владельца. */
    public long getNoteId() { return noteId; }
    /** @param noteId ID заметки. */
    public void setNoteId(long noteId) { this.noteId = noteId; }

    /** @return Имя файла. */
    public String getFileName() { return fileName; }
    /** @param fileName Имя файла. */
    public void setFileName(String fileName) { this.fileName = fileName; }

    /** @return Относительный путь к файлу. */
    public String getFilePath() { return filePath; }
    /** @param filePath Относительный путь. */
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /** @return Имя файла. */
    @Override public String toString() { return fileName; }
}