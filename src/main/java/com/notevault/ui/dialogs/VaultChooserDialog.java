package com.notevault.ui.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public class VaultChooserDialog extends JDialog {

    private Path chosenPath;

    public VaultChooserDialog(Frame parent) {
        super(parent, "Open Vault", true);
        setSize(460, 220);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBorder(new EmptyBorder(24, 28, 20, 28));

        JLabel desc = new JLabel("<html><b>Choose or create a vault folder</b><br>" +
                "<span style='color:gray'>A vault is a directory where your notes and database are stored.</span></html>");
        panel.add(desc, BorderLayout.NORTH);

        JTextField pathField = new JTextField();
        pathField.setEditable(false);
        pathField.putClientProperty("JTextField.placeholderText", "No folder selected…");

        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Select Vault Folder");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JButton newBtn = new JButton("New Folder…");
        newBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Create New Vault Folder");
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File dir = fc.getSelectedFile();
                dir.mkdirs();
                pathField.setText(dir.getAbsolutePath());
            }
        });

        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(pathField, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
        btnRow.add(browseBtn);
        btnRow.add(newBtn);
        pathRow.add(btnRow, BorderLayout.EAST);
        panel.add(pathRow, BorderLayout.CENTER);

        JButton openBtn = new JButton("Open Vault");
        openBtn.putClientProperty("JButton.buttonType", "default");
        openBtn.addActionListener(e -> {
            String text = pathField.getText().trim();
            if (text.isEmpty()) { JOptionPane.showMessageDialog(this, "Please select a folder."); return; }
            chosenPath = Path.of(text);
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelBtn);
        buttons.add(openBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(openBtn);
        add(panel);
    }

    public Path getChosenPath() { return chosenPath; }
}