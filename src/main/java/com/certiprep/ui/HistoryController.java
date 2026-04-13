package com.certiprep.ui;

import com.certiprep.core.model.ExamSession;
import com.certiprep.core.service.DatabaseService;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.service.QuestionLoader;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.logging.Logger;


import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryController {

    private static final Logger logger = LoggerUtil.getLogger(HistoryController.class);

    @FXML private ComboBox<String> certFilterCombo;
    @FXML private ComboBox<String> modeFilterCombo;
    @FXML private Button refreshBtn;
    @FXML private Button deleteAllBtn;
    @FXML private TableView<ExamSession> historyTable;
    @FXML private TableColumn<ExamSession, String> dateCol;
    @FXML private TableColumn<ExamSession, String> certCol;
    @FXML private TableColumn<ExamSession, String> modeCol;
    @FXML private TableColumn<ExamSession, Integer> scoreCol;
    @FXML private TableColumn<ExamSession, Double> percentCol;
    @FXML private TableColumn<ExamSession, String> statusCol;
    @FXML private TableColumn<ExamSession, String> durationCol;
    @FXML private Button viewDetailsBtn;
    @FXML private Button deleteSessionBtn;
    @FXML private Button closeHistoryBtn;

    private ThemeManager themeManager;
    private I18nService i18nService;
    private DatabaseService databaseService;
    private ObservableList<ExamSession> sessionsList;
    private List<ExamSession> allSessions;

    public void init(ThemeManager themeManager, I18nService i18nService, DatabaseService databaseService) {
        this.themeManager = themeManager;
        this.i18nService = i18nService;
        this.databaseService = databaseService;

        setupTable();
        loadFilters();
        loadHistory();

        refreshBtn.setOnAction(e -> loadHistory());
        deleteAllBtn.setOnAction(e -> deleteAllHistory());
        viewDetailsBtn.setOnAction(e -> viewDetails());
        deleteSessionBtn.setOnAction(e -> deleteSelectedSession());
        closeHistoryBtn.setOnAction(e -> close());

        certFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> filterHistory());
        modeFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> filterHistory());
    }

    private void setupTable() {
        dateCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                )
        );
        certCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCertificationId())
        );
        modeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getMode().name())
        );
        scoreCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getScore()).asObject()
        );
        percentCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getPercentage()).asObject()
        );
        statusCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().isPassed() ? "✓ Réussi" : "✗ Échec"
                )
        );
        durationCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        formatDuration(cellData.getValue().getDurationSeconds())
                )
        );

        percentCol.setCellFactory(col -> new TableCell<ExamSession, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", item));
                }
            }
        });

        statusCol.setCellFactory(col -> new TableCell<ExamSession, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if (item.contains("Réussi")) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        sessionsList = FXCollections.observableArrayList();
        historyTable.setItems(sessionsList);
    }

    private void loadFilters() {
        // Certifications
        certFilterCombo.getItems().add("Toutes");
        QuestionLoader.getInstance().getAllCertifications().forEach(cert ->
                certFilterCombo.getItems().add(cert.getId())
        );
        certFilterCombo.getSelectionModel().select(0);

        // Modes
        modeFilterCombo.getItems().addAll("Tous", "EXAM", "FREE", "REVISION");
        modeFilterCombo.getSelectionModel().select(0);
    }

    private void loadHistory() {
        String certId = certFilterCombo.getValue();
        if ("Toutes".equals(certId)) {
            // Charger toutes les certifications
            allSessions = databaseService.getSessions(null);
        } else {
            allSessions = databaseService.getSessions(certId);
        }
        filterHistory();
    }

    private void filterHistory() {
        String modeFilter = modeFilterCombo.getValue();
        List<ExamSession> filtered = allSessions;

        if (modeFilter != null && !"Tous".equals(modeFilter)) {
            filtered = filtered.stream()
                    .filter(s -> s.getMode().name().equals(modeFilter))
                    .collect(Collectors.toList());
        }

        sessionsList.setAll(filtered);
    }

    private void viewDetails() {
        ExamSession selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Information", "Veuillez sélectionner une session");
            return;
        }

        // TODO: Afficher les détails de la session
        showAlert("Détails", "Session du " + selected.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                "\nScore: " + selected.getScore() + "/" + selected.getTotalQuestions() +
                "\nPourcentage: " + String.format("%.1f", selected.getPercentage()) + "%" +
                "\nStatut: " + (selected.isPassed() ? "RÉUSSI" : "ÉCHEC"));
    }

    private void deleteSelectedSession() {
        ExamSession selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Information", "Veuillez sélectionner une session");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer la session");
        confirm.setContentText("Êtes-vous sûr de vouloir supprimer cette session ?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            databaseService.deleteSession(selected.getSessionId());
            loadHistory();
        }
    }

    private void deleteAllHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer tout l'historique");
        confirm.setContentText("Êtes-vous sûr de vouloir supprimer TOUTES les sessions ? Cette action est irréversible.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            for (ExamSession session : allSessions) {
                databaseService.deleteSession(session.getSessionId());
            }
            loadHistory();
        }
    }

    private void close() {
        Stage stage = (Stage) closeHistoryBtn.getScene().getWindow();
        stage.close();
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}