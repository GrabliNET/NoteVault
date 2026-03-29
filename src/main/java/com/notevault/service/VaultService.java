package com.notevault.service;

import com.notevault.db.*;
import com.notevault.model.*;
import com.notevault.util.FrontMatterParser;
import com.notevault.util.VaultLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Central service: coordinates DB + file-system operations.
 * All note CRUD goes through here.
 */
public class VaultService {

    private static final Logger LOG = Logger.getLogger(VaultService.class.getName());
    private static VaultService instance;

    private VaultLayout layout;
    private final NoteDAO noteDAO = new NoteDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final BookDAO bookDAO = new BookDAO();
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();

    private VaultService() {}

    public static VaultService getInstance() {
        if (instance == null) instance = new VaultService();
        return instance;
    }

    // ---- Vault lifecycle ----

    public void openVault(Path vaultRoot) throws Exception {
        layout = new VaultLayout(vaultRoot);
        // Create base dirs
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_UNSORTED));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_MULTI));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_ATTACHMENTS));
        Files.createDirectories(vaultRoot.resolve(VaultLayout.DIR_DB));
        DatabaseManager.getInstance().openVault(vaultRoot.toString());
        LOG.info("Vault opened: " + vaultRoot);
    }

    public boolean isVaultOpen() {
        return layout != null && DatabaseManager.getInstance().isOpen();
    }

    public VaultLayout getLayout() { return layout; }

    // ---- Note CRUD ----

    public Note createNote(String title) throws Exception {
        return createNote(title, "", List.of());
    }

    public Note createNote(String title, String body, List<Tag> tags) throws Exception {
        Note note = new Note(title, "");
        note.setTags(tags);

        // Resolve book name → ID if present
        resolveBookReference(note);

        // Determine file path
        Path filePath = layout.uniquePath(layout.noteFilePath(note));
        layout.ensureDirs(note);

        // Build content with front-matter
        String content = FrontMatterParser.buildContent(note, body);
        note.setContent(content);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        note.setFilePath(layout.relativePath(filePath));
        noteDAO.insert(note);
        return note;
    }

    public void saveNote(Note note, String newContent) throws Exception {
        note.setContent(newContent);
        note.setUpdatedAt(LocalDateTime.now());

        // Re-parse front-matter from content (user may have edited it directly)
        List<Tag> oldTags = note.getTags();
        FrontMatterParser.applyToNote(newContent, note);
        resolveBookReference(note);

        Path oldPath = layout.resolve(note.getFilePath());
        Path newPath = layout.relocateIfNeeded(note, oldPath);
        note.setFilePath(layout.relativePath(newPath));

        Files.writeString(newPath, newContent, StandardCharsets.UTF_8);
        noteDAO.update(note);
    }

    public void updateNoteMeta(Note note) throws Exception {
        // Tags or book changed from UI — rebuild front-matter
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

    public void renameNote(Note note, String newTitle) throws Exception {
        note.setTitle(newTitle);
        Path oldPath = layout.resolve(note.getFilePath());

        // Calculate new path based on title + tags
        Path targetDir = layout.noteFilePath(note).getParent();
        String newFilename = VaultLayout.sanitizeFileName(newTitle) + ".md";
        Path newPath = layout.uniquePath(targetDir.resolve(newFilename));

        Files.createDirectories(newPath.getParent());
        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        note.setFilePath(layout.relativePath(newPath));

        // Rebuild content with updated front-matter
        String body = FrontMatterParser.extractBody(note.getContent());
        String newContent = FrontMatterParser.buildContent(note, body);
        note.setContent(newContent);
        Files.writeString(newPath, newContent, StandardCharsets.UTF_8);

        noteDAO.update(note);
    }

    public void deleteNote(Note note) throws Exception {
        Path filePath = layout.resolve(note.getFilePath());
        Files.deleteIfExists(filePath);
        // Clean up attachment dir if empty
        Path attDir = layout.attachmentDir(note);
        if (Files.isDirectory(attDir)) {
            try (var stream = Files.list(attDir)) {
                if (stream.findAny().isEmpty()) Files.delete(attDir);
            }
        }
        noteDAO.delete(note.getId());
    }

    public Note loadNoteContent(Note note) throws Exception {
        Path filePath = layout.resolve(note.getFilePath());
        if (Files.exists(filePath)) {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            note.setContent(content);
            FrontMatterParser.applyToNote(content, note);
        }
        return note;
    }

    // ---- Queries ----

    public List<Note> getAllNotes() throws Exception {
        return noteDAO.findAll();
    }

    public List<Note> getNotesByTag(Tag tag) throws Exception {
        return noteDAO.findByTag(tag.getId());
    }

    public List<Note> getNotesByBook(Book book) throws Exception {
        List<Note> notes = noteDAO.findByBook(book.getId());
        for (Note n : notes) loadNoteContent(n);
        return notes;
    }

    public List<Note> searchNotes(String query) throws Exception {
        return noteDAO.search(query);
    }

    // ---- Tags ----

    public List<Tag> getAllTags() throws Exception {
        return tagDAO.findAll();
    }

    public void renameTag(Tag tag, String newName) throws Exception {
        // Update all notes that had this tag
        List<Note> affected = noteDAO.findByTag(tag.getId());
        tagDAO.rename(tag.getId(), newName);
        tag.setName(newName);
        // Rebuild front-matter in affected files
        for (Note n : affected) {
            loadNoteContent(n);
            n.setTags(noteDAO.findTagsForNote(n.getId()));
            String body = FrontMatterParser.extractBody(n.getContent());
            String newContent = FrontMatterParser.buildContent(n, body);
            n.setContent(newContent);
            Files.writeString(layout.resolve(n.getFilePath()), newContent, StandardCharsets.UTF_8);
        }
    }

    public void deleteTag(Tag tag) throws Exception {
        tagDAO.delete(tag.getId());
        tagDAO.pruneUnused();
    }

    // ---- Books ----

    public List<Book> getAllBooks() throws Exception {
        return bookDAO.findAll();
    }

    public Book createBook(String name, String description) throws Exception {
        Book book = new Book(name);
        book.setDescription(description);
        return bookDAO.insert(book);
    }

    public void updateBook(Book book) throws Exception {
        bookDAO.update(book);
    }

    public void deleteBook(Book book) throws Exception {
        bookDAO.delete(book.getId());
    }

    // ---- Attachments ----

    public Attachment addAttachment(Note note, Path sourceFile) throws Exception {
        Path attDir = layout.attachmentDir(note);
        Files.createDirectories(attDir);
        String fileName = sourceFile.getFileName().toString();
        Path dest = attDir.resolve(fileName);
        // Avoid overwriting
        if (Files.exists(dest)) {
            String base = fileName.contains(".") ?
                    fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String ext = fileName.contains(".") ?
                    fileName.substring(fileName.lastIndexOf('.')) : "";
            int i = 1;
            while (Files.exists(dest)) dest = attDir.resolve(base + "-" + i++ + ext);
        }
        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        String relPath = layout.relativePath(dest);
        Attachment att = new Attachment(note.getId(), dest.getFileName().toString(), relPath);
        return attachmentDAO.insert(att);
    }

    public List<Attachment> getAttachments(Note note) throws Exception {
        return attachmentDAO.findByNote(note.getId());
    }

    public void deleteAttachment(Attachment att) throws Exception {
        Path filePath = layout.resolve(att.getFilePath());
        Files.deleteIfExists(filePath);
        attachmentDAO.delete(att.getId());
    }

    // ---- Book combined view ----

    /**
     * Returns merged markdown text of all notes in a book (for read view).
     * Notes are concatenated in order with a horizontal rule separator.
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
            Note n = notes.get(i);
            String body = FrontMatterParser.extractBody(n.getContent());
            if (!body.isBlank()) {
                sb.append(body.stripTrailing());
                if (i < notes.size() - 1) sb.append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }

    // ---- Internal helpers ----

    private void resolveBookReference(Note note) throws Exception {
        String bookName = note.getMetaExtra().get("bookName");
        if (bookName == null || bookName.isBlank()) return;
        // Find or create book by name
        List<Book> books = bookDAO.findAll();
        for (Book b : books) {
            if (b.getName().equalsIgnoreCase(bookName)) {
                note.setBookId(b.getId());
                return;
            }
        }
        // Create the book automatically
        Book newBook = createBook(bookName, "");
        note.setBookId(newBook.getId());
    }
}