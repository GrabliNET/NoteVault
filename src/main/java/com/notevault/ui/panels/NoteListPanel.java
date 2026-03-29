package com.notevault.ui.panels;

import com.notevault.model.*;
import com.notevault.service.VaultService;
import com.notevault.ui.MainWindow;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


public class NoteListPanel extends JPanel {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final MainWindow mainWindow;
    private DefaultListModel<Note> listModel;
    private JList<Note> noteList;
    private JTextField searchField;
    private JLabel headerLabel;
    private Object currentSelection = "ALL";
    private Timer searchDebounce;

    public NoteListPanel(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        // ---- Header ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 12, 6, 12));
        headerLabel = new JLabel("All Notes");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 13f));
        header.add(headerLabel, BorderLayout.NORTH);

        // Search field
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search…");
        searchField.putClientProperty("JTextField.leadingIcon", searchIcon());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        searchDebounce = new Timer(250, e -> performSearch());
        searchDebounce.setRepeats(false);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        searchPanel.add(searchField);
        header.add(searchPanel, BorderLayout.CENTER);

        // ---- List ----
        listModel = new DefaultListModel<>();
        noteList = new JList<>(listModel);
        noteList.setCellRenderer(new NoteListCellRenderer());
        noteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        noteList.setFixedCellHeight(-1);
        noteList.setBorder(new EmptyBorder(4, 0, 4, 0));

        noteList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mainWindow.onNoteSelected(noteList.getSelectedValue());
            }
        });

        // Right-click context menu
        noteList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int idx = noteList.locationToIndex(e.getPoint());
                    if (idx >= 0) noteList.setSelectedIndex(idx);
                    showContextMenu(e);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(noteList);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    // ---- Public API ----

    public void showAllNotes() {
        currentSelection = "ALL";
        headerLabel.setText("All Notes");
        loadFromService(() -> VaultService.getInstance().getAllNotes());
    }

    public void loadSelection(Object selection) {
        currentSelection = selection;
        searchField.setText("");
        if ("ALL".equals(selection)) {
            showAllNotes();
        } else if (selection instanceof Tag tag) {
            headerLabel.setText("# " + tag.getName());
            loadFromService(() -> VaultService.getInstance().getNotesByTag(tag));
        } else if (selection instanceof Book book) {
            headerLabel.setText("📖 " + book.getName());
            loadFromService(() -> VaultService.getInstance().getNotesByBook(book));
        }
    }

    public void reload() {
        loadSelection(currentSelection);
    }

    public void addNote(Note note) {
        listModel.add(0, note);
    }

    public void refreshNote(Note note) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId() == note.getId()) {
                listModel.set(i, note);
                return;
            }
        }
    }

    public void removeNote(Note note) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId() == note.getId()) {
                listModel.remove(i);
                return;
            }
        }
    }

    public void selectNote(Note note) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId() == note.getId()) {
                noteList.setSelectedIndex(i);
                noteList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    public void focusSearch() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    // ---- Internal ----

    private void loadFromService(NoteSupplier supplier) {
        listModel.clear();
        if (!VaultService.getInstance().isVaultOpen()) return;
        SwingWorker<List<Note>, Void> worker = new SwingWorker<>() {
            @Override protected List<Note> doInBackground() throws Exception { return supplier.get(); }
            @Override protected void done() {
                try {
                    List<Note> notes = get();
                    listModel.clear();
                    notes.forEach(listModel::addElement);
                } catch (Exception ex) { mainWindow.showError("Load notes failed", ex); }
            }
        };
        worker.execute();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isBlank()) { reload(); return; }
        loadFromService(() -> VaultService.getInstance().searchNotes(query));
    }

    private void showContextMenu(MouseEvent e) {
        Note note = noteList.getSelectedValue();
        if (note == null) return;
        JPopupMenu menu = new JPopupMenu();

        JMenuItem rename = new JMenuItem("Rename…");
        rename.addActionListener(ev -> renameNote(note));

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(ev -> deleteNote(note));

        menu.add(rename);
        menu.addSeparator();
        menu.add(delete);
        menu.show(noteList, e.getX(), e.getY());
    }

    private void renameNote(Note note) {
        String newTitle = (String) JOptionPane.showInputDialog(mainWindow,
                "New title:", "Rename Note", JOptionPane.PLAIN_MESSAGE, null, null, note.getTitle());
        if (newTitle == null || newTitle.isBlank()) return;
        try {
            VaultService.getInstance().renameNote(note, newTitle.trim());
            refreshNote(note);
            mainWindow.setStatus("Renamed: " + newTitle);
        } catch (Exception ex) { mainWindow.showError("Rename failed", ex); }
    }

    private void deleteNote(Note note) {
        int confirm = JOptionPane.showConfirmDialog(mainWindow,
                "Delete \"" + note.getTitle() + "\"?\nThis cannot be undone.",
                "Delete Note", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            VaultService.getInstance().deleteNote(note);
            mainWindow.onNoteDeleted(note);
        } catch (Exception ex) { mainWindow.showError("Delete failed", ex); }
    }

    private Icon searchIcon() {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 1, y + 1, 9, 9);
                g2.drawLine(x + 8, y + 8, x + 12, y + 12);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 14; }
            @Override public int getIconHeight() { return 14; }
        };
    }

    // ---- Cell renderer ----

    private static class NoteListCellRenderer extends JPanel implements ListCellRenderer<Note> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel dateLabel = new JLabel();
        private final JLabel tagsLabel = new JLabel();
        private final JPanel textPanel = new JPanel(new BorderLayout(0, 2));

        NoteListCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 13f));
            dateLabel.setFont(dateLabel.getFont().deriveFont(10f));
            tagsLabel.setFont(tagsLabel.getFont().deriveFont(10f));
            JPanel meta = new JPanel(new BorderLayout());
            meta.setOpaque(false);
            meta.add(dateLabel, BorderLayout.WEST);
            meta.add(tagsLabel, BorderLayout.EAST);
            textPanel.setOpaque(false);
            textPanel.add(titleLabel, BorderLayout.NORTH);
            textPanel.add(meta, BorderLayout.SOUTH);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Note> list, Note note,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            titleLabel.setText(note.getTitle() != null ? note.getTitle() : "(Untitled)");
            dateLabel.setText(note.getUpdatedAt() != null ? note.getUpdatedAt().format(DATE_FMT) : "");
            dateLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            // Tags as pills text
            if (note.getTags() != null && !note.getTags().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                note.getTags().stream().limit(3).forEach(t -> sb.append(" #").append(t.getName()));
                tagsLabel.setText(sb.toString().trim());
                tagsLabel.setForeground(UIManager.getColor("Component.accentColor") != null
                        ? UIManager.getColor("Component.accentColor")
                        : new Color(0x5588CC));
            } else {
                tagsLabel.setText("");
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                titleLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(index % 2 == 0
                        ? list.getBackground()
                        : UIManager.getColor("Table.alternateRowColor") != null
                        ? UIManager.getColor("Table.alternateRowColor")
                        : list.getBackground());
                titleLabel.setForeground(list.getForeground());
            }
            setOpaque(true);
            return this;
        }
    }

    @FunctionalInterface
    interface NoteSupplier {
        List<Note> get() throws Exception;
    }
}