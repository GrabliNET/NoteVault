package com.notevault.ui.dialogs;

import com.notevault.model.Tag;
import com.notevault.service.VaultService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TagManagerDialog extends JDialog {

    private final List<Tag> selectedTags;
    private boolean changed = false;
    private DefaultListModel<Tag> availableModel;
    private DefaultListModel<Tag> selectedModel;

    public TagManagerDialog(Frame parent, com.notevault.model.Note note) {
        super(parent, "Manage Tags", true);
        setSize(480, 360);
        setLocationRelativeTo(parent);
        selectedTags = new ArrayList<>(note.getTags() != null ? note.getTags() : List.of());
        buildUI();
    }

    private void buildUI() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Available tags
        availableModel = new DefaultListModel<>();
        try {
            for (Tag t : VaultService.getInstance().getAllTags()) {
                boolean alreadySelected = selectedTags.stream().anyMatch(s -> s.getName().equalsIgnoreCase(t.getName()));
                if (!alreadySelected) availableModel.addElement(t);
            }
        } catch (Exception ignored) {}

        JList<Tag> availableList = new JList<>(availableModel);
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Selected tags
        selectedModel = new DefaultListModel<>();
        selectedTags.forEach(selectedModel::addElement);
        JList<Tag> selectedList = new JList<>(selectedModel);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.add(new JLabel("Available Tags"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(availableList), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 6));
        rightPanel.add(new JLabel("Note Tags"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(selectedList), BorderLayout.CENTER);

        // New tag input
        JTextField newTagField = new JTextField();
        newTagField.putClientProperty("JTextField.placeholderText", "New tag name…");
        JButton addNewBtn = new JButton("Add");
        addNewBtn.addActionListener(e -> {
            String name = newTagField.getText().trim();
            if (!name.isEmpty()) {
                Tag t = new Tag(name);
                selectedModel.addElement(t);
                newTagField.setText("");
                changed = true;
            }
        });
        newTagField.addActionListener(e -> addNewBtn.doClick());

        JPanel newTagRow = new JPanel(new BorderLayout(4, 0));
        newTagRow.add(newTagField, BorderLayout.CENTER);
        newTagRow.add(addNewBtn, BorderLayout.EAST);
        rightPanel.add(newTagRow, BorderLayout.SOUTH);

        // Arrow buttons
        JButton addBtn = new JButton("→");
        addBtn.setToolTipText("Add selected");
        addBtn.addActionListener(e -> {
            for (Tag t : availableList.getSelectedValuesList()) {
                availableModel.removeElement(t);
                selectedModel.addElement(t);
                changed = true;
            }
        });
        JButton removeBtn = new JButton("←");
        removeBtn.setToolTipText("Remove selected");
        removeBtn.addActionListener(e -> {
            for (Tag t : selectedList.getSelectedValuesList()) {
                selectedModel.removeElement(t);
                availableModel.addElement(t);
                changed = true;
            }
        });

        JPanel arrows = new JPanel(new GridLayout(2, 1, 0, 4));
        arrows.add(addBtn);
        arrows.add(removeBtn);
        JPanel arrowWrapper = new JPanel(new GridBagLayout());
        arrowWrapper.add(arrows);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(arrowWrapper, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);
        leftPanel.setPreferredSize(new Dimension(160, 0));
        rightPanel.setPreferredSize(new Dimension(160, 0));

        JButton okBtn = new JButton("Apply");
        okBtn.putClientProperty("JButton.buttonType", "default");
        okBtn.addActionListener(e -> {
            selectedTags.clear();
            for (int i = 0; i < selectedModel.size(); i++) selectedTags.add(selectedModel.get(i));
            dispose();
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> { changed = false; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancelBtn);
        buttons.add(okBtn);

        getRootPane().setDefaultButton(okBtn);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    public List<Tag> getSelectedTags() { return selectedTags; }
    public boolean isChanged() { return changed; }
}