package com.notevault.ui.panels;

import com.notevault.model.*;
import com.notevault.service.*;
import com.notevault.ui.MainWindow;
import com.notevault.ui.components.TagChipPanel;
import com.notevault.ui.dialogs.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class EditorPanel extends JPanel {

    private final MainWindow mainWindow;
    private Note currentNote;

    // ---- Toolbar ----
    private JPanel toolbar;
    private JLabel noteTitleLabel;
    private JButton editPreviewToggle;
    private JButton addTagBtn;
    private JButton attachBtn;
    private JButton bookBtn;
    private JButton exportBtn;
    private TagChipPanel tagChipPanel;

    // ---- Split: editor left, preview right ----
    private JSplitPane editorSplit;
    private JTextArea editorArea;
    private JEditorPane previewPane;

    private boolean previewVisible = true;
    private Timer autoSaveTimer;
    private volatile boolean dirty = false;

    public EditorPanel(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        // ---- Toolbar ----
        toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                new EmptyBorder(6, 12, 6, 12)));

        noteTitleLabel = new JLabel("Select a note");
        noteTitleLabel.setFont(noteTitleLabel.getFont().deriveFont(Font.BOLD, 14f));

        editPreviewToggle = toolbarBtn("⇄", "Toggle preview (split view)");
        editPreviewToggle.addActionListener(e -> togglePreview());

        addTagBtn = toolbarBtn("🏷", "Manage tags");
        addTagBtn.addActionListener(e -> manageTags());

        attachBtn = toolbarBtn("📎", "Attach file");
        attachBtn.addActionListener(e -> attachFile());

        bookBtn = toolbarBtn("📖", "Assign to book");
        bookBtn.addActionListener(e -> assignBook());

        exportBtn = toolbarBtn("⬇", "Export to PDF");
        exportBtn.addActionListener(e -> exportCurrentNoteToPdf());

        toolbar.add(noteTitleLabel);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(addTagBtn);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(bookBtn);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(attachBtn);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(editPreviewToggle);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(exportBtn);

        // ---- Tag chips row ----
        tagChipPanel = new TagChipPanel(this::onTagRemoved);
        tagChipPanel.setBorder(new EmptyBorder(4, 12, 0, 12));

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(toolbar, BorderLayout.NORTH);
        topArea.add(tagChipPanel, BorderLayout.SOUTH);

        // ---- Editor text area ----
        editorArea = new JTextArea();
        editorArea.setFont(new Font("SF Mono", Font.PLAIN, 13));
        editorArea.setLineWrap(true);
        editorArea.setWrapStyleWord(true);
        editorArea.setMargin(new Insets(16, 20, 16, 20));
        editorArea.setTabSize(4);

        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onEdit(); }
            public void removeUpdate(DocumentEvent e) { onEdit(); }
            public void changedUpdate(DocumentEvent e) {}
        });

        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorScroll.setBorder(null);
        editorScroll.getVerticalScrollBar().setUnitIncrement(12);

        // ---- Preview pane ----
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setEditorKit(new HTMLEditorKit());
        // Handle link clicks in preview (open externally)
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {}
            }
        });

        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                UIManager.getColor("Separator.foreground")));
        previewScroll.getVerticalScrollBar().setUnitIncrement(12);

        // ---- Split ----
        editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, previewScroll);
        editorSplit.setResizeWeight(0.5);
        editorSplit.setDividerSize(1);
        editorSplit.setContinuousLayout(true);
        editorSplit.setBorder(null);

        // ---- Placeholder ----
        JPanel placeholder = new JPanel(new GridBagLayout());
        JLabel hint = new JLabel("Select a note or press ⌘N to create one");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setFont(hint.getFont().deriveFont(14f));
        placeholder.add(hint);

        add(topArea, BorderLayout.NORTH);
        add(editorSplit, BorderLayout.CENTER);

        setEnabled(false);
        updateToolbarEnabled(false);
    }

    // ---- Public API ----

    public void loadNote(Note note) {
        saveCurrentNote(); // auto-save previous
        currentNote = note;
        noteTitleLabel.setText(note.getTitle());
        String content = note.getContent() != null ? note.getContent() : "";

        editorArea.getDocument().removeDocumentListener(null); // keep listener, just block dirty
        editorArea.setText(content);
        editorArea.setCaretPosition(0);
        dirty = false;

        tagChipPanel.setTags(note.getTags());
        updatePreview();
        updateToolbarEnabled(true);
        editorArea.requestFocusInWindow();
    }

    public void clearEditor() {
        saveCurrentNote();
        currentNote = null;
        noteTitleLabel.setText("Select a note");
        editorArea.setText("");
        previewPane.setText("");
        tagChipPanel.setTags(null);
        updateToolbarEnabled(false);
        dirty = false;
    }

    public void refreshPreview() {
        if (currentNote != null) updatePreview();
    }

    public void saveCurrentNote() {
        if (currentNote == null || !dirty) return;
        try {
            String content = editorArea.getText();
            VaultService.getInstance().saveNote(currentNote, content);
            dirty = false;
            mainWindow.onNoteSaved(currentNote);
        } catch (Exception ex) {
            mainWindow.showError("Auto-save failed", ex);
        }
    }

    public void exportCurrentNoteToPdf() {
        if (currentNote == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Note to PDF");
        fc.setSelectedFile(new java.io.File(currentNote.getTitle() + ".pdf"));
        if (fc.showSaveDialog(mainWindow) != JFileChooser.APPROVE_OPTION) return;
        saveCurrentNote();
        try {
            new PdfExporter().exportNote(currentNote, fc.getSelectedFile().toPath());
            mainWindow.setStatus("Exported: " + fc.getSelectedFile().getName());
        } catch (Exception ex) { mainWindow.showError("Export failed", ex); }
    }

    // ---- Internal ----

    private void onEdit() {
        dirty = true;
        scheduleAutoSave();
        updatePreviewDelayed();
    }

    private javax.swing.Timer previewDebounce;
    private void updatePreviewDelayed() {
        if (previewDebounce == null) {
            previewDebounce = new javax.swing.Timer(400, e -> updatePreview());
            previewDebounce.setRepeats(false);
        }
        previewDebounce.restart();
    }

    private javax.swing.Timer autoSaveDebounce;
    private void scheduleAutoSave() {
        if (autoSaveDebounce == null) {
            autoSaveDebounce = new javax.swing.Timer(2000, e -> saveCurrentNote());
            autoSaveDebounce.setRepeats(false);
        }
        autoSaveDebounce.restart();
    }

    private void updatePreview() {
        if (!previewVisible || currentNote == null) return;
        String content = editorArea.getText();
        VaultService vs = VaultService.getInstance();
        Path notePath = vs.isVaultOpen() ? vs.getLayout().resolve(currentNote.getFilePath()) : null;
        String html = MarkdownRenderer.render(content, vs.isVaultOpen() ? vs.getLayout() : null, notePath);
        // Preserve scroll position
        int scrollPos = ((JScrollPane) previewPane.getParent().getParent()).getVerticalScrollBar().getValue();
        previewPane.setText(html);
        SwingUtilities.invokeLater(() ->
                ((JScrollPane) previewPane.getParent().getParent()).getVerticalScrollBar().setValue(scrollPos));
    }

    private void togglePreview() {
        previewVisible = !previewVisible;
        editorSplit.getRightComponent().setVisible(previewVisible);
        editPreviewToggle.setText(previewVisible ? "⇄" : "◻");
        if (previewVisible) updatePreview();
    }

    private void manageTags() {
        if (currentNote == null) return;
        saveCurrentNote();
        TagManagerDialog dialog = new TagManagerDialog(mainWindow, currentNote);
        dialog.setVisible(true);
        if (dialog.isChanged()) {
            currentNote.setTags(dialog.getSelectedTags());
            tagChipPanel.setTags(currentNote.getTags());
            try {
                VaultService.getInstance().updateNoteMeta(currentNote);
                editorArea.setText(currentNote.getContent());
                dirty = false;
                mainWindow.refreshSidebar();
            } catch (Exception ex) { mainWindow.showError("Tag update failed", ex); }
        }
    }

    private void onTagRemoved(Tag tag) {
        if (currentNote == null) return;
        currentNote.getTags().remove(tag);
        try {
            saveCurrentNote();
            VaultService.getInstance().updateNoteMeta(currentNote);
            editorArea.setText(currentNote.getContent());
            dirty = false;
            mainWindow.refreshSidebar();
        } catch (Exception ex) { mainWindow.showError("Tag removal failed", ex); }
    }

    private void attachFile() {
        if (currentNote == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Attach File");
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(mainWindow) != JFileChooser.APPROVE_OPTION) return;
        for (java.io.File f : fc.getSelectedFiles()) {
            try {
                Attachment att = VaultService.getInstance().addAttachment(currentNote, f.toPath());
                // Insert wiki-link syntax at cursor
                String ref = "\n![[" + att.getFileName() + "]]\n";
                int pos = editorArea.getCaretPosition();
                editorArea.insert(ref, pos);
                mainWindow.setStatus("Attached: " + att.getFileName());
            } catch (Exception ex) { mainWindow.showError("Attach failed", ex); }
        }
    }

    private void assignBook() {
        if (currentNote == null) return;
        saveCurrentNote();
        BookAssignDialog dialog = new BookAssignDialog(mainWindow, currentNote);
        dialog.setVisible(true);
        if (dialog.isChanged()) {
            try {
                VaultService.getInstance().updateNoteMeta(currentNote);
                editorArea.setText(currentNote.getContent());
                dirty = false;
                mainWindow.refreshSidebar();
            } catch (Exception ex) { mainWindow.showError("Book assignment failed", ex); }
        }
    }

    private void updateToolbarEnabled(boolean enabled) {
        addTagBtn.setEnabled(enabled);
        attachBtn.setEnabled(enabled);
        bookBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        editPreviewToggle.setEnabled(enabled);
        editorArea.setEnabled(enabled);
    }

    private JButton toolbarBtn(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(btn.getFont().deriveFont(15f));
        return btn;
    }
}