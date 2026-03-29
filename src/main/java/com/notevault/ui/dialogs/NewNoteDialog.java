package com.notevault.ui.dialogs;

import com.notevault.model.*;
import com.notevault.service.VaultService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NewNoteDialog extends JDialog {

    private Note createdNote;
    private JTextField titleField;
    private JTextField tagsField;
    private JComboBox<BookItem> bookCombo;

    public NewNoteDialog(Frame parent) {
        super(parent, "New Note", true);
        setSize(420, 260);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 24, 16, 24));
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 10);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        fc.insets = new Insets(4, 0, 4, 0);
        fc.gridwidth = GridBagConstraints.REMAINDER;

        lc.gridy = 0; fc.gridy = 0;
        panel.add(new JLabel("Title:"), lc);
        titleField = new JTextField(22);
        panel.add(titleField, fc);

        lc.gridy = 1; fc.gridy = 1;
        panel.add(new JLabel("Tags:"), lc);
        tagsField = new JTextField();
        tagsField.putClientProperty("JTextField.placeholderText", "java, guide, tutorial  (comma-separated)");
        panel.add(tagsField, fc);

        lc.gridy = 2; fc.gridy = 2;
        panel.add(new JLabel("Book:"), lc);
        bookCombo = new JComboBox<>();
        bookCombo.addItem(new BookItem(null, "(None)"));
        if (VaultService.getInstance().isVaultOpen()) {
            try {
                for (Book b : VaultService.getInstance().getAllBooks())
                    bookCombo.addItem(new BookItem(b, b.getName()));
            } catch (Exception ignored) {}
        }
        panel.add(bookCombo, fc);

        // ---- Buttons ----
        JButton createBtn = new JButton("Create");
        createBtn.putClientProperty("JButton.buttonType", "default");
        createBtn.addActionListener(e -> doCreate());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelBtn);
        buttons.add(createBtn);

        getRootPane().setDefaultButton(createBtn);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(0, 0, 12, 8));

        SwingUtilities.invokeLater(() -> titleField.requestFocusInWindow());
    }

    private void doCreate() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title cannot be empty.");
            return;
        }
        List<Tag> tags = parseTags(tagsField.getText());
        try {
            createdNote = VaultService.getInstance().createNote(title, "", tags);
            BookItem bi = (BookItem) bookCombo.getSelectedItem();
            if (bi != null && bi.book != null) {
                createdNote.setBookId(bi.book.getId());
                VaultService.getInstance().updateNoteMeta(createdNote);
            }
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to create note: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<Tag> parseTags(String text) {
        List<Tag> tags = new ArrayList<>();
        if (text == null || text.isBlank()) return tags;
        for (String t : text.split("[,;\\s]+")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) tags.add(new Tag(trimmed));
        }
        return tags;
    }

    public Note getCreatedNote() { return createdNote; }

    private record BookItem(Book book, String label) {
        @Override public String toString() { return label; }
    }
}