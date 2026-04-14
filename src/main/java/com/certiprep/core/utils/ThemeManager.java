package com.certiprep.core.utils;

import javafx.scene.Scene;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

public class ThemeManager {

    private static final Logger logger = LoggerUtil.getLogger(ThemeManager.class);
    private static final String CONFIG_FILE = "data/settings.properties";
    private boolean isDarkMode;

    public ThemeManager() {
        loadSettings();
    }

    private void loadSettings() {
        Properties props = new Properties();
        Path configPath = Paths.get(CONFIG_FILE);

        if (Files.exists(configPath)) {
            try (InputStream input = new FileInputStream(CONFIG_FILE)) {
                props.load(input);
                isDarkMode = "dark".equals(props.getProperty("theme", "light"));
            } catch (IOException e) {
                logger.severe("Erreur chargement settings");
                isDarkMode = false;
            }
        } else {
            isDarkMode = false;
            saveSettings();
        }
    }

    private void saveSettings() {
        try {
            Files.createDirectories(Paths.get("data"));
            Properties props = new Properties();
            props.setProperty("theme", isDarkMode ? "dark" : "light");

            try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
                props.store(output, "CertiPrep Engine Settings");
            }
        } catch (IOException e) {
            logger.severe("Erreur sauvegarde settings");
        }
    }

    public void applyTheme(Scene scene) {
        String themeFile = isDarkMode ? "/css/dark-theme.css" : "/css/light-theme.css";
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource(themeFile).toExternalForm());
    }

    public void toggleTheme(Scene scene) {
        isDarkMode = !isDarkMode;
        saveSettings();
        applyTheme(scene);
    }

    public void setTheme(Scene scene, Theme theme) {
        isDarkMode = (theme == Theme.DARK);
        saveSettings();
        applyTheme(scene);
    }

    public boolean isDarkMode() {
        return isDarkMode;
    }

    public boolean isLightMode() {
        return !isDarkMode;
    }

    public enum Theme {
        LIGHT, DARK
    }
}