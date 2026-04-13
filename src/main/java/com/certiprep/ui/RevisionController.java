package com.certiprep.ui;

import com.certiprep.core.model.*;
import com.certiprep.core.service.*;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.util.logging.Logger;


import java.util.*;

public class RevisionController {

    private static final Logger logger = LoggerUtil.getLogger(RevisionController.class);

    @FXML private ComboBox<String> themeCombo;
    @FXML private Slider questionSlider;
    @FXML private Label questionCountLabel;
    @FXML private Button startBtn;
    @FXML private ScrollPane revisionScroll;
    @FXML private Text revisionQuestion;
    @FXML private VBox revisionOptions;
    @FXML private Button showAnswerBtn;
    @FXML private VBox answerContainer;
    @FXML private Text correctAnswerText;
    @FXML private Text explanationText;
    @FXML private Button prevRevisionBtn;
    @FXML private Button nextRevisionBtn;
    @FXML private Button randomRevisionBtn;
    @FXML private Label revisionCounter;

    private Certification certification;
    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private QuestionLoader questionLoader;

    private List<Question> revisionQuestions;
    private int currentIndex;
    private List<RadioButton> optionButtons;
    private ToggleGroup optionsGroup;

    public void init(Certification certification, ThemeManager themeManager,
                     I18nService i18nService, DatabaseService databaseService,
                     QuestionLoader questionLoader) {
        this.certification = certification;
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = questionLoader;

        setupUI();
    }

    private void setupUI() {
        // Thèmes
        themeCombo.getItems().add("Tous les thèmes");
        themeCombo.getItems().addAll(questionLoader.getThemes(certification.getId()));
        themeCombo.getSelectionModel().select(0);

        // Slider
        int maxQuestions = certification.getTotalQuestions();
        questionSlider.setMax(maxQuestions);
        questionSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            questionCountLabel.setText(String.valueOf(newVal.intValue()));
        });

        startBtn.setOnAction(e -> startRevision());
        showAnswerBtn.setOnAction(e -> showAnswer());
        prevRevisionBtn.setOnAction(e -> previousQuestion());
        nextRevisionBtn.setOnAction(e -> nextQuestion());
        randomRevisionBtn.setOnAction(e -> randomQuestion());
    }

    private void startRevision() {
        String selectedTheme = themeCombo.getValue();
        int nbQuestions = (int) questionSlider.getValue();

        if ("Tous les thèmes".equals(selectedTheme)) {
            revisionQuestions = questionLoader.getRandomQuestions(certification.getId(), nbQuestions);
        } else {
            List<Question> themeQuestions = questionLoader.getQuestionsByTheme(certification.getId(), selectedTheme);
            if (themeQuestions.size() < nbQuestions) {
                nbQuestions = themeQuestions.size();
            }
            List<Question> shuffled = new ArrayList<>(themeQuestions);
            Collections.shuffle(shuffled);
            revisionQuestions = shuffled.subList(0, nbQuestions);
        }

        if (revisionQuestions.isEmpty()) {
            showAlert("Erreur", "Aucune question disponible");
            return;
        }

        currentIndex = 0;
        revisionScroll.setVisible(true);
        startBtn.setDisable(true);
        themeCombo.setDisable(true);
        questionSlider.setDisable(true);

        displayRevisionQuestion();
    }

    /*private void displayRevisionQuestion() {
        Question q = revisionQuestions.get(currentIndex);
        revisionQuestion.setText(q.getQuestion());

        revisionOptions.getChildren().clear();
        optionButtons = new ArrayList<>();
        optionsGroup = new ToggleGroup();

        for (int i = 0; i < q.getOptions().size(); i++) {
            RadioButton rb = new RadioButton(q.getOptions().get(i));
            rb.setToggleGroup(optionsGroup);
            rb.setUserData(i);
            rb.setWrapText(true);
            rb.setPrefWidth(700);
            rb.getStyleClass().add("question-option");

            int finalI = i;
            rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    // En mode révision, on peut vérifier immédiatement
                    checkAnswer(finalI);
                }
            });

            optionButtons.add(rb);
            revisionOptions.getChildren().add(rb);
        }

        // Cacher la réponse
        answerContainer.setVisible(false);
        showAnswerBtn.setVisible(true);

        revisionCounter.setText(String.format("Question %d/%d", currentIndex + 1, revisionQuestions.size()));

        updateNavigationButtons();
    }
*/
    private void checkAnswer(int selectedIndex) {
        Question q = revisionQuestions.get(currentIndex);
        boolean isCorrect = (selectedIndex == q.getCorrect());

        // Colorer la réponse
        for (int i = 0; i < optionButtons.size(); i++) {
            RadioButton rb = optionButtons.get(i);
            if (i == q.getCorrect()) {
                rb.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            } else if (i == selectedIndex && !isCorrect) {
                rb.setStyle("-fx-text-fill: red;");
            }
        }

        if (!isCorrect) {
            showAnswer();
        } else {
            // Si correct, afficher tout de même l'explication
            correctAnswerText.setText("✓ Correct! " + q.getCorrectAnswer());
            explanationText.setText(q.getExplanation());
            answerContainer.setVisible(true);
            showAnswerBtn.setVisible(false);
        }
    }

    private void showAnswer() {
        Question q = revisionQuestions.get(currentIndex);
        correctAnswerText.setText("Réponse correcte: " + q.getCorrectAnswer());
        explanationText.setText(q.getExplanation());
        answerContainer.setVisible(true);
        showAnswerBtn.setVisible(false);

        // Mettre en évidence la bonne réponse
        for (int i = 0; i < optionButtons.size(); i++) {
            if (i == q.getCorrect()) {
                optionButtons.get(i).setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            }
        }
    }

    private void previousQuestion() {
        if (currentIndex > 0) {
            currentIndex--;
            displayRevisionQuestion();
        }
        updateNavigationButtons();
    }

    private void nextQuestion() {
        if (currentIndex < revisionQuestions.size() - 1) {
            currentIndex++;
            displayRevisionQuestion();
        }
        updateNavigationButtons();
    }

    private void randomQuestion() {
        if (!revisionQuestions.isEmpty()) {
            currentIndex = new Random().nextInt(revisionQuestions.size());
            displayRevisionQuestion();
        }
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        prevRevisionBtn.setDisable(currentIndex == 0);
        nextRevisionBtn.setDisable(currentIndex == revisionQuestions.size() - 1);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    // Ajouter cette méthode à RevisionController.java

    public void initWithQuestions(List<Question> questionsToReview, String title,
                                  ThemeManager themeManager, I18nService i18nService) {
        this.themeManager = themeManager;
        this.i18nService = i18nService;

        this.revisionQuestions = new ArrayList<>(questionsToReview);
        this.currentIndex = 0;

        setupRevisionUI();
        displayRevisionQuestion();

        // Mettre à jour le titre
        Stage stage = (Stage) startBtn.getScene().getWindow();
        if (stage != null) {
            stage.setTitle(title);
        }
    }

    private void setupRevisionUI() {
        revisionScroll.setVisible(true);
        showAnswerBtn.setVisible(true);
        updateNavigationButtons();
    }

    private void displayRevisionQuestion() {
        if (revisionQuestions == null || revisionQuestions.isEmpty()) return;

        Question q = revisionQuestions.get(currentIndex);
        revisionQuestion.setText(q.getQuestion());

        revisionOptions.getChildren().clear();
        optionButtons = new ArrayList<>();
        optionsGroup = new ToggleGroup();

        for (int i = 0; i < q.getOptions().size(); i++) {
            RadioButton rb = new RadioButton(q.getOptions().get(i));
            rb.setToggleGroup(optionsGroup);
            rb.setUserData(i);
            rb.setWrapText(true);
            rb.setPrefWidth(700);
            rb.getStyleClass().add("question-option");

            final int selectedIndex = i;
            rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    checkAnswer(selectedIndex);
                }
            });

            optionButtons.add(rb);
            revisionOptions.getChildren().add(rb);
        }

        answerContainer.setVisible(false);
        showAnswerBtn.setVisible(true);
        revisionCounter.setText(String.format("Question %d/%d", currentIndex + 1, revisionQuestions.size()));
        updateNavigationButtons();
    }
}