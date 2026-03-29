package com.notevault.util;

import com.notevault.model.Note;
import com.notevault.model.Tag;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Reads and writes the YAML front-matter block at the top of Markdown files.
 *
 * Supported fields:
 *   tags:      list of strings
 *   book:      list with single book name (we use first entry)
 *   order:     integer (position within book)
 */
public class FrontMatterParser {

    private static final Yaml YAML = new Yaml();

    /** Parse front-matter from full file content and populate Note fields */
    public static void applyToNote(String content, Note note) {
        Map<String, Object> fm = extractFrontMatter(content);
        if (fm == null) return;

        // Tags
        Object tagsObj = fm.get("tags");
        if (tagsObj instanceof List<?> tagList) {
            List<Tag> tags = new ArrayList<>();
            for (Object t : tagList) {
                if (t != null) tags.add(new Tag(t.toString().trim()));
            }
            note.setTags(tags);
        }

        // Book
        Object bookObj = fm.get("book");
        if (bookObj instanceof List<?> bookList && !bookList.isEmpty()) {
            // book name stored; caller resolves to ID
            String bookName = bookList.get(0).toString().trim();
            note.getMetaExtra().put("bookName", bookName);
        } else if (bookObj instanceof String s) {
            note.getMetaExtra().put("bookName", s.trim());
        }

        // Order
        Object orderObj = fm.get("order");
        if (orderObj instanceof Integer i) {
            note.setBookOrder(i);
        } else if (orderObj instanceof Number n) {
            note.setBookOrder(n.intValue());
        }
    }

    /** Build YAML front-matter string from Note and prepend to body */
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

        if (fm.isEmpty()) {
            return body != null ? body : "";
        }

        StringBuilder sb = new StringBuilder("---\n");
        // Manual serialization for clean output
        if (fm.containsKey("tags")) {
            sb.append("tags:\n");
            for (String t : (List<String>) fm.get("tags")) {
                sb.append("  - ").append(t).append("\n");
            }
        }
        if (fm.containsKey("book")) {
            sb.append("book:\n");
            for (Object b : (List<?>) fm.get("book")) {
                sb.append("  - ").append(b).append("\n");
            }
        }
        if (fm.containsKey("order")) {
            sb.append("order: ").append(fm.get("order")).append("\n");
        }
        sb.append("---\n");
        if (body != null && !body.isEmpty()) {
            sb.append(body);
        }
        return sb.toString();
    }

    /** Extract raw front-matter map; returns null if none found */
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

    /** Strip front-matter block and return only the body */
    public static String extractBody(String content) {
        if (content == null) return "";
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("\n---", 3);
        if (end == -1) return content;
        return content.substring(end + 4).stripLeading();
    }

    /** Parse Obsidian-style attachment reference: ![[filename]] */
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