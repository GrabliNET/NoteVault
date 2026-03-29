package com.notevault.service;

import com.notevault.db.*;
import com.notevault.model.*;
import com.notevault.util.FrontMatterParser;
import com.notevault.util.VaultLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * @brief Центральный сервисный слой приложения.
 *
 * @details
 * Координирует все операции, требующие одновременного изменения
 * **файловой системы** и **базы данных**. Является единственным местом,
 * где принимаются решения о физическом расположении файлов.
 *
 * ## Принцип двойного хранения
 * Каждая заметка существует в двух представлениях:
 * - **MD-файл** — источник истины для содержимого и базовых метаданных
 *   (теги, книга, порядок — через YAML front-matter).
 * - **SQLite** — источник истины для быстрого поиска, индексов, связей
 *   и агрегированных данных (счётчик тегов и т.д.).
 *
 * При любом изменении оба представления обновляются в рамках одного
 * публичного метода сервиса.
 *
 * ## Жизненный цикл vault
 * @code
 * VaultService.getInstance().openVault(path);  // 1. выбор vault
 * Note n = service.createNote("Title", "", tags); // 2. создание заметки
 * service.saveNote(n, newContent);               // 3. сохранение
 * service.deleteNote(n);                         // 4. удаление
 * @endcode
 *
 * \warning Не является потокобезопасным. Весь доступ должен производиться
 *          из Event Dispatch Thread или с внешней синхронизацией.
 *
 * @see com.notevault.util.VaultLayout
 * @see com.notevault.util.FrontMatterParser
 * @see com.notevault.db.NoteDAO
 */
public class VaultService {

    private static final Logger LOG = Logger.getLogger(VaultService.class.getName());

    /** @brief Единственный экземпляр (паттерн Singleton). */
    private static VaultService instance;

    /** @brief Менеджер физической раскладки файлов vault. */
    private VaultLayout layout;

    private final NoteDAO       noteDAO       = new NoteDAO();
    private final TagDAO        tagDAO        = new TagDAO();
    private final BookDAO       bookDAO       = new BookDAO();
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();

    private VaultService() {}

    /**
     * @brief Возвращает единственный экземпляр сервиса.
     * @return Экземпляр {@link VaultService}.
     */
    public static VaultService getInstance() {
        if (instance == null) instance = new VaultService();
        return instance;
    }

    // ── Vault lifecycle ───────────────────────────────────────────────────

    /**
     * @brief Открывает хранилище заметок по указанному пути.
     *
     * @details
     * -# Инициализирует {@link VaultLayout} для управления файловой структурой.
     * -# Создаёт системные директории vault ({@code _unsorted}, {@code _multi},
     *    {@code _attachments}, {@code _db}).
     * -# Открывает (или создаёт) SQLite БД через {@link DatabaseManager}.
     *
     * @param vaultRoot Абсолютный путь к корневой директории vault.
     * @throws Exception если не удалось создать директории или открыть БД.
     */
    public void openVault(Path vaultRoot) throws Exception {
        layout = new VaultLayout(vaultRoot);
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_UNSORTED));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_MULTI));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_ATTACHMENTS));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_DB));
        DatabaseManager.getInstance().openVault(vaultRoot.toString());
        LOG.info("Vault opened: " + vaultRoot);
    }

    /**
     * @brief Проверяет, открыт ли vault.
     * @return {@code true} если layout инициализирован и БД открыта.
     */
    public boolean isVaultOpen() {
        return layout != null && DatabaseManager.getInstance().isOpen();
    }

    /**
     * @brief Возвращает менеджер файловой раскладки.
     * @return Текущий {@link VaultLayout} или {@code null} если vault не открыт.
     */
    public VaultLayout getLayout() { return layout; }

    // ── Note CRUD ─────────────────────────────────────────────────────────

    /**
     * @brief Создаёт новую заметку с пустым телом и без тегов.
     *
     * @param title Заголовок заметки.
     * @return Созданная заметка с заполненным {@code id} и {@code filePath}.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public Note createNote(String title) throws Exception {
        return createNote(title, "", List.of());
    }

    /**
     * @brief Создаёт новую заметку с заданным содержимым и тегами.
     *
     * @details
     * Алгоритм:
     * -# Определяет целевую директорию по тегам через {@link VaultLayout#noteFilePath}.
     * -# Генерирует уникальное имя файла (добавляет суффикс при коллизии).
     * -# Строит полный текст файла (front-matter + тело) через {@link FrontMatterParser#buildContent}.
     * -# Записывает файл на диск.
     * -# Сохраняет метаданные в БД через {@link NoteDAO#insert}.
     *
     * @param title  Заголовок заметки.
     * @param body   Тело заметки в формате Markdown (без front-matter).
     * @param tags   Список тегов (может быть пустым).
     * @return Созданная и сохранённая заметка.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public Note createNote(String title, String body, List<Tag> tags) throws Exception {
        Note note = new Note(title, "");
        note.setTags(tags);
        resolveBookReference(note);

        Path filePath = layout.uniquePath(layout.noteFilePath(note));
        layout.ensureDirs(note);

        String content = FrontMatterParser.buildContent(note, body);
        note.setContent(content);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        note.setFilePath(layout.relativePath(filePath));
        noteDAO.insert(note);
        return note;
    }

    /**
     * @brief Сохраняет изменённое содержимое заметки.
     *
     * @details
     * Перечитывает front-matter из нового содержимого (пользователь мог
     * изменить теги вручную прямо в редакторе). При изменении тегов
     * файл может быть **перемещён** в другую директорию vault.
     *
     * @param note       Заметка (должна иметь корректный {@code id} и {@code filePath}).
     * @param newContent Полный новый текст файла (front-matter + тело).
     * @throws Exception при ошибке файловой системы или БД.
     */
    public void saveNote(Note note, String newContent) throws Exception {
        note.setContent(newContent);
        note.setUpdatedAt(LocalDateTime.now());

        FrontMatterParser.applyToNote(newContent, note);
        resolveBookReference(note);

        Path oldPath = layout.resolve(note.getFilePath());
        Path newPath = layout.relocateIfNeeded(note, oldPath);
        note.setFilePath(layout.relativePath(newPath));

        Files.writeString(newPath, newContent, StandardCharsets.UTF_8);
        noteDAO.update(note);
    }

    /**
     * @brief Обновляет метаданные заметки (теги, книга) без изменения тела.
     *
     * @details
     * Перестраивает front-matter файла на основе текущего состояния объекта
     * {@code note}, при необходимости перемещает файл.
     *
     * @param note Заметка с обновлёнными тегами / книгой.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public void updateNoteMeta(Note note) throws Exception {
        resolveBookReference(note);
        String body = FrontMatterParser.extractBody(note.getContent());
        String newContent = FrontMatterParser.buildContent(note, body);
        note.setContent(newContent);

        Path oldPath = layout.resolve(note.getFilePath());
        Path newPath = layout.relocateIfNeeded(note, oldPath);
        note.setFilePath(layout.relativePath(newPath));

        Files.writeString(newPath, newContent, StandardCharsets.UTF_8);
        noteDAO.update(note);
    }

    /**
     * @brief Переименовывает заметку и физически переименовывает файл.
     *
     * @details
     * Новое имя файла строится из нового заголовка через
     * {@link VaultLayout#sanitizeFileName}. Если файл с таким именем
     * уже существует, добавляется числовой суффикс.
     *
     * @param note     Заметка для переименования.
     * @param newTitle Новый заголовок.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public void renameNote(Note note, String newTitle) throws Exception {
        note.setTitle(newTitle);
        Path oldPath = layout.resolve(note.getFilePath());
        Path targetDir = layout.noteFilePath(note).getParent();
        String newFilename = VaultLayout.sanitizeFileName(newTitle) + ".md";
        Path newPath = layout.uniquePath(targetDir.resolve(newFilename));

        Files.createDirectories(newPath.getParent());
        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        note.setFilePath(layout.relativePath(newPath));

        String body = FrontMatterParser.extractBody(note.getContent());
        String newContent = FrontMatterParser.buildContent(note, body);
        note.setContent(newContent);
        Files.writeString(newPath, newContent, StandardCharsets.UTF_8);

        noteDAO.update(note);
    }

    /**
     * @brief Удаляет заметку: файл с диска и запись из БД.
     *
     * @details
     * Удаление из БД каскадно очищает {@code note_tags}, {@code book_notes}
     * и {@code attachments}. Директория вложений удаляется только если она пуста.
     *
     * @param note Заметка для удаления.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public void deleteNote(Note note) throws Exception {
        Path filePath = layout.resolve(note.getFilePath());
        Files.deleteIfExists(filePath);

        Path attDir = layout.attachmentDir(note);
        if (Files.isDirectory(attDir)) {
            try (var stream = Files.list(attDir)) {
                if (stream.findAny().isEmpty()) Files.delete(attDir);
            }
        }
        noteDAO.delete(note.getId());
    }

    /**
     * @brief Загружает содержимое MD-файла в объект заметки.
     *
     * @details
     * Читает файл с диска и разбирает front-matter через
     * {@link FrontMatterParser#applyToNote}. Вызывается лениво — только
     * при открытии заметки в редакторе.
     *
     * @param note Заметка с корректным {@code filePath}; содержимое будет перезаписано.
     * @return Тот же объект {@code note} с заполненным {@link Note#getContent()}.
     * @throws Exception если файл не найден или недоступен.
     */
    public Note loadNoteContent(Note note) throws Exception {
        Path filePath = layout.resolve(note.getFilePath());
        if (Files.exists(filePath)) {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            note.setContent(content);
            FrontMatterParser.applyToNote(content, note);
        }
        return note;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /**
     * @brief Возвращает все заметки хранилища.
     * @return Список заметок, отсортированных по дате изменения.
     * @throws Exception при ошибке запроса к БД.
     */
    public List<Note> getAllNotes() throws Exception { return noteDAO.findAll(); }

    /**
     * @brief Возвращает заметки с указанным тегом.
     * @param tag Тег с корректным {@code id}.
     * @return Список заметок.
     * @throws Exception при ошибке запроса к БД.
     */
    public List<Note> getNotesByTag(Tag tag) throws Exception {
        return noteDAO.findByTag(tag.getId());
    }

    /**
     * @brief Возвращает заметки книги с загруженным содержимым.
     *
     * @details
     * В отличие от {@link #getAllNotes()}, здесь содержимое файлов
     * загружается сразу — для последующей склейки в {@link #getBookContent}.
     *
     * @param book Книга с корректным {@code id}.
     * @return Заметки книги в порядке {@code order_index}.
     * @throws Exception при ошибке запроса или чтения файлов.
     */
    public List<Note> getNotesByBook(Book book) throws Exception {
        List<Note> notes = noteDAO.findByBook(book.getId());
        for (Note n : notes) loadNoteContent(n);
        return notes;
    }

    /**
     * @brief Выполняет поиск заметок по части заголовка.
     *
     * @param query Поисковый запрос (LIKE без учёта регистра).
     * @return Список подходящих заметок.
     * @throws Exception при ошибке запроса к БД.
     */
    public List<Note> searchNotes(String query) throws Exception {
        return noteDAO.search(query);
    }

    // ── Tags ──────────────────────────────────────────────────────────────

    /**
     * @brief Возвращает все теги с количеством заметок.
     * @return Список тегов, отсортированных по имени.
     * @throws Exception при ошибке запроса к БД.
     */
    public List<Tag> getAllTags() throws Exception { return tagDAO.findAll(); }

    /**
     * @brief Переименовывает тег и обновляет front-matter всех затронутых заметок.
     *
     * @details
     * После переименования в БД перечитывает все заметки с этим тегом
     * и перезаписывает их MD-файлы с обновлённым front-matter. Файлы
     * **не перемещаются** (имя директории тега не меняется автоматически).
     *
     * \note Если нужно переместить файлы в директорию с новым именем тега,
     *       это потребует дополнительной реализации.
     *
     * @param tag     Тег для переименования.
     * @param newName Новое имя тега.
     * @throws Exception при ошибке БД или файловой системы.
     */
    public void renameTag(Tag tag, String newName) throws Exception {
        List<Note> affected = noteDAO.findByTag(tag.getId());
        tagDAO.rename(tag.getId(), newName);
        tag.setName(newName);
        for (Note n : affected) {
            loadNoteContent(n);
            n.setTags(noteDAO.findTagsForNote(n.getId()));
            String body = FrontMatterParser.extractBody(n.getContent());
            String newContent = FrontMatterParser.buildContent(n, body);
            n.setContent(newContent);
            Files.writeString(layout.resolve(n.getFilePath()), newContent, StandardCharsets.UTF_8);
        }
    }

    /**
     * @brief Удаляет тег и очищает неиспользуемые теги.
     * @param tag Тег для удаления.
     * @throws Exception при ошибке БД.
     */
    public void deleteTag(Tag tag) throws Exception {
        tagDAO.delete(tag.getId());
        tagDAO.pruneUnused();
    }

    // ── Books ─────────────────────────────────────────────────────────────

    /**
     * @brief Возвращает все книги.
     * @return Список книг, отсортированных по имени.
     * @throws Exception при ошибке запроса к БД.
     */
    public List<Book> getAllBooks() throws Exception { return bookDAO.findAll(); }

    /**
     * @brief Создаёт новую книгу.
     *
     * @param name        Уникальное имя книги.
     * @param description Описание (может быть пустым).
     * @return Созданная книга с заполненным {@code id}.
     * @throws Exception при ошибке вставки в БД.
     */
    public Book createBook(String name, String description) throws Exception {
        Book book = new Book(name);
        book.setDescription(description);
        return bookDAO.insert(book);
    }

    /**
     * @brief Обновляет имя и описание книги.
     * @param book Книга с актуальными данными.
     * @throws Exception при ошибке обновления.
     */
    public void updateBook(Book book) throws Exception { bookDAO.update(book); }

    /**
     * @brief Удаляет книгу (заметки при этом сохраняются).
     *
     * Удаление из БД каскадно удаляет {@code book_notes}.
     * Физические файлы заметок не затрагиваются.
     *
     * @param book Книга для удаления.
     * @throws Exception при ошибке удаления.
     */
    public void deleteBook(Book book) throws Exception { bookDAO.delete(book.getId()); }

    // ── Attachments ───────────────────────────────────────────────────────

    /**
     * @brief Копирует файл в директорию вложений заметки и регистрирует в БД.
     *
     * @details
     * Файл копируется в {@code _attachments/<note-slug>/}. При коллизии имён
     * добавляется числовой суффикс. Исходный файл не удаляется.
     *
     * @param note       Заметка-владелец.
     * @param sourceFile Путь к исходному файлу.
     * @return Запись о вложении с заполненным {@code id}.
     * @throws Exception при ошибке файловой системы или БД.
     */
    public Attachment addAttachment(Note note, Path sourceFile) throws Exception {
        Path attDir = layout.attachmentDir(note);
        Files.createDirectories(attDir);
        String fileName = sourceFile.getFileName().toString();
        Path dest = attDir.resolve(fileName);
        if (Files.exists(dest)) {
            String base = fileName.contains(".") ?
                    fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String ext  = fileName.contains(".") ?
                    fileName.substring(fileName.lastIndexOf('.')) : "";
            int i = 1;
            while (Files.exists(dest)) dest = attDir.resolve(base + "-" + i++ + ext);
        }
        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        String relPath = layout.relativePath(dest);
        Attachment att = new Attachment(note.getId(), dest.getFileName().toString(), relPath);
        return attachmentDAO.insert(att);
    }

    /**
     * @brief Возвращает список вложений заметки.
     * @param note Заметка.
     * @return Список вложений.
     * @throws Exception при ошибке запроса.
     */
    public List<Attachment> getAttachments(Note note) throws Exception {
        return attachmentDAO.findByNote(note.getId());
    }

    /**
     * @brief Удаляет вложение: физический файл и запись в БД.
     * @param att Вложение для удаления.
     * @throws Exception при ошибке.
     */
    public void deleteAttachment(Attachment att) throws Exception {
        Files.deleteIfExists(layout.resolve(att.getFilePath()));
        attachmentDAO.delete(att.getId());
    }

    // ── Book content ──────────────────────────────────────────────────────

    /**
     * @brief Формирует объединённый Markdown-текст всех заметок книги.
     *
     * @details
     * Заметки берутся в порядке {@code order_index} и конкатенируются
     * через горизонтальную линию {@code ---}. Физические файлы не изменяются.
     * Результат используется для отображения книги в режиме чтения и
     * для экспорта в PDF.
     *
     * Формат результата:
     * @code{.md}
     * # Название книги
     *
     * Описание книги...
     *
     * <содержимое заметки 1>
     *
     * ---
     *
     * <содержимое заметки 2>
     * @endcode
     *
     * @param book Книга с корректным {@code id}.
     * @return Markdown-строка с объединённым содержимым.
     * @throws Exception при ошибке чтения файлов или запроса к БД.
     */
    public String getBookContent(Book book) throws Exception {
        List<Note> notes = getNotesByBook(book);
        if (notes.isEmpty()) return "*This book has no notes yet.*";
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(book.getName()).append("\n\n");
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            sb.append(book.getDescription()).append("\n\n");
        }
        for (int i = 0; i < notes.size(); i++) {
            String body = FrontMatterParser.extractBody(notes.get(i).getContent());
            if (!body.isBlank()) {
                sb.append(body.stripTrailing());
                if (i < notes.size() - 1) sb.append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * @brief Разрешает имя книги из {@code metaExtra} в ID существующей или новой книги.
     *
     * @details
     * Если в {@link Note#getMetaExtra()} есть ключ {@code "bookName"},
     * ищет книгу с таким именем в БД. Если не найдена — создаёт автоматически.
     * Результат записывается в {@link Note#setBookId(Long)}.
     *
     * @param note Заметка для обработки.
     * @throws Exception при ошибке обращения к БД.
     */
    private void resolveBookReference(Note note) throws Exception {
        String bookName = note.getMetaExtra().get("bookName");
        if (bookName == null || bookName.isBlank()) return;
        for (Book b : bookDAO.findAll()) {
            if (b.getName().equalsIgnoreCase(bookName)) {
                note.setBookId(b.getId());
                return;
            }
        }
        Book newBook = createBook(bookName, "");
        note.setBookId(newBook.getId());
    }
}