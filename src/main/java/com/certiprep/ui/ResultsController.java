package com.certiprep.ui;

import com.certiprep.core.model.*;
import com.certiprep.core.service.*;
import com.certiprep.core.utils.ThemeManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class ResultsController {

    private static final Logger logger = Logger.getLogger(ResultsController.class.getName());

    @FXML private Text resultTitle;
    @FXML private Text scoreText;
    @FXML private Text percentageText;
    @FXML private Text statusText;
    @FXML private Text timeText;
    @FXML private BarChart<String, Number> themeChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private TableView<ThemeStats> themeTable;
    @FXML private TableColumn<ThemeStats, String> themeCol;
    @FXML private TableColumn<ThemeStats, Integer> correctCol;
    @FXML private TableColumn<ThemeStats, Integer> totalCol;
    @FXML private TableColumn<ThemeStats, Double> percentCol;
    @FXML private Button exportPdfBtn;
    @FXML private Button reviewWrongBtn;
    @FXML private Button viewDetailsBtn;
    @FXML private Button newExamBtn;
    @FXML private Button closeBtn;

    private ExamSession session;
    private List<Question> questions;
    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private List<ThemeStats> themeStats;

    public void init(ExamSession session, List<Question> questions, ThemeManager themeManager,
                     I18nService i18nService, DatabaseService databaseService) {
        this.session = session;
        this.questions = questions;
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;

        logger.info("Initialisation des résultats pour session: " + session.getSessionId());

        // Calculer les statistiques
        ScoringService scoringService = ScoringService.getInstance();
        themeStats = scoringService.calculateThemeStats(session, questions);

        displayResults();
        setupChart();
        setupTable();

        // Sauvegarder la session
        databaseService.saveSession(session);
    }

    private void displayResults() {
        int total = session.getTotalQuestions();
        int score = session.getScore();
        double percentage = session.getPercentage();

        scoreText.setText(score + " / " + total);
        percentageText.setText(String.format("%.1f%%", percentage));

        if (session.isPassed()) {
            statusText.setText("RÉUSSI ✓");
            statusText.setStyle("-fx-fill: green; -fx-font-weight: bold;");
        } else {
            statusText.setText("ÉCHEC ✗");
            statusText.setStyle("-fx-fill: red; -fx-font-weight: bold;");
        }

        long hours = session.getDurationSeconds() / 3600;
        long minutes = (session.getDurationSeconds() % 3600) / 60;
        long seconds = session.getDurationSeconds() % 60;
        timeText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void setupChart() {
        if (themeChart == null) {
            logger.warning("themeChart est null");
            return;
        }

        themeChart.setTitle("Résultats par thème");
        if (xAxis != null) xAxis.setLabel("Thème");
        if (yAxis != null) yAxis.setLabel("Pourcentage (%)");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Réussite (%)");

        for (ThemeStats stats : themeStats) {
            series.getData().add(new XYChart.Data<>(stats.getThemeName(), stats.getPercentage()));
        }

        themeChart.getData().clear();
        themeChart.getData().add(series);

        // Ajouter des couleurs personnalisées
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getYValue().doubleValue() >= 70) {
                data.getNode().setStyle("-fx-bar-fill: #27ae60;");
            } else if (data.getYValue().doubleValue() >= 50) {
                data.getNode().setStyle("-fx-bar-fill: #f39c12;");
            } else {
                data.getNode().setStyle("-fx-bar-fill: #e74c3c;");
            }
        }
    }

    private void setupTable() {
        themeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getThemeName()));
        correctCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCorrectAnswers()).asObject());
        totalCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getTotalQuestions()).asObject());
        percentCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getPercentage()).asObject());

        percentCol.setCellFactory(col -> new TableCell<ThemeStats, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", item));
                    if (item >= 70) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if (item >= 50) {
                        setStyle("-fx-text-fill: orange;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        themeTable.setItems(FXCollections.observableArrayList(themeStats));
    }

    @FXML
    private void exportPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter PDF");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf")
        );
        fileChooser.setInitialFileName("certiprep_results_" + System.currentTimeMillis() + ".pdf");

        File file = fileChooser.showSaveDialog(exportPdfBtn.getScene().getWindow());
        if (file != null) {
            ScoringService scoringService = ScoringService.getInstance();
            List<Question> wrongQuestions = scoringService.getWrongQuestions(session, questions);

            PdfExportService pdfService = PdfExportService.getInstance();
            boolean success = pdfService.exportDetailedResults(session, themeStats, wrongQuestions, questions, file.getAbsolutePath());

            if (success) {
                showAlert("Succès", "PDF exporté avec succès");
            } else {
                showAlert("Erreur", "Erreur lors de l'export PDF");
            }
        }
    }

    @FXML
    private void reviewWrongQuestions() {
        ScoringService scoringService = ScoringService.getInstance();
        List<Question> wrongQuestions = scoringService.getWrongQuestions(session, questions);

        if (wrongQuestions.isEmpty()) {
            showAlert("Félicitations", "Aucune erreur à réviser !");
            return;
        }

        // Ouvrir le mode révision avec uniquement les questions erronées
        openRevisionMode(wrongQuestions, "Révision des erreurs");
    }

    @FXML
    private void viewDetailedSession() {
        // Ouvrir la vue détaillée de la session
        openSessionDetail();
    }

    private void openRevisionMode(List<Question> questionsToReview, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/revision.fxml"));
            Scene scene = new Scene(loader.load(), 900, 700);
            themeManager.applyTheme(scene);

            RevisionController controller = loader.getController();
            controller.initWithQuestions(questionsToReview, title, themeManager, i18nService);

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            logger.severe("Erreur ouverture révision: " + e.getMessage());
            showAlert("Erreur", "Impossible d'ouvrir le mode révision");
        }
    }

    private void openSessionDetail() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/session_detail.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 700);
            themeManager.applyTheme(scene);

            SessionDetailController controller = loader.getController();
            controller.init(session, questions, themeManager, i18nService);

            Stage stage = new Stage();
            stage.setTitle("Détail de la session");
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            logger.severe("Erreur ouverture détail session: " + e.getMessage());
            showAlert("Erreur", "Impossible d'ouvrir le détail de la session");
        }
    }

    @FXML
    private void newExam() {
        close();
    }

    @FXML
    private void close() {
        Stage stage = (Stage) closeBtn.getScene().getWindow();
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