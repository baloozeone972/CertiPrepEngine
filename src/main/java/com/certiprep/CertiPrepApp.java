package com.certiprep;

import com.certiprep.core.service.DatabaseService;
import com.certiprep.core.service.I18nService;
import com.certiprep.core.utils.LogUtils;
import com.certiprep.core.utils.LoggerUtil;
import com.certiprep.core.utils.ThemeManager;
import com.certiprep.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.util.logging.Logger;


import java.util.Objects;

public class CertiPrepApp extends Application {

    private static final Logger logger = LoggerUtil.getLogger(CertiPrepApp.class);
    private static Stage primaryStage;
    private static ThemeManager themeManager;
    private static I18nService i18nService;
    private static DatabaseService databaseService;

    static {
        // Initialisation du système de logs au chargement de la classe
        LogUtils.initLogDirectory();
        LogUtils.cleanOldLogs();
    }

    @Override
    public void start(Stage stage) {
        try {
            logger.info("Démarrage de CertiPrep Engine");
            logger.info("Java version: {}"+ System.getProperty("java.version"));
            logger.info("OS: {} {}"+ System.getProperty("os.name")+System.getProperty("os.version"));

            primaryStage = stage;

            // Initialisation des services
            logger.fine("Initialisation des services...");
            themeManager = new ThemeManager();
            i18nService = I18nService.getInstance();
            databaseService = DatabaseService.getInstance();
            databaseService.initializeDatabase();

            // Chargement de l'interface principale
            logger.fine("Chargement de l'interface utilisateur...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            loader.setResources(i18nService.getBundle());
            Scene scene = new Scene(loader.load(), 1200, 700);

            // Application du thème
            themeManager.applyTheme(scene);

            stage.setTitle("CertiPrep Engine - Java Certification Prep");
            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/logo.png")));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warning("Icône non trouvée, poursuite sans icône");
            }
            stage.setScene(scene);
            stage.setMinWidth(1000);
            stage.setMinHeight(600);
            stage.show();

            // Récupération du contrôleur
            MainController controller = loader.getController();
            controller.init(themeManager, i18nService, databaseService);

            logger.info("Application démarrée avec succès");

            // Log de la taille des logs
            long logsSize = LogUtils.getLogsSize();
            if (logsSize > 100 * 1024 * 1024) { // Plus de 100MB
                logger.warning("La taille des logs dépasse 100MB: {} MB"+ logsSize / (1024 * 1024));
            }

        } catch (Exception e) {
            logger.severe("Erreur au démarrage de l'application"+ e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Arrêt de CertiPrep Engine");
        logger.info("Taille totale des logs: {} KB"+ LogUtils.getLogsSize() / 1024);

        if (databaseService != null) {
            databaseService.close();
        }
        super.stop();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static ThemeManager getThemeManager() {
        return themeManager;
    }

    public static I18nService getI18nService() {
        return i18nService;
    }

    public static DatabaseService getDatabaseService() {
        return databaseService;
    }

    public static void main(String[] args) {
        launch(args);
    }
}