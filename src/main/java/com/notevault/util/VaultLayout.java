package com.notevault.util;

import com.notevault.model.Note;
import com.notevault.model.Tag;

import java.io.File;
import java.nio.file.*;
import java.util.List;

/**
 * @brief Менеджер физической структуры директорий внутри vault.
 *
 * @details
 * Инкапсулирует все правила размещения файлов: какой заметке
 * соответствует какой путь, где лежат вложения, как избежать коллизий имён.
 *
 * ## Структура vault
 * @code
 * vault/
 * ├── _db/
 * │   └── notes.db          ← SQLite база данных
 * ├── _unsorted/
 * │   └── *.md              ← заметки без тегов
 * ├── _multi/
 * │   └── *.md              ← заметки с двумя и более тегами
 * ├── _attachments/
 * │   └── <note-slug>/
 * │       └── <file>        ← вложения конкретной заметки
 * └── <tag>/
 *     └── *.md              ← заметки ровно с одним тегом
 * @endcode
 *
 * ## Правила размещения
 * | Число тегов | Директория           |
 * |-------------|----------------------|
 * | 0           | `_unsorted/`         |
 * | 1           | `<tagname>/`         |
 * | 2+          | `_multi/`            |
 *
 * Эта логика позволяет пользователю ориентироваться в хранилище
 * даже без приложения: тематические заметки лежат в папках с понятными именами.
 *
 * \note Класс работает только с путями; чтение/запись файлов —
 *       ответственность {@link com.notevault.service.VaultService}.
 *
 * @see com.notevault.service.VaultService
 */
public class VaultLayout {

    /** @brief Имя директории для заметок без тегов. */
    public static final String DIR_UNSORTED    = "_unsorted";

    /** @brief Имя директории для заметок с несколькими тегами. */
    public static final String DIR_MULTI       = "_multi";

    /** @brief Имя корневой директории вложений. */
    public static final String DIR_ATTACHMENTS = "_attachments";

    /** @brief Имя директории базы данных. */
    public static final String DIR_DB          = "_db";

    /** @brief Корневая директория vault. */
    private final Path vaultRoot;

    /**
     * @brief Создаёт экземпляр для указанного vault.
     * @param vaultRoot Абсолютный путь к корню vault.
     */
    public VaultLayout(Path vaultRoot) {
        this.vaultRoot = vaultRoot;
    }

    /** @return Корневая директория vault. */
    public Path getVaultRoot() { return vaultRoot; }

    /**
     * @brief Определяет имя директории для заметки на основе её тегов.
     *
     * @param note Заметка (теги должны быть заполнены).
     * @return Имя поддиректории относительно vault root.
     */
    public String folderForNote(Note note) {
        List<Tag> tags = note.getTags();
        if (tags == null || tags.isEmpty()) return DIR_UNSORTED;
        if (tags.size() == 1) return sanitizeDirName(tags.get(0).getName());
        return DIR_MULTI;
    }

    /**
     * @brief Вычисляет ожидаемый абсолютный путь к MD-файлу заметки.
     *
     * @details
     * Путь строится по схеме: {@code <vaultRoot>/<folder>/<slug>.md},
     * где {@code <folder>} определяется тегами, а {@code <slug>} —
     * санированным заголовком.
     *
     * \warning Возвращаемый путь может уже существовать. Для гарантии
     *          уникальности используйте {@link #uniquePath(Path)}.
     *
     * @param note Заметка с заполненными заголовком и тегами.
     * @return Абсолютный {@link Path} к MD-файлу.
     */
    public Path noteFilePath(Note note) {
        String folder   = folderForNote(note);
        String filename = sanitizeFileName(note.getTitle()) + ".md";
        return vaultRoot.resolve(folder).resolve(filename);
    }

    /**
     * @brief Возвращает директорию вложений для заметки.
     *
     * Путь: {@code <vaultRoot>/_attachments/<note-slug>/}
     *
     * @param note Заметка с заполненным заголовком.
     * @return Абсолютный {@link Path} к директории вложений.
     */
    public Path attachmentDir(Note note) {
        return vaultRoot.resolve(DIR_ATTACHMENTS).resolve(sanitizeFileName(note.getTitle()));
    }

    /**
     * @brief Создаёт все директории, необходимые для размещения заметки.
     *
     * Создаёт родительскую директорию MD-файла и директорию вложений.
     *
     * @param note Заметка.
     * @throws Exception если создание директорий завершилось ошибкой.
     */
    public void ensureDirs(Note note) throws Exception {
        Files.createDirectories(noteFilePath(note).getParent());
        Files.createDirectories(attachmentDir(note));
    }

    /**
     * @brief Перемещает файл заметки если её теги изменились.
     *
     * @details
     * Сравнивает {@code currentPath} с ожидаемым путём по текущим тегам.
     * Если пути различаются — перемещает файл через {@code Files.move}.
     *
     * @param note        Заметка с актуальными тегами.
     * @param currentPath Текущий абсолютный путь к файлу.
     * @return Новый (или тот же) абсолютный путь к файлу.
     * @throws Exception при ошибке перемещения.
     */
    public Path relocateIfNeeded(Note note, Path currentPath) throws Exception {
        Path targetPath = noteFilePath(note);
        if (!currentPath.equals(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            Files.move(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        }
        return currentPath;
    }

    /**
     * @brief Вычисляет путь относительно корня vault.
     *
     * @details
     * Разделитель в возвращаемой строке всегда {@code '/'} независимо от ОС.
     *
     * @param absolutePath Абсолютный путь внутри vault.
     * @return Относительный путь с {@code '/'} в качестве разделителя.
     */
    public String relativePath(Path absolutePath) {
        return vaultRoot.relativize(absolutePath).toString().replace(File.separatorChar, '/');
    }

    /**
     * @brief Разрешает относительный путь в абсолютный.
     *
     * @param relativePath Относительный путь (разделитель {@code '/'}).
     * @return Абсолютный {@link Path}.
     */
    public Path resolve(String relativePath) {
        return vaultRoot.resolve(relativePath);
    }

    // ── Filename helpers ──────────────────────────────────────────────────

    /**
     * @brief Преобразует заголовок заметки в безопасное имя файла (без расширения).
     *
     * @details
     * Алгоритм:
     * -# Обрезает пробелы.
     * -# Заменяет символы, недопустимые в именах файлов, на {@code _}.
     * -# Заменяет пробелы на {@code -}.
     * -# Приводит к нижнему регистру.
     * -# Сжимает множественные дефисы.
     *
     * @param title Заголовок заметки.
     * @return Безопасное имя файла или {@code "untitled"} если заголовок пуст.
     */
    public static String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) return "untitled";
        return title.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "-")
                .toLowerCase()
                .replaceAll("-{2,}", "-");
    }

    /**
     * @brief Преобразует имя тега в безопасное имя директории.
     *
     * Аналог {@link #sanitizeFileName} для директорий.
     *
     * @param name Имя тега.
     * @return Безопасное имя директории или {@code "_unsorted"} если имя пусто.
     */
    public static String sanitizeDirName(String name) {
        if (name == null || name.isBlank()) return DIR_UNSORTED;
        return name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    /**
     * @brief Возвращает уникальный путь, добавляя числовой суффикс при коллизии.
     *
     * @details
     * Если {@code desired} не существует — возвращает его без изменений.
     * Иначе перебирает {@code base-1.md}, {@code base-2.md} и т.д.
     * до первого несуществующего варианта.
     *
     * @param desired Желаемый путь (обычно {@code noteFilePath(note)}).
     * @return Гарантированно несуществующий путь.
     */
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