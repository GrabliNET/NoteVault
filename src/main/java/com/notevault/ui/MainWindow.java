package com.notevault.ui;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.notevault.model.*;
import com.notevault.service.VaultService;
import com.notevault.ui.dialogs.NewNoteDialog;
import com.notevault.ui.dialogs.VaultChooserDialog;
import com.notevault.ui.panels.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;

public class MainWindow extends JFrame {

    private SidebarPanel sidebarPanel;
    private NoteListPanel noteListPanel;
    private EditorPanel editorPanel;
    private JSplitPane leftSplit;
    private JSplitPane rightSplit;

    private JLabel statusLabel;

    public MainWindow() {
        super("NoteVault");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        buildUI();
        buildMenuBar();
        registerShortcuts();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitApp(); }
        });

        // Open last vault or show chooser
        SwingUtilities.invokeLater(this::openLastVaultOrChoose);
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // ---- Sidebar (left) ----
        sidebarPanel = new SidebarPanel(this);
        sidebarPanel.setPreferredSize(new Dimension(200, 0));

        // ---- Note list (center-left) ----
        noteListPanel = new NoteListPanel(this);
        noteListPanel.setPreferredSize(new Dimension(260, 0));

        // ---- Editor (right) ----
        editorPanel = new EditorPanel(this);

        // ---- Split panes ----
        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, noteListPanel, editorPanel);
        rightSplit.setDividerSize(1);
        rightSplit.setDividerLocation(260);
        rightSplit.setContinuousLayout(true);
        rightSplit.setBorder(null);

        leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, rightSplit);
        leftSplit.setDividerSize(1);
        leftSplit.setDividerLocation(200);
        leftSplit.setContinuousLayout(true);
        leftSplit.setBorder(null);

        add(leftSplit, BorderLayout.CENTER);

        // ---- Status bar ----
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(2, 12, 2, 12));
        statusBar.setPreferredSize(new Dimension(0, 24));
        statusLabel = new JLabel("No vault open");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusBar.add(statusLabel, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // ---- File ----
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');

        JMenuItem newNote = new JMenuItem("New Note");
        newNote.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newNote.addActionListener(e -> newNote());

        JMenuItem openVault = new JMenuItem("Open Vault…");
        openVault.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openVault.addActionListener(e -> chooseVault());

        JMenuItem exportNote = new JMenuItem("Export Note to PDF…");
        exportNote.addActionListener(e -> editorPanel.exportCurrentNoteToPdf());

        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        quit.addActionListener(e -> exitApp());

        fileMenu.add(newNote);
        fileMenu.addSeparator();
        fileMenu.add(openVault);
        fileMenu.addSeparator();
        fileMenu.add(exportNote);
        fileMenu.addSeparator();
        fileMenu.add(quit);

        // ---- View ----
        JMenu viewMenu = new JMenu("View");

        JMenuItem toggleTheme = new JMenuItem("Toggle Dark/Light Mode");
        toggleTheme.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        toggleTheme.addActionListener(e -> toggleTheme());

        JMenuItem focusSearch = new JMenuItem("Focus Search");
        focusSearch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        focusSearch.addActionListener(e -> noteListPanel.focusSearch());

        viewMenu.add(toggleTheme);
        viewMenu.add(focusSearch);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
    }

    private void registerShortcuts() {
        // Cmd/Ctrl+N → new note
        getRootPane().registerKeyboardAction(
                e -> newNote(),
                KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ---- Public API used by child panels ----

    public void onNoteSelected(Note note) {
        if (note == null) { editorPanel.clearEditor(); return; }
        try {
            VaultService.getInstance().loadNoteContent(note);
            editorPanel.loadNote(note);
        } catch (Exception ex) {
            showError("Failed to load note", ex);
        }
    }

    public void onNoteSaved(Note note) {
        noteListPanel.refreshNote(note);
        setStatus("Saved: " + note.getTitle());
    }

    public void onNoteDeleted(Note note) {
        noteListPanel.removeNote(note);
        editorPanel.clearEditor();
        sidebarPanel.refresh();
        setStatus("Deleted: " + note.getTitle());
    }

    public void onSidebarSelectionChanged(Object selection) {
        noteListPanel.loadSelection(selection);
        editorPanel.clearEditor();
    }

    public void refreshSidebar() {
        sidebarPanel.refresh();
    }

    public void refreshAll() {
        sidebarPanel.refresh();
        noteListPanel.reload();
        editorPanel.refreshPreview();
    }

    // ---- Vault ----

    private void openLastVaultOrChoose() {
        Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);
        String lastVault = prefs.get("lastVault", null);
        if (lastVault != null) {
            java.io.File f = new java.io.File(lastVault);
            if (f.exists() && f.isDirectory()) {
                openVault(Path.of(lastVault));
                return;
            }
        }
        chooseVault();
    }

    public void chooseVault() {
        VaultChooserDialog dialog = new VaultChooserDialog(this);
        dialog.setVisible(true);
        Path chosen = dialog.getChosenPath();
        if (chosen != null) openVault(chosen);
    }

    private void openVault(Path vaultPath) {
        try {
            VaultService.getInstance().openVault(vaultPath);
            Preferences.userNodeForPackage(MainWindow.class).put("lastVault", vaultPath.toString());
            setTitle("NoteVault — " + vaultPath.getFileName());
            setStatus("Vault: " + vaultPath);
            sidebarPanel.refresh();
            noteListPanel.showAllNotes();
        } catch (Exception ex) {
            showError("Failed to open vault", ex);
        }
    }

    // ---- Actions ----

    private void newNote() {
        if (!VaultService.getInstance().isVaultOpen()) { chooseVault(); return; }
        NewNoteDialog dialog = new NewNoteDialog(this);
        dialog.setVisible(true);
        Note created = dialog.getCreatedNote();
        if (created != null) {
            noteListPanel.addNote(created);
            noteListPanel.selectNote(created);
            sidebarPanel.refresh();
        }
    }

    private void toggleTheme() {
        try {
            boolean isDark = UIManager.getLookAndFeel().getClass().getSimpleName().contains("Dark");
            if (isDark) FlatMacLightLaf.setup(); else FlatMacDarkLaf.setup();
            SwingUtilities.updateComponentTreeUI(this);
            Preferences.userNodeForPackage(MainWindow.class).put("theme", isDark ? "light" : "dark");
            editorPanel.refreshPreview();
        } catch (Exception ex) {
            showError("Theme switch failed", ex);
        }
    }

    private void exitApp() {
        editorPanel.saveCurrentNote();
        DatabaseManager.closeAll();
        dispose();
        System.exit(0);
    }

    public void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    public void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this,
                ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }
}

// Small helper to close DB on exit without importing directly
class DatabaseManager {
    static void closeAll() {
        com.notevault.db.DatabaseManager.getInstance().close();
    }
}