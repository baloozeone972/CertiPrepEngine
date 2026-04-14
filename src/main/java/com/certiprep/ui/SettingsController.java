package com.certiprep.ui;

import com.certiprep.core.service.DatabaseService;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.service.PreferencesService;
import com.certiprep.core.service.QuestionLoader;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class SettingsController {

    private static final Logger logger = LoggerUtil.getLogger(SettingsController.class);

    @FXML
    private RadioButton lightThemeBtn;
    @FXML
    private RadioButton darkThemeBtn;
    @FXML
    private RadioButton frenchBtn;
    @FXML
    private RadioButton englishBtn;
    @FXML
    private Button exportDataBtn;
    @FXML
    private Button importDataBtn;
    @FXML
    private Button resetDataBtn;
    @FXML
    private Button saveSettingsBtn;
    @FXML
    private Button cancelSettingsBtn;
    @FXML
    private Hyperlink websiteLink;

    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private QuestionLoader questionLoader;

    public void init(ThemeManager themeManager, I18nService i18nService,
                     DatabaseService databaseService, QuestionLoader questionLoader) {
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = questionLoader;

        loadCurrentSettings();

        ToggleGroup themeGroup = new ToggleGroup();
        lightThemeBtn.setToggleGroup(themeGroup);
        darkThemeBtn.setToggleGroup(themeGroup);

        ToggleGroup langGroup = new ToggleGroup();
        frenchBtn.setToggleGroup(langGroup);
        englishBtn.setToggleGroup(langGroup);

        saveSettingsBtn.setOnAction(e -> saveSettings());
        cancelSettingsBtn.setOnAction(e -> close());
        exportDataBtn.setOnAction(e -> exportData());
        importDataBtn.setOnAction(e -> importData());
        resetDataBtn.setOnAction(e -> resetData());
        websiteLink.setOnAction(e -> openWebsite());
    }

    private void loadCurrentSettings() {
        // Thème
        if (themeManager.isDarkMode()) {
            darkThemeBtn.setSelected(true);
        } else {
            lightThemeBtn.setSelected(true);
        }

        // Langue
        if (i18nService.isFrench()) {
            frenchBtn.setSelected(true);
        } else {
            englishBtn.setSelected(true);
        }
    }

    private void saveSettings() {
        // Appliquer le thème
        if (darkThemeBtn.isSelected() != themeManager.isDarkMode()) {
            themeManager.toggleTheme(saveSettingsBtn.getScene());
        }

        // Appliquer la langue
        if (frenchBtn.isSelected() && i18nService.isEnglish()) {
            i18nService.setFrench();
        } else if (englishBtn.isSelected() && i18nService.isFrench()) {
            i18nService.setEnglish();
        }

        showAlert("Succès", "Paramètres sauvegardés");
        close();
    }

    private void exportData() {
        // Exporter la base de données SQLite
        File sourceDb = new File("data/certiprep.db");
        if (!sourceDb.exists()) {
            showAlert("Erreur", "Aucune donnée à exporter");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Exporter les données");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("SQLite files (*.db)", "*.db")
        );
        fileChooser.setInitialFileName("certiprep_backup.db");

        File destFile = fileChooser.showSaveDialog(saveSettingsBtn.getScene().getWindow());
        if (destFile != null) {
            try {
                Files.copy(sourceDb.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                showAlert("Succès", "Données exportées avec succès");
            } catch (Exception e) {
                logger.severe("Erreur export");
                showAlert("Erreur", "Erreur lors de l'export: " + e.getMessage());
            }
        }
    }

    private void importData() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Importer des données");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("SQLite files (*.db)", "*.db")
        );

        File sourceFile = fileChooser.showOpenDialog(saveSettingsBtn.getScene().getWindow());
        if (sourceFile != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation");
            confirm.setHeaderText("Importer des données");
            confirm.setContentText("L'import remplacera les données existantes. Continuer ?");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                try {
                    // Fermer la connexion existante
                    databaseService.close();

                    // Copier le fichier
                    Path destPath = Paths.get("data/certiprep.db");
                    Files.createDirectories(destPath.getParent());
                    Files.copy(sourceFile.toPath(), destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    // Réinitialiser la connexion
                    databaseService.initializeDatabase();

                    showAlert("Succès", "Données importées avec succès. Redémarrez l'application pour appliquer les changements.");
                } catch (Exception e) {
                    logger.severe("Erreur import");
                    showAlert("Erreur", "Erreur lors de l'import: " + e.getMessage());
                }
            }
        }
    }

    private void resetData() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Réinitialiser les données");
        confirm.setContentText("Cette action supprimera TOUTES les données (historique). Continuer ?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // Supprimer la base de données
                Path dbPath = Paths.get("data/certiprep.db");
                Files.deleteIfExists(dbPath);

                // Réinitialiser la connexion
                databaseService.close();
                databaseService.initializeDatabase();

                showAlert("Succès", "Données réinitialisées avec succès");
            } catch (Exception e) {
                logger.severe("Erreur reset");
                showAlert("Erreur", "Erreur lors de la réinitialisation: " + e.getMessage());
            }
        }
    }

    private void openWebsite() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/certiprep"));
        } catch (Exception e) {
            logger.severe("Erreur ouverture site");
        }
    }

    private void close() {
        Stage stage = (Stage) cancelSettingsBtn.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void loadPreferences() {
        PreferencesService prefs = PreferencesService.getInstance();

        // Thème
        boolean isDark = "dark".equals(prefs.getTheme());
        if (isDark) darkThemeBtn.setSelected(true);
        else lightThemeBtn.setSelected(true);

        // Langue
        boolean isFrench = "fr".equals(prefs.getLanguage());
        if (isFrench) frenchBtn.setSelected(true);
        else englishBtn.setSelected(true);
    }

    @FXML
    private void savePreferences() {
        PreferencesService prefs = PreferencesService.getInstance();
        prefs.setTheme(darkThemeBtn.isSelected() ? "dark" : "light");
        prefs.setLanguage(frenchBtn.isSelected() ? "fr" : "en");

        // Appliquer immédiatement
        if (darkThemeBtn.isSelected() != themeManager.isDarkMode()) {
            themeManager.toggleTheme(saveSettingsBtn.getScene());
        }
        if (frenchBtn.isSelected() && i18nService.isEnglish()) {
            i18nService.setFrench();
        } else if (englishBtn.isSelected() && i18nService.isFrench()) {
            i18nService.setEnglish();
        }

        showAlert("Succès", "Préférences sauvegardées");
    }
}