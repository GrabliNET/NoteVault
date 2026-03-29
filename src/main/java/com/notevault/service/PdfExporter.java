package com.notevault.service;

import com.notevault.model.Book;
import com.notevault.model.Note;
import com.notevault.util.FrontMatterParser;
import com.notevault.util.VaultLayout;
import com.lowagie.text.DocumentException;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.nio.file.Path;

public class PdfExporter {

    private final VaultService vaultService = VaultService.getInstance();

    /** Export a single note to PDF */
    public void exportNote(Note note, Path outputPath) throws Exception {
        String markdown = note.getContent();
        String body = FrontMatterParser.extractBody(markdown);
        String htmlBody = MarkdownRenderer.renderSimple(body);

        // Build styled HTML header with tags
        StringBuilder headerHtml = new StringBuilder();
        headerHtml.append("<h1>").append(escapeHtml(note.getTitle())).append("</h1>\n");
        if (note.getTags() != null && !note.getTags().isEmpty()) {
            headerHtml.append("<p style='color:#888;font-size:0.85em;margin-top:-8px'>");
            note.getTags().forEach(t -> headerHtml.append("🏷 ").append(escapeHtml(t.getName())).append("&nbsp;&nbsp;"));
            headerHtml.append("</p>\n");
        }
        headerHtml.append("<hr style='border:none;border-top:1px solid #ddd;margin:16px 0'>\n");

        String fullHtml = buildPdfHtml(headerHtml + htmlBody);
        renderPdf(fullHtml, outputPath);
    }

    /** Export all notes in a book to a single PDF */
    public void exportBook(Book book, Path outputPath) throws Exception {
        String markdown = vaultService.getBookContent(book);
        String htmlBody = MarkdownRenderer.renderSimple(markdown);
        String fullHtml = buildPdfHtml(htmlBody);
        renderPdf(fullHtml, outputPath);
    }

    private void renderPdf(String html, Path outputPath) throws Exception {
        try (OutputStream os = new FileOutputStream(outputPath.toFile())) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(os);
        }
    }

    private String buildPdfHtml(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                  <style>
                    @page { margin: 2cm 2.5cm; }
                    body {
                      font-family: Georgia, 'Times New Roman', serif;
                      font-size: 12pt;
                      line-height: 1.7;
                      color: #1a1a1a;
                    }
                    h1 { font-size: 1.8em; font-weight: bold; margin-bottom: 0.2em; }
                    h2 { font-size: 1.4em; font-weight: bold; margin-top: 1.4em; }
                    h3 { font-size: 1.1em; font-weight: bold; }
                    code {
                      font-family: 'Courier New', monospace;
                      font-size: 0.88em;
                      background: #f4f4f4;
                      padding: 1px 4px;
                    }
                    pre {
                      background: #f4f4f4;
                      padding: 12px;
                      border-left: 3px solid #aaa;
                      font-family: 'Courier New', monospace;
                      font-size: 0.85em;
                    }
                    blockquote {
                      border-left: 3px solid #ccc;
                      padding-left: 12px;
                      color: #555;
                      margin: 0;
                    }
                    table { border-collapse: collapse; width: 100%%; }
                    th, td { border: 1px solid #ccc; padding: 6px 10px; }
                    th { background: #f0f0f0; font-weight: bold; }
                    hr { border: none; border-top: 1px solid #ddd; }
                    img { max-width: 100%%; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(body);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}