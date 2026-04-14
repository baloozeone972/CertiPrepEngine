package com.certiprep.core.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Utilitaire de gestion des logs
 */
public class LogUtils {

    private static final Logger logger = LoggerUtil.getLogger(LogUtils.class);
    private static final String LOG_DIR = "logs";

    /**
     * Initialise le dossier de logs
     */
    public static void initLogDirectory() {
        try {
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
                logger.info("Dossier de logs créé: {}" + LOG_DIR);
            }
        } catch (Exception e) {
            System.err.println("Impossible de créer le dossier de logs: " + e.getMessage());
        }
    }

    /**
     * Nettoie les logs de plus de 30 jours
     */
    public static void cleanOldLogs() {
        try {
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                return;
            }

            long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;

            Files.list(logPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".log") || path.toString().endsWith(".gz"))
                    .forEach(path -> {
                        try {
                            if (Files.getLastModifiedTime(path).toMillis() < thirtyDaysAgo) {
                                Files.delete(path);
                                logger.info("Log supprimé (ancien): {}" + path.getFileName());
                            }
                        } catch (Exception e) {
                            logger.warning("Impossible de supprimer le log: {}" + path);
                        }
                    });
        } catch (Exception e) {
            logger.warning("Erreur lors du nettoyage des logs");
        }
    }

    /**
     * Crée un fichier de rapport de session
     */
    public static void createSessionReport(String sessionId, String content) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s/session_%s_%s.txt", LOG_DIR, sessionId, timestamp);
            Path filePath = Paths.get(filename);
            Files.writeString(filePath, content);
            logger.info("Rapport de session créé: {}" + filename);
        } catch (Exception e) {
            logger.severe("Erreur lors de la création du rapport de session");
        }
    }

    /**
     * Obtient la taille totale des logs
     */
    public static long getLogsSize() {
        try {
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                return 0;
            }

            return Files.list(logPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Supprime tous les logs
     */
    public static void clearLogs() {
        try {
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                return;
            }

            Files.list(logPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.info("Log supprimé: {}" + path.getFileName());
                        } catch (Exception e) {
                            logger.warning("Impossible de supprimer le log: {}" + path);
                        }
                    });
        } catch (Exception e) {
            logger.severe("Erreur lors de la suppression des logs");
        }
    }
}