package com.certiprep.ui;

import com.certiprep.core.model.Question;
import com.certiprep.core.service.QuestionLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

public class ImportQuestionsController {

    private static final Logger logger = Logger.getLogger(ImportQuestionsController.class.getName());
    private static final String QUESTIONS_DIR = "src/main/resources/certifications/java21/questions/";
    private static final String[] TARGET_FILES = {
            "01_fondamentaux.json",
            "02_types_donnees.json",
            "03_poo.json",
            "04_collections.json",
            "05_exceptions.json",
            "06_io_nio.json",
            "07_multithreading.json",
            "08_lambda_streams.json",
            "09_modules.json",
            "10_nouveautes.json"
    };
    private final ObjectMapper objectMapper = new ObjectMapper();
    @FXML
    private TextField themeField;
    @FXML
    private ComboBox<String> targetFileCombo;
    @FXML
    private TextArea previewArea;
    @FXML
    private Button selectFileBtn;
    @FXML
    private Button importBtn;
    @FXML
    private Button cancelBtn;
    @FXML
    private Label statusLabel;
    private File selectedJsonFile;
    private List<Question> importedQuestions;

    @FXML
    public void initialize() {
        targetFileCombo.getItems().addAll(TARGET_FILES);
        targetFileCombo.getSelectionModel().selectFirst();

        selectFileBtn.setOnAction(e -> selectFile());
        importBtn.setOnAction(e -> importQuestions());
        cancelBtn.setOnAction(e -> close());
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier JSON de questions");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json")
        );

        selectedJsonFile = fileChooser.showOpenDialog(selectFileBtn.getScene().getWindow());
        if (selectedJsonFile != null) {
            try {
                importedQuestions = objectMapper.readValue(selectedJsonFile, new TypeReference<List<Question>>() {
                });
                previewArea.setText(String.format("Fichier: %s\n%d questions trouvées\n\nExemple:\n%s",
                        selectedJsonFile.getName(),
                        importedQuestions.size(),
                        importedQuestions.get(0).getQuestion()
                ));
                statusLabel.setText("Fichier chargé: " + importedQuestions.size() + " questions");
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (Exception e) {
                statusLabel.setText("Erreur: Fichier JSON invalide");
                statusLabel.setStyle("-fx-text-fill: red;");
                logger.severe("Erreur lecture JSON: " + e.getMessage());
            }
        }
    }

    private void importQuestions() {
        if (importedQuestions == null || importedQuestions.isEmpty()) {
            statusLabel.setText("Aucune question à importer");
            return;
        }

        String theme = themeField.getText().trim();
        if (theme.isEmpty()) {
            theme = "Importé";
        }

        // Mettre à jour le thème des questions importées
        for (Question q : importedQuestions) {
            q.setTheme(theme);
            q.setThemeLabel(theme);
        }

        String targetFile = targetFileCombo.getValue();
        Path targetPath = Paths.get(QUESTIONS_DIR + targetFile);

        try {
            // Lire les questions existantes
            List<Question> existingQuestions;
            if (Files.exists(targetPath)) {
                existingQuestions = objectMapper.readValue(targetPath.toFile(), new TypeReference<List<Question>>() {
                });
            } else {
                existingQuestions = new java.util.ArrayList<>();
            }

            // Ajouter les nouvelles questions
            int startId = existingQuestions.size() + 1;
            for (int i = 0; i < importedQuestions.size(); i++) {
                Question q = importedQuestions.get(i);
                q.setId(String.format("IMP-%03d-%03d", System.currentTimeMillis() % 1000, startId + i));
                existingQuestions.add(q);
            }

            // Sauvegarder
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetPath.toFile(), existingQuestions);

            statusLabel.setText(String.format("Importé: %d questions ajoutées à %s", importedQuestions.size(), targetFile));
            statusLabel.setStyle("-fx-text-fill: green;");

            // Recharger les questions
            QuestionLoader.getInstance().reloadCertification("java21");

            // Option: proposer de fermer
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Succès");
            alert.setHeaderText(null);
            alert.setContentText(importedQuestions.size() + " questions importées avec succès.\nRedémarrez l'application pour voir les changements.");
            alert.showAndWait();

        } catch (Exception e) {
            statusLabel.setText("Erreur lors de l'import: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            logger.severe("Erreur import: " + e.getMessage());
        }
    }

    private void close() {
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }
}