package com.notevault.util;

import com.notevault.model.Note;
import com.notevault.model.Tag;

import java.io.File;
import java.nio.file.*;
import java.util.List;

/**
 * Manages the physical folder layout inside a vault directory.
 *
 * Layout rules:
 *   - 0 tags     → _unsorted/
 *   - 1 tag      → <tagname>/
 *   - 2+ tags    → _multi/
 *   - Attachments → _attachments/<note-slug>/
 *   - Database   → _db/
 */
public class VaultLayout {

    public static final String DIR_UNSORTED    = "_unsorted";
    public static final String DIR_MULTI       = "_multi";
    public static final String DIR_ATTACHMENTS = "_attachments";
    public static final String DIR_DB          = "_db";

    private final Path vaultRoot;

    public VaultLayout(Path vaultRoot) {
        this.vaultRoot = vaultRoot;
    }

    public Path getVaultRoot() { return vaultRoot; }

    /** Determine the folder (relative to vault root) a note should live in */
    public String folderForNote(Note note) {
        List<Tag> tags = note.getTags();
        if (tags == null || tags.isEmpty()) return DIR_UNSORTED;
        if (tags.size() == 1) return sanitizeDirName(tags.get(0).getName());
        return DIR_MULTI;
    }

    /** Full absolute path where the note file should be stored */
    public Path noteFilePath(Note note) {
        String folder = folderForNote(note);
        String filename = sanitizeFileName(note.getTitle()) + ".md";
        return vaultRoot.resolve(folder).resolve(filename);
    }

    /** Attachment directory for a note */
    public Path attachmentDir(Note note) {
        String slug = sanitizeFileName(note.getTitle());
        return vaultRoot.resolve(DIR_ATTACHMENTS).resolve(slug);
    }

    /** Create all necessary directories */
    public void ensureDirs(Note note) throws Exception {
        Files.createDirectories(noteFilePath(note).getParent());
        Files.createDirectories(attachmentDir(note));
    }

    /** Move a note file if its tag situation changed */
    public Path relocateIfNeeded(Note note, Path currentPath) throws Exception {
        Path targetPath = noteFilePath(note);
        if (!currentPath.equals(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            Files.move(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        }
        return currentPath;
    }

    /** Compute path relative to vault root */
    public String relativePath(Path absolutePath) {
        return vaultRoot.relativize(absolutePath).toString().replace(File.separatorChar, '/');
    }

    /** Resolve a relative path back to absolute */
    public Path resolve(String relativePath) {
        return vaultRoot.resolve(relativePath);
    }

    // ---- Sanitization helpers ----

    public static String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) return "untitled";
        return title.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "-")
                .toLowerCase()
                .replaceAll("-{2,}", "-");
    }

    public static String sanitizeDirName(String name) {
        if (name == null || name.isBlank()) return DIR_UNSORTED;
        return name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    /** Ensure file name is unique in its folder by appending a counter */
    public Path uniquePath(Path desired) {
        if (!Files.exists(desired)) return desired;
        Path parent = desired.getParent();
        String name = desired.getFileName().toString();
        String base = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
        int counter = 1;
        Path candidate;
        do {
            candidate = parent.resolve(base + "-" + counter + ".md");
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }
}