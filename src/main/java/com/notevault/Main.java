package com.notevault;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.notevault.db.DatabaseManager;
import com.notevault.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * @file Main.java
 * @brief Точка входа приложения NoteVault.
 *
 * @details
 * Инициализирует Look & Feel (FlatLaf macOS-стиль), применяет тему
 * (тёмная / светлая) из пользовательских настроек, затем запускает
 * главное окно {@link com.notevault.ui.MainWindow} в потоке Event Dispatch Thread.
 *
 * Порядок инициализации:
 * -# Выбор темы (из {@code Preferences} или автодетектирование).
 * -# Настройка UIManager (скругления, ширина скроллбара и т.д.).
 * -# Инициализация {@link DatabaseManager} (без открытия конкретного vault).
 * -# Создание и отображение {@link MainWindow}.
 */
public class Main {

    /**
     * @brief Главный метод приложения.
     *
     * @param args Аргументы командной строки (не используются).
     */
    public static void main(String[] args) {
        try {
            String theme = getPreferredTheme();
            if ("dark".equals(theme)) {
                FlatMacDarkLaf.setup();
            } else {
                FlatMacLightLaf.setup();
            }
            UIManager.put("defaultFont", new Font("SF Pro Text", Font.PLAIN, 10));
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

    /**
     * @brief Определяет предпочтительную тему оформления.
     *
     * @details
     * Читает значение ключа {@code "theme"} из {@link Preferences}.
     * Возможные значения: {@code "dark"}, {@code "light"}, {@code "auto"}.
     * При значении {@code "auto"} возвращает {@code "light"} (системное
     * определение тёмного режима macOS через переменную окружения оставлено
     * как заглушка для будущей реализации).
     *
     * @return {@code "dark"} или {@code "light"}.
     */
    private static String getPreferredTheme() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        String saved = prefs.get("theme", "auto");
        if ("dark".equals(saved)) return "dark";
        if ("light".equals(saved)) return "light";
        return "light";
    }
}