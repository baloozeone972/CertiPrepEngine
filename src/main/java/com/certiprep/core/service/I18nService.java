package com.certiprep.core.service;

import com.certiprep.core.utils.LoggerUtil;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class I18nService {

    private static final Logger logger = LoggerUtil.getLogger(I18nService.class);
    private static I18nService instance;
    private ResourceBundle bundle;
    private Locale currentLocale;

    private I18nService() {
        this.currentLocale = Locale.FRENCH; // Par défaut français
        loadBundle();
    }

    public static synchronized I18nService getInstance() {
        if (instance == null) {
            instance = new I18nService();
        }
        return instance;
    }

    private void loadBundle() {
        try {
            bundle = ResourceBundle.getBundle("i18n/messages", currentLocale);
            logger.info("Langue chargée: {}" + "currentLocale.getDisplayName()");
        } catch (Exception e) {
            logger.severe("Erreur chargement bundle pour " + " currentLocale");
            bundle = ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
        }
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        loadBundle();
    }

    public void setFrench() {
        setLocale(Locale.FRENCH);
    }

    public void setEnglish() {
        setLocale(Locale.ENGLISH);
    }

    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            logger.warning("Clé non trouvée: " + " key");
            return key;
        }
    }

    public String get(String key, Object... args) {
        return String.format(get(key), args);
    }

    public ResourceBundle getBundle() {
        return bundle;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public boolean isFrench() {
        return currentLocale.equals(Locale.FRENCH);
    }

    public boolean isEnglish() {
        return currentLocale.equals(Locale.ENGLISH);
    }
}