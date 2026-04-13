package com.certiprep.core.utils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public class LoggerUtil {

    private static final String LOG_DIR = "logs";
    private static boolean initialized = false;

    public static void initLogger() {
        if (initialized) return;

        try {
            // Créer le dossier logs
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Configurer le logger racine
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);

            // Supprimer les handlers existants
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            // Handler pour la console
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.INFO);
            consoleHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] %s: %s%n",
                            new java.util.Date(),
                            record.getLevel().getName(),
                            record.getMessage()
                    );
                }
            });
            rootLogger.addHandler(consoleHandler);

            // Handler pour le fichier
            FileHandler fileHandler = new FileHandler(LOG_DIR + "/certiprep.log", 10 * 1024 * 1024, 5, true);
            fileHandler.setLevel(Level.FINE);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("%s [%s] %s - %s%n",
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            record.getLevel().getName(),
                            record.getSourceClassName(),
                            record.getMessage()
                    );
                }
            });
            rootLogger.addHandler(fileHandler);

            // Handler pour les erreurs
            FileHandler errorHandler = new FileHandler(LOG_DIR + "/certiprep-error.log", 10 * 1024 * 1024, 5, true);
            errorHandler.setLevel(Level.WARNING);
            errorHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("%s [%s] %s - %s%n",
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            record.getLevel().getName(),
                            record.getSourceClassName(),
                            record.getMessage()
                    );
                }
            });
            rootLogger.addHandler(errorHandler);

            initialized = true;
            getLogger(LoggerUtil.class).info("Système de logging initialisé");

        } catch (IOException e) {
            System.err.println("Impossible d'initialiser les logs: " + e.getMessage());
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        if (!initialized) {
            initLogger();
        }
        return Logger.getLogger(clazz.getName());
    }

    public static void cleanOldLogs() {
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) return;

            long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;

            for (File file : logDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".log")) {
                    if (file.lastModified() < thirtyDaysAgo) {
                        file.delete();
                        getLogger(LoggerUtil.class).info("Log supprimé (ancien): " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur nettoyage logs: " + e.getMessage());
        }
    }

    public static long getLogsSize() {
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) return 0;

            long size = 0;
            for (File file : logDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".log")) {
                    size += file.length();
                }
            }
            return size;
        } catch (Exception e) {
            return 0;
        }
    }
}