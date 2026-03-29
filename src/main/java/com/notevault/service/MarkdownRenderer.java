package com.notevault.service;

import com.notevault.util.VaultLayout;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.nio.file.Path;
import java.util.List;

public class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            TaskListItemsExtension.create()
    );

    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(false)
            .build();

    /**
     * Render markdown content to a full HTML page for display in JEditorPane.
     * Handles ![[filename]] attachment syntax by converting to <img> or <a> tags.
     */
    public static String render(String markdownContent, VaultLayout layout, Path noteFilePath) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return wrapHtml("<p style='color:gray;font-style:italic'>Empty note</p>", false);
        }

        // Strip YAML front-matter before rendering
        String body = stripFrontMatter(markdownContent);

        // Pre-process Obsidian-style ![[file]] references
        body = processAttachmentRefs(body, layout, noteFilePath);

        Node document = PARSER.parse(body);
        String html = RENDERER.render(document);
        return wrapHtml(html, isDarkTheme());
    }

    /** Render plain markdown string (no attachment processing) */
    public static String renderSimple(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String body = stripFrontMatter(markdown);
        Node doc = PARSER.parse(body);
        return RENDERER.render(doc);
    }

    private static String processAttachmentRefs(String body, VaultLayout layout, Path noteFilePath) {
        if (layout == null) return body;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            int start = body.indexOf("![[", i);
            if (start == -1) {
                sb.append(body.substring(i));
                break;
            }
            sb.append(body, i, start);
            int end = body.indexOf("]]", start + 3);
            if (end == -1) {
                sb.append(body.substring(start));
                break;
            }
            String filename = body.substring(start + 3, end).trim();
            sb.append(resolveAttachmentTag(filename, layout, noteFilePath));
            i = end + 2;
        }
        return sb.toString();
    }

    private static String resolveAttachmentTag(String filename, VaultLayout layout, Path noteFilePath) {
        // Try to locate the file in _attachments
        Path attDir = layout.getVaultRoot().resolve(VaultLayout.DIR_ATTACHMENTS);
        // Search subdirs
        Path found = null;
        try (var stream = java.nio.file.Files.walk(attDir, 2)) {
            found = stream.filter(p -> p.getFileName().toString().equals(filename)).findFirst().orElse(null);
        } catch (Exception ignored) {}

        if (found != null) {
            String uri = found.toUri().toString();
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
                return "<img src=\"" + uri + "\" alt=\"" + filename + "\" style=\"max-width:100%;border-radius:6px;\">";
            } else {
                return "<a href=\"" + uri + "\">" + filename + "</a>";
            }
        }
        // Not found — show as code
        return "<code>![[" + filename + "]]</code>";
    }

    private static String stripFrontMatter(String content) {
        if (content == null) return "";
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("\n---", 3);
        if (end == -1) return content;
        return content.substring(end + 4).stripLeading();
    }

    private static boolean isDarkTheme() {
        String laf = javax.swing.UIManager.getLookAndFeel().getClass().getSimpleName();
        return laf.contains("Dark");
    }

    public static String wrapHtml(String body, boolean dark) {
        String bg = dark ? "#1e1e1e" : "#ffffff";
        String fg = dark ? "#d4d4d4" : "#1a1a1a";
        String linkColor = dark ? "#6ab0f5" : "#0066cc";
        String codeBg = dark ? "#2d2d2d" : "#f4f4f4";
        String borderColor = dark ? "#3a3a3a" : "#e0e0e0";
        String blockquoteBorder = dark ? "#555" : "#ccc";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                  body {
                    font-family: -apple-system, 'SF Pro Text', 'Helvetica Neue', Arial, sans-serif;
                    font-size: 14px;
                    line-height: 1.7;
                    color: %s;
                    background: %s;
                    padding: 24px 32px;
                    margin: 0;
                    max-width: 800px;
                  }
                  h1 { font-size: 1.9em; font-weight: 700; margin-top: 0.4em; border-bottom: 1px solid %s; padding-bottom: 0.3em; }
                  h2 { font-size: 1.5em; font-weight: 600; margin-top: 1.4em; }
                  h3 { font-size: 1.2em; font-weight: 600; margin-top: 1.2em; }
                  a { color: %s; text-decoration: none; }
                  a:hover { text-decoration: underline; }
                  code {
                    font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
                    font-size: 0.88em;
                    background: %s;
                    padding: 2px 6px;
                    border-radius: 4px;
                  }
                  pre {
                    background: %s;
                    border-radius: 8px;
                    padding: 16px;
                    overflow-x: auto;
                    border: 1px solid %s;
                  }
                  pre code { background: none; padding: 0; }
                  blockquote {
                    border-left: 3px solid %s;
                    margin: 0;
                    padding: 0 0 0 16px;
                    color: gray;
                  }
                  table { border-collapse: collapse; width: 100%%; margin: 12px 0; }
                  th, td { border: 1px solid %s; padding: 8px 12px; text-align: left; }
                  th { background: %s; font-weight: 600; }
                  hr { border: none; border-top: 1px solid %s; margin: 24px 0; }
                  img { max-width: 100%%; border-radius: 6px; }
                  ul, ol { padding-left: 1.6em; }
                  li { margin: 4px 0; }
                  input[type=checkbox] { margin-right: 6px; }
                  p { margin: 0.6em 0; }
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(fg, bg, borderColor, linkColor, codeBg, codeBg, borderColor,
                blockquoteBorder, borderColor, codeBg, borderColor, body);
    }
}