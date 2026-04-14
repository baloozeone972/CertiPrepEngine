package com.certiprep.ui;

import com.certiprep.core.model.Certification;
import com.certiprep.core.service.DatabaseService;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.service.QuestionLoader;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class MainController {

    private static final Logger logger = LoggerUtil.getLogger(MainController.class);

    @FXML
    private Button themeToggleBtn;
    @FXML
    private Button languageToggleBtn;
    @FXML
    private ListView<String> certificationList;
    @FXML
    private TextArea certInfoArea;
    @FXML
    private Button examModeBtn;
    @FXML
    private Button freeModeBtn;
    @FXML
    private Button revisionModeBtn;
    @FXML
    private Button historyBtn;
    @FXML
    private Button settingsBtn;
    @FXML
    private Button importBtn;
    @FXML
    private Button quitBtn;
    @FXML
    private Label statusLabel;

    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private QuestionLoader questionLoader;
    private List<Certification> certifications;
    private Certification selectedCertification;

    public void init(ThemeManager themeManager, I18nService i18nService, DatabaseService databaseService) {
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = QuestionLoader.getInstance();

        loadCertifications();
        setupEventHandlers();
        updateUILanguage();
    }

    private void loadCertifications() {
        certifications = questionLoader.getAllCertifications();
        certificationList.getItems().clear();

        for (Certification cert : certifications) {
            certificationList.getItems().add(cert.getName());
        }

        if (!certifications.isEmpty()) {
            certificationList.getSelectionModel().select(0);
            selectedCertification = certifications.get(0);
            updateCertificationInfo();
        }
    }

    private void setupEventHandlers() {
        certificationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            int index = certificationList.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index < certifications.size()) {
                selectedCertification = certifications.get(index);
                updateCertificationInfo();
            }
        });
    }

    private void updateCertificationInfo() {
        if (selectedCertification != null) {
            String info = String.format(
                    "ID: %s\nNom: %s\nVersion: %s\nDescription: %s\nTotal questions: %d\nDurée examen: %d min\nQuestions examen: %d\nSeuil réussite: %d%% (%d/%d)",
                    selectedCertification.getId(),
                    selectedCertification.getName(),
                    selectedCertification.getVersion(),
                    selectedCertification.getDescription(),
                    selectedCertification.getTotalQuestions(),
                    selectedCertification.getExamDurationMinutes(),
                    selectedCertification.getExamQuestionCount(),
                    selectedCertification.getPassingScore(),
                    selectedCertification.getPassingQuestionsCount(),
                    selectedCertification.getExamQuestionCount()
            );
            certInfoArea.setText(info);
        }
    }

    private void updateUILanguage() {
        examModeBtn.setText(i18nService.get("mode.exam"));
        freeModeBtn.setText(i18nService.get("mode.free"));
        revisionModeBtn.setText(i18nService.get("mode.revision"));
        historyBtn.setText(i18nService.get("history"));
        settingsBtn.setText(i18nService.get("settings"));
        importBtn.setText(i18nService.get("import.questions"));
        quitBtn.setText(i18nService.get("quit"));
        statusLabel.setText(i18nService.get("ready"));
    }

    @FXML
    private void toggleTheme() {
        if (themeToggleBtn != null && themeToggleBtn.getScene() != null) {
            themeManager.toggleTheme(themeToggleBtn.getScene());
        }
    }

    @FXML
    private void toggleLanguage() {
        if (i18nService.isFrench()) {
            i18nService.setEnglish();
        } else {
            i18nService.setFrench();
        }
        updateUILanguage();
    }

    @FXML
    private void startExamMode() {
        if (selectedCertification == null) {
            showAlert("Erreur", "Veuillez sélectionner une certification");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/exam.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 900, 700);
            themeManager.applyTheme(scene);

            ExamController controller = loader.getController();
            controller.init(selectedCertification, themeManager, i18nService, databaseService, questionLoader);

            Stage stage = new Stage();
            stage.setTitle("Mode Examen - " + selectedCertification.getName());
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            logger.severe("Erreur ouverture mode examen");
            showAlert("Erreur", "Impossible d'ouvrir le mode examen: " + e.getMessage());
        }
    }

    @FXML
    private void startFreeMode() {
        if (selectedCertification == null) {
            showAlert("Erreur", "Veuillez sélectionner une certification");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/free_mode.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 800, 600);
            themeManager.applyTheme(scene);

            FreeModeController controller = loader.getController();
            controller.init(selectedCertification, themeManager, i18nService, databaseService, questionLoader);

            Stage stage = new Stage();
            stage.setTitle("Mode Libre - " + selectedCertification.getName());
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            logger.severe("Erreur ouverture mode libre");
            showAlert("Erreur", "Impossible d'ouvrir le mode libre: " + e.getMessage());
        }
    }

    @FXML
    private void startRevisionMode() {
        if (selectedCertification == null) {
            showAlert("Erreur", "Veuillez sélectionner une certification");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/revision.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 800, 600);
            themeManager.applyTheme(scene);

            RevisionController controller = loader.getController();
            controller.init(selectedCertification, themeManager, i18nService, databaseService, questionLoader);

            Stage stage = new Stage();
            stage.setTitle("Mode Révision - " + selectedCertification.getName());
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            logger.severe("Erreur ouverture mode révision");
            showAlert("Erreur", "Impossible d'ouvrir le mode révision: " + e.getMessage());
        }
    }

    @FXML
    private void showHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 1000, 600);
            themeManager.applyTheme(scene);

            HistoryController controller = loader.getController();
            controller.init(themeManager, i18nService, databaseService);

            Stage stage = new Stage();
            stage.setTitle(i18nService.get("history"));
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            logger.severe("Erreur ouverture historique");
            showAlert("Erreur", "Impossible d'ouvrir l'historique: " + e.getMessage());
        }
    }

    @FXML
    private void showSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 600, 500);
            themeManager.applyTheme(scene);

            SettingsController controller = loader.getController();
            controller.init(themeManager, i18nService, databaseService, questionLoader);

            Stage stage = new Stage();
            stage.setTitle(i18nService.get("settings"));
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            logger.severe("Erreur ouverture paramètres");
            showAlert("Erreur", "Impossible d'ouvrir les paramètres: " + e.getMessage());
        }
    }

    @FXML
    private void importQuestions() {
        showAlert("Information", "Fonctionnalité d'import de questions à venir");
    }

    @FXML
    private void quit() {
        Platform.exit();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}