package com.notevault.ui.components;

import com.notevault.model.Tag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontal panel displaying tags as colored chips with an × remove button.
 */
public class TagChipPanel extends JPanel {

    private final Consumer<Tag> onRemove;

    public TagChipPanel(Consumer<Tag> onRemove) {
        this.onRemove = onRemove;
        setLayout(new WrapLayout(FlowLayout.LEFT, 4, 2));
        setOpaque(false);
        setPreferredSize(new Dimension(0, 26));
    }

    public void setTags(List<Tag> tags) {
        removeAll();
        if (tags != null) {
            for (Tag tag : tags) {
                add(buildChip(tag));
            }
        }
        revalidate();
        repaint();
    }

    private JPanel buildChip(Tag tag) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = UIManager.getColor("Component.accentColor");
                if (bg == null) bg = new Color(0x5588CC);
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setBorder(new EmptyBorder(1, 6, 1, 3));

        JLabel nameLabel = new JLabel("#" + tag.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent == null) accent = new Color(0x5588CC);
        nameLabel.setForeground(accent);

        JButton removeBtn = new JButton("×");
        removeBtn.setFont(removeBtn.getFont().deriveFont(11f));
        removeBtn.setBorderPainted(false);
        removeBtn.setContentAreaFilled(false);
        removeBtn.setFocusable(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.setForeground(UIManager.getColor("Label.disabledForeground"));
        removeBtn.setMargin(new Insets(0, 0, 0, 0));
        removeBtn.addActionListener(e -> onRemove.accept(tag));
        removeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                removeBtn.setForeground(UIManager.getColor("Label.foreground"));
            }
            @Override public void mouseExited(MouseEvent e) {
                removeBtn.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        });

        chip.add(nameLabel);
        chip.add(removeBtn);
        return chip;
    }

    /**
     * FlowLayout that wraps to new lines when there's not enough space.
     */
    public static class WrapLayout extends FlowLayout {
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);
                int width = 0, height = 0, rowWidth = 0, rowHeight = 0;
                int nmembers = target.getComponentCount();
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth) {
                            height += rowHeight + vgap;
                            rowWidth = d.width + hgap;
                            rowHeight = d.height;
                        } else {
                            rowWidth += d.width + hgap;
                            rowHeight = Math.max(rowHeight, d.height);
                        }
                        width = Math.max(width, rowWidth);
                    }
                }
                height += rowHeight + insets.top + insets.bottom + vgap * 2;
                return new Dimension(width, Math.max(height, 22));
            }
        }
    }
}