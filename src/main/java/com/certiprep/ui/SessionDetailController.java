package com.certiprep.ui;

import com.certiprep.core.model.ExamSession;
import com.certiprep.core.model.Question;
import com.certiprep.core.model.UserAnswer;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.utils.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SessionDetailController {

    private static final Logger logger = Logger.getLogger(SessionDetailController.class.getName());

    @FXML
    private Label dateLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label durationLabel;
    @FXML
    private ComboBox<Integer> questionCombo;
    @FXML
    private Button prevBtn;
    @FXML
    private Button nextBtn;
    @FXML
    private Label questionCounter;
    @FXML
    private Text questionText;
    @FXML
    private VBox optionsContainer;
    @FXML
    private Text userAnswerText;
    @FXML
    private Text correctAnswerText;
    @FXML
    private Text explanationText;
    @FXML
    private FlowPane paletteContainer;
    @FXML
    private Button closeBtn;

    private ExamSession session;
    private List<Question> questions;
    private ThemeManager themeManager;
    private I18nService i18nService;
    private int currentIndex;
    private List<RadioButton> optionButtons;

    public void init(ExamSession session, List<Question> questions, ThemeManager themeManager, I18nService i18nService) {
        this.session = session;
        this.questions = questions;
        this.themeManager = themeManager;
        this.i18nService = i18nService;

        displaySessionInfo();
        setupQuestionCombo();
        displayQuestion(0);
        updatePalette();
    }

    private void displaySessionInfo() {
        dateLabel.setText(session.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        scoreLabel.setText(session.getScore() + " / " + session.getTotalQuestions() +
                " (" + String.format("%.1f", session.getPercentage()) + "%)");

        long hours = session.getDurationSeconds() / 3600;
        long minutes = (session.getDurationSeconds() % 3600) / 60;
        long seconds = session.getDurationSeconds() % 60;
        durationLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void setupQuestionCombo() {
        for (int i = 1; i <= questions.size(); i++) {
            questionCombo.getItems().add(i);
        }
        questionCombo.setOnAction(e -> {
            if (questionCombo.getValue() != null) {
                displayQuestion(questionCombo.getValue() - 1);
            }
        });
    }

    private void displayQuestion(int index) {
        currentIndex = index;
        Question q = questions.get(index);
        questionText.setText(q.getQuestion());

        optionsContainer.getChildren().clear();
        optionButtons = new ArrayList<>();
        ToggleGroup group = new ToggleGroup();

        // Trouver la réponse de l'utilisateur
        UserAnswer userAnswer = session.getUserAnswers().stream()
                .filter(a -> a.getQuestionId().equals(q.getId()))
                .findFirst()
                .orElse(null);

        for (int i = 0; i < q.getOptions().size(); i++) {
            RadioButton rb = new RadioButton(q.getOptions().get(i));
            rb.setToggleGroup(group);
            rb.setDisable(true);
            rb.setWrapText(true);
            rb.setPrefWidth(700);

            // Colorer selon correct/incorrect
            if (i == q.getCorrect()) {
                rb.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            }
            if (userAnswer != null && userAnswer.getSelectedAnswer() == i) {
                rb.setSelected(true);
                if (i != q.getCorrect()) {
                    rb.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }

            optionButtons.add(rb);
            optionsContainer.getChildren().add(rb);
        }

        // Afficher les résultats
        if (userAnswer != null) {
            boolean isCorrect = userAnswer.getSelectedAnswer() == q.getCorrect();
            userAnswerText.setText("Votre réponse: " + (userAnswer.getSelectedAnswer() >= 0 ?
                    q.getOptions().get(userAnswer.getSelectedAnswer()) : "Non répondue"));
            userAnswerText.setStyle(isCorrect ? "-fx-fill: green;" : "-fx-fill: red; -fx-font-weight: bold;");

            correctAnswerText.setText("Réponse correcte: " + q.getCorrectAnswer());
            explanationText.setText(q.getExplanation());
        }

        questionCounter.setText((index + 1) + " / " + questions.size());
        questionCombo.setValue(index + 1);
        updatePalette();
    }

    private void updatePalette() {
        paletteContainer.getChildren().clear();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            UserAnswer userAnswer = session.getUserAnswers().stream()
                    .filter(a -> a.getQuestionId().equals(q.getId()))
                    .findFirst()
                    .orElse(null);

            Button btn = new Button(String.valueOf(i + 1));
            btn.setPrefWidth(35);
            btn.setPrefHeight(35);
            btn.getStyleClass().add("palette-btn");

            boolean isCorrect = userAnswer != null && userAnswer.getSelectedAnswer() == q.getCorrect();
            boolean isAnswered = userAnswer != null && userAnswer.getSelectedAnswer() != -1;

            if (isCorrect) {
                btn.getStyleClass().add("palette-answered");
            } else if (isAnswered) {
                btn.getStyleClass().add("palette-marked");
            } else {
                btn.getStyleClass().add("palette-unanswered");
            }

            if (i == currentIndex) {
                btn.getStyleClass().add("palette-current");
            }

            final int index = i;
            btn.setOnAction(e -> displayQuestion(index));
            paletteContainer.getChildren().add(btn);
        }
    }

    @FXML
    private void previousQuestion() {
        if (currentIndex > 0) {
            displayQuestion(currentIndex - 1);
        }
    }

    @FXML
    private void nextQuestion() {
        if (currentIndex < questions.size() - 1) {
            displayQuestion(currentIndex + 1);
        }
    }

    @FXML
    private void close() {
        Stage stage = (Stage) closeBtn.getScene().getWindow();
        stage.close();
    }
}