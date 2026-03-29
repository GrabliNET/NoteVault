package com.notevault.ui.panels;

import com.notevault.model.*;
import com.notevault.service.VaultService;
import com.notevault.ui.MainWindow;
import com.notevault.ui.dialogs.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class SidebarPanel extends JPanel {

    private final MainWindow mainWindow;
    private JTree tree;
    private DefaultMutableTreeNode rootNode;
    private DefaultMutableTreeNode allNotesNode;
    private DefaultMutableTreeNode booksNode;
    private DefaultMutableTreeNode tagsNode;
    private DefaultTreeModel treeModel;

    private JButton newNoteBtn;

    public SidebarPanel(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        // ---- Header ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12, 12, 8, 8));
        JLabel title = new JLabel("NoteVault");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        newNoteBtn = new JButton("+");
        newNoteBtn.setToolTipText("New Note (⌘N)");
        newNoteBtn.setFont(newNoteBtn.getFont().deriveFont(16f));
        newNoteBtn.setFocusable(false);
        newNoteBtn.setBorderPainted(false);
        newNoteBtn.setContentAreaFilled(false);
        newNoteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newNoteBtn.addActionListener(e -> mainWindow.chooseVault());
        header.add(title, BorderLayout.WEST);
        header.add(newNoteBtn, BorderLayout.EAST);

        // ---- Tree ----
        rootNode = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(28);
        tree.setBorder(new EmptyBorder(4, 4, 4, 4));
        tree.setCellRenderer(new SidebarCellRenderer());
        tree.setToggleClickCount(1);

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null) return;
            Object userObj = node.getUserObject();
            if (node == allNotesNode) {
                mainWindow.onSidebarSelectionChanged("ALL");
            } else if (userObj instanceof Tag) {
                mainWindow.onSidebarSelectionChanged(userObj);
            } else if (userObj instanceof Book) {
                mainWindow.onSidebarSelectionChanged(userObj);
            }
        });

        // Right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = tree.getRowForLocation(e.getX(), e.getY());
                    if (row >= 0) tree.setSelectionRow(row);
                    showContextMenu(e);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(8);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        rootNode.removeAllChildren();

        // All Notes
        allNotesNode = new DefaultMutableTreeNode("All Notes");
        rootNode.add(allNotesNode);

        // Books
        booksNode = new DefaultMutableTreeNode("Books");
        if (VaultService.getInstance().isVaultOpen()) {
            try {
                List<Book> books = VaultService.getInstance().getAllBooks();
                for (Book b : books) {
                    booksNode.add(new DefaultMutableTreeNode(b));
                }
            } catch (Exception ignored) {}
        }
        rootNode.add(booksNode);

        // Tags
        tagsNode = new DefaultMutableTreeNode("Tags");
        if (VaultService.getInstance().isVaultOpen()) {
            try {
                List<Tag> tags = VaultService.getInstance().getAllTags();
                for (Tag t : tags) {
                    tagsNode.add(new DefaultMutableTreeNode(t));
                }
            } catch (Exception ignored) {}
        }
        rootNode.add(tagsNode);

        treeModel.reload();

        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);

        // Re-select All Notes by default
        tree.setSelectionRow(0);
    }

    private void showContextMenu(MouseEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) return;
        Object userObj = node.getUserObject();
        JPopupMenu menu = new JPopupMenu();

        if (node == booksNode) {
            JMenuItem newBook = new JMenuItem("New Book…");
            newBook.addActionListener(ev -> createBook());
            menu.add(newBook);
        } else if (userObj instanceof Book book) {
            JMenuItem rename = new JMenuItem("Rename…");
            rename.addActionListener(ev -> renameBook(book));
            JMenuItem delete = new JMenuItem("Delete Book");
            delete.addActionListener(ev -> deleteBook(book));
            JMenuItem exportPdf = new JMenuItem("Export to PDF…");
            exportPdf.addActionListener(ev -> exportBook(book));
            menu.add(rename);
            menu.add(exportPdf);
            menu.addSeparator();
            menu.add(delete);
        } else if (node == tagsNode) {
            // nothing
        } else if (userObj instanceof Tag tag) {
            JMenuItem rename = new JMenuItem("Rename Tag…");
            rename.addActionListener(ev -> renameTag(tag));
            JMenuItem delete = new JMenuItem("Delete Tag");
            delete.addActionListener(ev -> deleteTag(tag));
            menu.add(rename);
            menu.addSeparator();
            menu.add(delete);
        }

        if (menu.getComponentCount() > 0)
            menu.show(tree, e.getX(), e.getY());
    }

    private void createBook() {
        String name = JOptionPane.showInputDialog(mainWindow, "Book name:", "New Book", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        try {
            VaultService.getInstance().createBook(name.trim(), "");
            refresh();
        } catch (Exception ex) { mainWindow.showError("Create book failed", ex); }
    }

    private void renameBook(Book book) {
        String name = (String) JOptionPane.showInputDialog(mainWindow, "New name:", "Rename Book",
                JOptionPane.PLAIN_MESSAGE, null, null, book.getName());
        if (name == null || name.isBlank()) return;
        try {
            book.setName(name.trim());
            VaultService.getInstance().updateBook(book);
            refresh();
        } catch (Exception ex) { mainWindow.showError("Rename failed", ex); }
    }

    private void deleteBook(Book book) {
        int confirm = JOptionPane.showConfirmDialog(mainWindow,
                "Delete book \"" + book.getName() + "\"?\n(Notes are kept, only the book is removed.)",
                "Delete Book", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            VaultService.getInstance().deleteBook(book);
            refresh();
            mainWindow.onSidebarSelectionChanged("ALL");
        } catch (Exception ex) { mainWindow.showError("Delete failed", ex); }
    }

    private void exportBook(Book book) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Book to PDF");
        fc.setSelectedFile(new java.io.File(book.getName() + ".pdf"));
        if (fc.showSaveDialog(mainWindow) != JFileChooser.APPROVE_OPTION) return;
        try {
            new com.notevault.service.PdfExporter().exportBook(book, fc.getSelectedFile().toPath());
            mainWindow.setStatus("Exported: " + fc.getSelectedFile().getName());
        } catch (Exception ex) { mainWindow.showError("Export failed", ex); }
    }

    private void renameTag(Tag tag) {
        String name = (String) JOptionPane.showInputDialog(mainWindow, "New tag name:", "Rename Tag",
                JOptionPane.PLAIN_MESSAGE, null, null, tag.getName());
        if (name == null || name.isBlank()) return;
        try {
            VaultService.getInstance().renameTag(tag, name.trim());
            refresh();
        } catch (Exception ex) { mainWindow.showError("Rename failed", ex); }
    }

    private void deleteTag(Tag tag) {
        int confirm = JOptionPane.showConfirmDialog(mainWindow,
                "Delete tag \"" + tag.getName() + "\"?\nIt will be removed from all notes.",
                "Delete Tag", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            VaultService.getInstance().deleteTag(tag);
            refresh();
        } catch (Exception ex) { mainWindow.showError("Delete failed", ex); }
    }

    // ---- Custom cell renderer ----

    private static class SidebarCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setBorder(new EmptyBorder(2, 0, 2, 0));
            setFont(getFont().deriveFont(13f));

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof Tag t) {
                setText("  # " + t.getName() + (t.getNoteCount() > 0 ? "  " + t.getNoteCount() : ""));
                setIcon(null);
            } else if (userObj instanceof Book b) {
                setText("  📖 " + b.getName());
                setIcon(null);
            } else if ("All Notes".equals(userObj)) {
                setText("  📝 All Notes");
                setIcon(null);
                setFont(getFont().deriveFont(Font.PLAIN, 13f));
            } else if ("Books".equals(userObj) || "Tags".equals(userObj)) {
                setText("  " + userObj);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setIcon(null);
            } else {
                setIcon(null);
            }
            return this;
        }
    }
}