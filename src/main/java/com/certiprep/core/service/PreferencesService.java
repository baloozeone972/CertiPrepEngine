package com.certiprep.core.service;

import com.certiprep.core.utils.ThemeManager;

import java.util.prefs.Preferences;

public class PreferencesService {

    private static PreferencesService instance;
    private final Preferences prefs;

    private PreferencesService() {
        prefs = Preferences.userNodeForPackage(PreferencesService.class);
    }

    public static synchronized PreferencesService getInstance() {
        if (instance == null) {
            instance = new PreferencesService();
        }
        return instance;
    }

    public String getTheme() {
        return prefs.get("theme", "light");
    }

    // Thème
    public void setTheme(String theme) {
        prefs.put("theme", theme);
    }

    public String getLanguage() {
        return prefs.get("language", "fr");
    }

    // Langue
    public void setLanguage(String lang) {
        prefs.put("language", lang);
    }

    public String getLastCertification() {
        return prefs.get("last_certification", "java21");
    }

    // Dernière certification
    public void setLastCertification(String certId) {
        prefs.put("last_certification", certId);
    }

    public String getFreeModeThemes() {
        return prefs.get("free_mode_themes", "");
    }

    // Préférences mode libre
    public void setFreeModeThemes(String themes) {
        prefs.put("free_mode_themes", themes);
    }

    public int getFreeModeQuestionCount() {
        return prefs.getInt("free_mode_question_count", 30);
    }

    public void setFreeModeQuestionCount(int count) {
        prefs.putInt("free_mode_question_count", count);
    }

    public int getFreeModeDuration() {
        return prefs.getInt("free_mode_duration", 60);
    }

    public void setFreeModeDuration(int minutes) {
        prefs.putInt("free_mode_duration", minutes);
    }

    public double getWindowWidth() {
        return prefs.getDouble("window_width", 1200);
    }

    // Taille de fenêtre
    public void setWindowWidth(double width) {
        prefs.putDouble("window_width", width);
    }

    public double getWindowHeight() {
        return prefs.getDouble("window_height", 700);
    }

    public void setWindowHeight(double height) {
        prefs.putDouble("window_height", height);
    }

    // Sauvegarde générale
    public void saveAllSettings(ThemeManager themeManager, I18nService i18nService,
                                double width, double height, String lastCert) {
        setTheme(themeManager.isDarkMode() ? "dark" : "light");
        setLanguage(i18nService.isFrench() ? "fr" : "en");
        setWindowWidth(width);
        setWindowHeight(height);
        setLastCertification(lastCert);
    }
}