package com.notevault.ui.dialogs;

import com.notevault.model.*;
import com.notevault.service.VaultService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class BookAssignDialog extends JDialog {

    private final Note note;
    private boolean changed = false;
    private JComboBox<BookItem> bookCombo;
    private JSpinner orderSpinner;

    public BookAssignDialog(Frame parent, Note note) {
        super(parent, "Assign to Book", true);
        this.note = note;
        setSize(380, 200);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 24, 12, 24));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(6, 0, 6, 12);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        fc.insets = new Insets(6, 0, 6, 0);
        fc.gridwidth = GridBagConstraints.REMAINDER;

        // Book selector
        lc.gridy = 0; fc.gridy = 0;
        panel.add(new JLabel("Book:"), lc);
        bookCombo = new JComboBox<>();
        bookCombo.addItem(new BookItem(null, "(None)"));
        Long currentBookId = note.getBookId();
        try {
            List<Book> books = VaultService.getInstance().getAllBooks();
            for (Book b : books) {
                bookCombo.addItem(new BookItem(b, b.getName()));
                if (b.getId() == (currentBookId != null ? currentBookId : -1)) {
                    bookCombo.setSelectedIndex(bookCombo.getItemCount() - 1);
                }
            }
        } catch (Exception ignored) {}
        panel.add(bookCombo, fc);

        // Order
        lc.gridy = 1; fc.gridy = 1;
        panel.add(new JLabel("Position:"), lc);
        orderSpinner = new JSpinner(new SpinnerNumberModel(
                note.getBookOrder() != null ? note.getBookOrder() : 0, 0, 9999, 1));
        panel.add(orderSpinner, fc);

        JButton applyBtn = new JButton("Apply");
        applyBtn.putClientProperty("JButton.buttonType", "default");
        applyBtn.addActionListener(e -> {
            BookItem bi = (BookItem) bookCombo.getSelectedItem();
            if (bi != null && bi.book != null) {
                note.setBookId(bi.book.getId());
                note.getMetaExtra().put("bookName", bi.book.getName());
            } else {
                note.setBookId(null);
                note.getMetaExtra().remove("bookName");
            }
            note.setBookOrder((Integer) orderSpinner.getValue());
            changed = true;
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancelBtn);
        buttons.add(applyBtn);

        getRootPane().setDefaultButton(applyBtn);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    public boolean isChanged() { return changed; }

    private record BookItem(Book book, String label) {
        @Override public String toString() { return label; }
    }
}