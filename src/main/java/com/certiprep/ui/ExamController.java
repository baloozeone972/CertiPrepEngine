package com.certiprep.ui;

import com.certiprep.core.model.Certification;
import com.certiprep.core.model.ExamSession;
import com.certiprep.core.model.Question;
import com.certiprep.core.model.UserAnswer;
import com.certiprep.core.service.DatabaseService;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.service.QuestionLoader;
import com.certiprep.core.service.ScoringService;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ExamController {

    private static final Logger logger = LoggerUtil.getLogger(ExamController.class);
    @FXML
    public Button pauseBtn;

    @FXML
    private Label questionCounter;
    @FXML
    private Label timerLabel;
    @FXML
    private Button submitBtn;
    @FXML
    private Text questionText;
    @FXML
    private VBox optionsContainer;
    @FXML
    private Button prevBtn;
    @FXML
    private Button nextBtn;
    @FXML
    private Button markBtn;
    @FXML
    private FlowPane paletteContainer;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label answeredStatus;

    private Certification certification;
    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private QuestionLoader questionLoader;

    private List<Question> examQuestions;
    private List<UserAnswer> userAnswers;
    private Set<Integer> markedQuestions;
    private int currentIndex;
    private Timeline timer;
    private long remainingSeconds;
    private long totalSeconds;
    private List<RadioButton> optionButtons;
    private ToggleGroup optionsGroup;
    private boolean isPaused = false;

    public void init(Certification certification, ThemeManager themeManager,
                     I18nService i18nService, DatabaseService databaseService,
                     QuestionLoader questionLoader) {
        this.certification = certification;
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = questionLoader;

        // Générer les questions de l'examen
        examQuestions = questionLoader.getRandomQuestions(
                certification.getId(),
                certification.getExamQuestionCount()
        );

        // Vérification
        if (examQuestions == null || examQuestions.isEmpty()) {
            logger.severe("AUCUNE QUESTION CHARGÉE !");
            showAlert("Erreur", "Impossible de charger les questions.");
            return;
        }

        logger.info("{} questions chargées pour l'examen" + "examQuestions.size()");

        // Initialiser le timer avec la durée de l'examen
        totalSeconds = certification.getExamDurationMinutes() * 60L;
        remainingSeconds = totalSeconds;

        logger.info("Timer initialisé à {} secondes ({} minutes)" + " remainingSeconds, certification.getExamDurationMinutes()");

        initExam();
    }

    public void initWithCustomQuestions(Certification certification, List<Question> questions,
                                        int durationMinutes, ThemeManager themeManager,
                                        I18nService i18nService, DatabaseService databaseService,
                                        QuestionLoader questionLoader) {
        this.certification = certification;
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;
        this.questionLoader = questionLoader;

        this.examQuestions = new ArrayList<>(questions);

        if (durationMinutes > 0) {
            totalSeconds = durationMinutes * 60L;
            remainingSeconds = totalSeconds;
        } else {
            totalSeconds = 0;
            remainingSeconds = 0;
        }

        logger.info("Mode libre: {} questions, durée: {} minutes" + "examQuestions.size(), durationMinutes");

        initExam();
    }

    private void initExam() {
        // Initialiser les réponses
        userAnswers = new ArrayList<>();
        for (int i = 0; i < examQuestions.size(); i++) {
            userAnswers.add(new UserAnswer(examQuestions.get(i).getId(), -1, false, 0, examQuestions.get(i).getTheme()));
        }

        markedQuestions = new HashSet<>();
        currentIndex = 0;
        optionButtons = new ArrayList<>();
        optionsGroup = new ToggleGroup();

        setupUI();

        // Démarrer le timer seulement si une durée est définie
        if (totalSeconds > 0) {
            startTimer();
            logger.info("Timer démarré avec {} secondes" + remainingSeconds);
        } else {
            timerLabel.setText("Illimité");
            logger.info("Mode sans limite de temps");
        }

        displayQuestion();
    }

    private void setupUI() {
        updateNavigationButtons();
        updatePalette();
        updateProgress();

        // Initialiser l'affichage du timer
        if (totalSeconds > 0) {
            updateTimerDisplay();
        } else {
            timerLabel.setText("Illimité");
        }
    }

    private void startTimer() {
        if (timer != null) {
            timer.stop();
        }

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (!isPaused && remainingSeconds > 0) {
                remainingSeconds--;
                updateTimerDisplay();

                if (remainingSeconds <= 0) {
                    timer.stop();
                    logger.info("Temps écoulé ! Soumission automatique de l'examen");
                    submitExam();
                }
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
        logger.info("Timer démarré");
    }

    private void updateTimerDisplay() {
        if (timerLabel == null) {
            logger.severe("timerLabel est null !");
            return;
        }

        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        long seconds = remainingSeconds % 60;

        String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        timerLabel.setText(timeStr);

        // Changement de couleur quand il reste moins de 5 minutes
        if (remainingSeconds <= 300 && remainingSeconds > 0) {
            timerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 16px;");
        } else if (remainingSeconds > 0) {
            timerLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 16px;");
        }

        // Mettre à jour le titre de la fenêtre avec le temps restant
        Stage stage = (Stage) timerLabel.getScene().getWindow();
        if (stage != null) {
            stage.setTitle(String.format("Examen - Temps restant: %s", timeStr));
        }
    }

    @FXML
    private void pauseTimer() {
        isPaused = !isPaused;
        if (isPaused) {
            logger.info("Timer en pause");
            timerLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            logger.info("Timer repris");
            updateTimerDisplay();
        }
    }

    private void displayQuestion() {
        if (examQuestions == null || examQuestions.isEmpty() || currentIndex >= examQuestions.size()) {
            logger.severe("Impossible d'afficher la question: liste vide ou index invalide");
            return;
        }

        Question q = examQuestions.get(currentIndex);
        questionText.setText(q.getQuestion());

        optionsContainer.getChildren().clear();
        optionButtons.clear();
        optionsGroup = new ToggleGroup();

        for (int i = 0; i < q.getOptions().size(); i++) {
            RadioButton rb = new RadioButton(q.getOptions().get(i));
            rb.setToggleGroup(optionsGroup);
            rb.setUserData(i);
            rb.setWrapText(true);
            rb.setPrefWidth(700);
            rb.getStyleClass().add("question-option");

            // Restaurer la réponse précédente
            UserAnswer answer = userAnswers.get(currentIndex);
            if (answer.getSelectedAnswer() == i) {
                rb.setSelected(true);
            }

            final int selectedIndex = i;
            rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    saveAnswer(selectedIndex);
                }
            });

            optionButtons.add(rb);
            optionsContainer.getChildren().add(rb);
        }

        // Mise à jour du marqueur
        boolean isMarked = markedQuestions.contains(currentIndex);
        markBtn.setText(isMarked ? "Retirer marqueur" : "Marquer pour révision");

        questionCounter.setText(String.format("Question %d/%d", currentIndex + 1, examQuestions.size()));

        updatePalette();
        updateProgress();
        updateNavigationButtons();
    }

    private void saveAnswer(int selectedIndex) {
        UserAnswer answer = userAnswers.get(currentIndex);
        answer.setSelectedAnswer(selectedIndex);
        answer.setCorrect(selectedIndex == examQuestions.get(currentIndex).getCorrect());
        answer.setTheme(examQuestions.get(currentIndex).getTheme());
        answer.setResponseTimeMs(System.currentTimeMillis());
        updatePalette();
        updateProgress();
    }

    @FXML
    private void previousQuestion() {
        if (currentIndex > 0) {
            currentIndex--;
            displayQuestion();
        }
        updateNavigationButtons();
    }

    @FXML
    private void nextQuestion() {
        if (currentIndex < examQuestions.size() - 1) {
            currentIndex++;
            displayQuestion();
        }
        updateNavigationButtons();
    }

    @FXML
    private void toggleMark() {
        if (markedQuestions.contains(currentIndex)) {
            markedQuestions.remove(currentIndex);
            markBtn.setText("Marquer pour révision");
        } else {
            markedQuestions.add(currentIndex);
            markBtn.setText("Retirer marqueur");
        }
        updatePalette();
    }

    @FXML
    private void submitExam() {
        logger.info("Soumission de l'examen");

        if (timer != null) {
            timer.stop();
        }

        // Vérifier si toutes les questions ont une réponse
        long unanswered = userAnswers.stream().filter(a -> a.getSelectedAnswer() == -1).count();
        if (unanswered > 0) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation");
            confirm.setHeaderText("Questions sans réponse");
            confirm.setContentText(String.format("Il reste %d questions sans réponse. Voulez-vous vraiment soumettre ?", unanswered));

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                // Reprendre le timer si l'utilisateur annule
                if (timer != null && remainingSeconds > 0) {
                    timer.play();
                }
                return;
            }
        }

        // Calculer le score final
        ScoringService scoringService = ScoringService.getInstance();
        ExamSession session = createSession();
        scoringService.calculateScore(session, examQuestions);

        // Sauvegarder la session
        databaseService.saveSession(session);

        // Afficher les résultats
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/results.fxml"));
            Scene scene = new Scene(loader.load(), 900, 700);
            themeManager.applyTheme(scene);

            ResultsController controller = loader.getController();
            controller.init(session, examQuestions, themeManager, i18nService, databaseService);

            Stage stage = (Stage) submitBtn.getScene().getWindow();
            stage.close();

            Stage resultsStage = new Stage();
            resultsStage.setTitle("Résultats");
            resultsStage.setScene(scene);
            resultsStage.show();

        } catch (IOException e) {
            logger.severe("Erreur ouverture résultats");
            showAlert("Erreur", "Impossible d'afficher les résultats: " + e.getMessage());
        }
    }

    private void updateNavigationButtons() {
        prevBtn.setDisable(currentIndex == 0);
        nextBtn.setDisable(currentIndex == examQuestions.size() - 1);
    }

    private void updatePalette() {
        if (paletteContainer == null) return;

        paletteContainer.getChildren().clear();

        for (int i = 0; i < examQuestions.size(); i++) {
            Button btn = new Button(String.valueOf(i + 1));
            btn.setPrefWidth(35);
            btn.setPrefHeight(35);
            btn.getStyleClass().add("palette-btn");

            UserAnswer answer = userAnswers.get(i);
            if (answer.getSelectedAnswer() != -1) {
                btn.getStyleClass().add("palette-answered");
            } else if (markedQuestions.contains(i)) {
                btn.getStyleClass().add("palette-marked");
            } else {
                btn.getStyleClass().add("palette-unanswered");
            }

            if (i == currentIndex) {
                btn.getStyleClass().add("palette-current");
            }

            final int index = i;
            btn.setOnAction(e -> {
                currentIndex = index;
                displayQuestion();
                updateNavigationButtons();
            });

            paletteContainer.getChildren().add(btn);
        }
    }

    private void updateProgress() {
        long answered = userAnswers.stream().filter(a -> a.getSelectedAnswer() != -1).count();
        double progress = (double) answered / examQuestions.size();
        progressBar.setProgress(progress);
        answeredStatus.setText(String.format("Répondues: %d/%d", answered, examQuestions.size()));
    }

    private ExamSession createSession() {
        ExamSession session = new ExamSession(
                certification.getId(),
                ExamSession.ExamMode.EXAM,
                examQuestions.size(),
                certification.getExamDurationMinutes()
        );

        // Calculer le temps passé
        long timeSpent = totalSeconds - remainingSeconds;
        session.setDurationMinutes((int) (timeSpent / 60));

        for (UserAnswer answer : userAnswers) {
            session.getUserAnswers().add(answer);
        }

        session.setEndTime(java.time.LocalDateTime.now());

        // Calculer le score
        int correctCount = 0;
        for (int i = 0; i < userAnswers.size(); i++) {
            UserAnswer answer = userAnswers.get(i);
            Question q = examQuestions.get(i);
            if (answer.getSelectedAnswer() == q.getCorrect()) {
                correctCount++;
                answer.setCorrect(true);
            }
        }
        session.setScore(correctCount);
        session.setPassed(correctCount >= certification.getPassingQuestionsCount());

        return session;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void togglePause() {
        isPaused = !isPaused;
        if (timer != null) {
            if (isPaused) {
                timer.pause();
                pauseBtn.setText("▶ Reprendre");
                timerLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                logger.info("Examen mis en pause");
            } else {
                timer.play();
                pauseBtn.setText("⏸ Pause");
                updateTimerDisplay();
                logger.info("Examen repris");
            }
        }
    }

    //TODO
    /*// Variables supplémentaires
private boolean isPaused = false;
private Button pauseBtn;

// Dans setupUI() ou initExam()
private void setupUI() {
    // ... code existant ...

    // Ajouter bouton pause si pas déjà fait
    if (pauseBtn == null && timerLabel != null) {
        pauseBtn = new Button("⏸ Pause");
        pauseBtn.getStyleClass().add("secondary-btn");
        pauseBtn.setOnAction(e -> togglePause());
        // Ajouter à la barre supérieure - à adapter selon votre layout
    }
}

@FXML
private void togglePause() {
    isPaused = !isPaused;
    if (timer != null) {
        if (isPaused) {
            timer.pause();
            pauseBtn.setText("▶ Reprendre");
            timerLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            logger.info("Examen mis en pause");
        } else {
            timer.play();
            pauseBtn.setText("⏸ Pause");
            updateTimerDisplay();
            logger.info("Examen repris");
        }
    }
}

// Modifier updateTimer pour vérifier isPaused
private void updateTimer() {
    if (!isPaused && remainingSeconds > 0) {
        remainingSeconds--;
        updateTimerDisplay();

        if (remainingSeconds <= 0) {
            timer.stop();
            submitExam();
        }
    }
}

// Sauvegarder l'état de pause dans la session
private ExamSession createSession() {
    ExamSession session = new ExamSession(...);
    // ... code existant ...

    // Ajouter le temps de pause? Optionnel
    return session;
}*/
}