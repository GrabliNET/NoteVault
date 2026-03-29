package com.notevault.util;

import com.notevault.model.Note;
import com.notevault.model.Tag;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * @brief Парсер и генератор YAML front-matter блока в MD-файлах.
 *
 * @details
 * Обеспечивает двунаправленную конвертацию между YAML front-matter
 * внутри Markdown-файлов и полями объекта {@link Note}.
 *
 * ## Поддерживаемые поля front-matter
 * | Поле    | Тип           | Описание                              |
 * |---------|---------------|---------------------------------------|
 * | `tags`  | список строк  | Теги заметки                          |
 * | `book`  | список строк  | Имя книги (берётся первый элемент)    |
 * | `order` | целое число   | Позиция в книге (с нуля)              |
 *
 * ## Пример front-matter
 * @code{.yaml}
 * ---
 * tags:
 *   - java
 *   - tutorial
 * book:
 *   - java_guide
 * order: 2
 * ---
 * @endcode
 *
 * ## Синтаксис вложений
 * Ссылки на прикреплённые файлы используют Obsidian-style синтаксис:
 * @code{.md}
 * ![[image.png]]
 * ![[document.pdf]]
 * @endcode
 * {@link #extractAttachmentRefs(String)} выделяет имена файлов из таких ссылок.
 *
 * \note Класс является утилитным — все методы статические, экземпляр не создаётся.
 *
 * @see com.notevault.service.VaultService
 * @see com.notevault.service.MarkdownRenderer
 */
public class FrontMatterParser {

    /** @brief SnakeYAML инстанс для десериализации front-matter. Не потокобезопасен сам по себе. */
    private static final Yaml YAML = new Yaml();

    /** @brief Утилитный класс — конструктор недоступен. */
    private FrontMatterParser() {}

    /**
     * @brief Разбирает front-matter файла и заполняет поля объекта {@link Note}.
     *
     * @details
     * Если front-matter отсутствует или не содержит нужных полей,
     * поля объекта {@code note} не изменяются.
     *
     * @param content Полное содержимое MD-файла (front-matter + тело).
     * @param note    Объект заметки, в который записываются распознанные поля.
     */
    @SuppressWarnings("unchecked")
    public static void applyToNote(String content, Note note) {
        Map<String, Object> fm = extractFrontMatter(content);
        if (fm == null) return;

        Object tagsObj = fm.get("tags");
        if (tagsObj instanceof List<?> tagList) {
            List<Tag> tags = new ArrayList<>();
            for (Object t : tagList) {
                if (t != null) tags.add(new Tag(t.toString().trim()));
            }
            note.setTags(tags);
        }

        Object bookObj = fm.get("book");
        if (bookObj instanceof List<?> bookList && !bookList.isEmpty()) {
            note.getMetaExtra().put("bookName", bookList.get(0).toString().trim());
        } else if (bookObj instanceof String s) {
            note.getMetaExtra().put("bookName", s.trim());
        }

        Object orderObj = fm.get("order");
        if (orderObj instanceof Integer i) {
            note.setBookOrder(i);
        } else if (orderObj instanceof Number n) {
            note.setBookOrder(n.intValue());
        }
    }

    /**
     * @brief Строит полный текст MD-файла: front-matter (из полей Note) + тело.
     *
     * @details
     * Front-matter генерируется вручную (без SnakeYAML) для контроля
     * форматирования вывода и минимизации лишних полей. Если у заметки
     * нет тегов и она не принадлежит книге — front-matter не добавляется.
     *
     * @param note Заметка с актуальными тегами, книгой и порядком.
     * @param body Тело заметки (без front-matter).
     * @return Полный текст файла, готовый для записи на диск.
     */
    @SuppressWarnings("unchecked")
    public static String buildContent(Note note, String body) {
        Map<String, Object> fm = new LinkedHashMap<>();

        if (note.getTags() != null && !note.getTags().isEmpty()) {
            List<String> tagNames = new ArrayList<>();
            for (Tag t : note.getTags()) tagNames.add(t.getName());
            fm.put("tags", tagNames);
        }
        if (note.getMetaExtra() != null && note.getMetaExtra().containsKey("bookName")) {
            fm.put("book", List.of(note.getMetaExtra().get("bookName")));
        }
        if (note.getBookOrder() != null) {
            fm.put("order", note.getBookOrder());
        }

        if (fm.isEmpty()) return body != null ? body : "";

        StringBuilder sb = new StringBuilder("---\n");
        if (fm.containsKey("tags")) {
            sb.append("tags:\n");
            for (String t : (List<String>) fm.get("tags"))
                sb.append("  - ").append(t).append("\n");
        }
        if (fm.containsKey("book")) {
            sb.append("book:\n");
            for (Object b : (List<?>) fm.get("book"))
                sb.append("  - ").append(b).append("\n");
        }
        if (fm.containsKey("order")) {
            sb.append("order: ").append(fm.get("order")).append("\n");
        }
        sb.append("---\n");
        if (body != null && !body.isEmpty()) sb.append(body);
        return sb.toString();
    }

    /**
     * @brief Извлекает сырой front-matter блок как Map.
     *
     * @param content Полный текст MD-файла.
     * @return Распарсенная карта полей или {@code null} если front-matter отсутствует.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractFrontMatter(String content) {
        if (content == null || !content.startsWith("---")) return null;
        int end = content.indexOf("\n---", 3);
        if (end == -1) return null;
        String yamlBlock = content.substring(4, end).trim();
        try {
            Object parsed = YAML.load(yamlBlock);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * @brief Возвращает тело заметки без YAML front-matter.
     *
     * @param content Полный текст MD-файла.
     * @return Тело без front-matter (пустая строка если {@code content == null}).
     */
    public static String extractBody(String content) {
        if (content == null) return "";
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("\n---", 3);
        if (end == -1) return content;
        return content.substring(end + 4).stripLeading();
    }

    /**
     * @brief Извлекает список имён файлов из Obsidian-style ссылок {@code ![[file]]}.
     *
     * @details
     * Ищет все вхождения паттерна {@code ![[...]]}} в теле заметки
     * (front-matter при этом исключается).
     *
     * @param content Полный текст MD-файла.
     * @return Список имён файлов (без скобок и пути).
     */
    public static List<String> extractAttachmentRefs(String content) {
        List<String> refs = new ArrayList<>();
        String body = extractBody(content);
        int i = 0;
        while ((i = body.indexOf("![[", i)) != -1) {
            int end = body.indexOf("]]", i + 3);
            if (end == -1) break;
            refs.add(body.substring(i + 3, end).trim());
            i = end + 2;
        }
        return refs;
    }
}