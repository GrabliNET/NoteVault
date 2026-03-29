package com.notevault;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.notevault.db.DatabaseManager;
import com.notevault.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public class Main {

    public static void main(String[] args) {
        // Apply FlatLaf Mac theme based on system preference
        try {
            String theme = getPreferredTheme();
            if ("dark".equals(theme)) {
                FlatMacDarkLaf.setup();
            } else {
                FlatMacLightLaf.setup();
            }
            // Extra FlatLaf tweaks
            UIManager.put("defaultFont", new Font("SF Pro Text", Font.PLAIN, 14));
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 8);
            UIManager.put("TabbedPane.tabHeight", 32);
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            DatabaseManager.getInstance().initialize();
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }

    private static String getPreferredTheme() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        String saved = prefs.get("theme", "auto");
        if ("dark".equals(saved)) return "dark";
        if ("light".equals(saved)) return "light";
        // Auto-detect from system
        String osTheme = System.getProperty("os.name", "").toLowerCase();
        // Check macOS dark mode
        try {
            String result = System.getenv("DARK_MODE");
            if ("1".equals(result)) return "dark";
        } catch (Exception ignored) {}
        return "light";
    }
}