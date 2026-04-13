package com.certiprep.ui;

import com.certiprep.core.model.*;
import com.certiprep.core.service.*;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.logging.Logger;

import java.io.IOException;
import java.util.*;

public class FreeModeController {

    private static final Logger logger = LoggerUtil.getLogger(FreeModeController.class);

    @FXML private VBox themesContainer;
    @FXML private Slider questionSlider;
    @FXML private Label questionCountLabel;
    @FXML private RadioButton unlimitedTimeBtn;
    @FXML private RadioButton limitedTimeBtn;
    @FXML private Spinner<Integer> minutesSpinner;
    @FXML private Button generateBtn;
    @FXML private Button cancelBtn;

    private Certification certification;
    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private QuestionLoader questionLoader;
    private Map<String, CheckBox> themeCheckboxes;

    public void init(Certification certification, ThemeManager themeManager,
                     I18nService i18nService, DatabaseService databaseService,
                     QuestionLoader questionLoader) {
        this.certification = certification;
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = questionLoader;

        themeCheckboxes = new LinkedHashMap<>();

        setupUI();
    }

    @FXML
    private void initialize() {
        // Cette méthode est appelée automatiquement après le chargement FXML
        logger.info("Initialisation du contrôleur FreeModeController");
    }

    private void setupUI() {
        // Thèmes
        List<String> themes = questionLoader.getThemes(certification.getId());
        for (String theme : themes) {
            CheckBox cb = new CheckBox(theme);
            cb.setSelected(true);
            cb.setPadding(new Insets(5, 10, 5, 10));
            themeCheckboxes.put(theme, cb);
            themesContainer.getChildren().add(cb);
        }

        // Slider nombre de questions
        int maxQuestions = certification.getTotalQuestions();
        questionSlider.setMax(maxQuestions);
        questionSlider.setValue(30);
        questionCountLabel.setText("30");

        questionSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            questionCountLabel.setText(String.valueOf(newVal.intValue()));
        });

        // Durée
        ToggleGroup durationGroup = new ToggleGroup();
        unlimitedTimeBtn.setToggleGroup(durationGroup);
        limitedTimeBtn.setToggleGroup(durationGroup);
        unlimitedTimeBtn.setSelected(true);

        unlimitedTimeBtn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            minutesSpinner.setDisable(!newVal);
        });

        minutesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 360, 60));
        minutesSpinner.setDisable(true);
    }

    @FXML
    private void generateTest() {
        logger.info("Génération du test en mode libre");

        // Récupérer les thèmes sélectionnés
        List<String> selectedThemes = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : themeCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedThemes.add(entry.getKey());
            }
        }

        if (selectedThemes.isEmpty()) {
            showAlert("Erreur", "Veuillez sélectionner au moins un thème");
            return;
        }

        // Calculer le nombre total de questions disponibles
        int totalAvailable = 0;
        for (String theme : selectedThemes) {
            totalAvailable += questionLoader.getThemeQuestionCount(certification.getId(), theme);
        }

        int nbQuestions = (int) questionSlider.getValue();
        if (nbQuestions > totalAvailable) {
            showAlert("Attention", String.format("Seulement %d questions disponibles dans les thèmes sélectionnés", totalAvailable));
            nbQuestions = totalAvailable;
        }

        if (nbQuestions <= 0) {
            showAlert("Erreur", "Aucune question disponible");
            return;
        }

        // Répartir les questions proportionnellement
        Map<String, Integer> finalCounts = new HashMap<>();
        int remaining = nbQuestions;

        for (String theme : selectedThemes) {
            int available = questionLoader.getThemeQuestionCount(certification.getId(), theme);
            int count = (int) Math.round((double) available / totalAvailable * nbQuestions);
            count = Math.min(count, available);
            finalCounts.put(theme, count);
            remaining -= count;
        }

        // Distribuer le reste
        int themeIndex = 0;
        while (remaining > 0 && !selectedThemes.isEmpty()) {
            String theme = selectedThemes.get(themeIndex % selectedThemes.size());
            int current = finalCounts.get(theme);
            int max = questionLoader.getThemeQuestionCount(certification.getId(), theme);
            if (current < max) {
                finalCounts.put(theme, current + 1);
                remaining--;
            }
            themeIndex++;
        }

        // Générer les questions
        List<Question> questions = questionLoader.getRandomQuestionsByThemes(certification.getId(), finalCounts);

        if (questions.isEmpty()) {
            showAlert("Erreur", "Aucune question générée");
            return;
        }

        // Déterminer la durée
        int durationMinutes = unlimitedTimeBtn.isSelected() ? 0 : minutesSpinner.getValue();

        // Ouvrir le mode examen avec ces questions
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/exam.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 900, 700);
            themeManager.applyTheme(scene);

            ExamController controller = loader.getController();
            controller.initWithCustomQuestions(certification, questions, durationMinutes,
                    themeManager, i18nService, databaseService, questionLoader);

            Stage stage = (Stage) generateBtn.getScene().getWindow();
            stage.close();

            Stage examStage = new Stage();
            examStage.setTitle("Mode Libre - " + certification.getName());
            examStage.setScene(scene);
            examStage.show();

        } catch (IOException e) {
            logger.severe("Erreur génération test");
            showAlert("Erreur", "Impossible de générer le test: " + e.getMessage());
        }
    }

    @FXML
    private void cancel() {
        logger.info("Annulation du mode libre");
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}