package com.certiprep.core.service;

import com.certiprep.core.model.Certification;
import com.certiprep.core.model.Question;
import com.certiprep.core.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class QuestionLoader {

    private static final Logger logger = LoggerUtil.getLogger(QuestionLoader.class);
    private static QuestionLoader instance;
    private final ObjectMapper objectMapper;
    private Map<String, List<Question>> certificationQuestions;
    private Map<String, Certification> certifications;

    private QuestionLoader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.certificationQuestions = new HashMap<>();
        this.certifications = new HashMap<>();
        loadAllCertifications();
    }

    public static synchronized QuestionLoader getInstance() {
        if (instance == null) {
            instance = new QuestionLoader();
        }
        return instance;
    }

    private void loadAllCertifications() {
        logger.info("=== Démarrage du chargement dynamique des certifications ===");

        List<String> availableCerts = scanAvailableCertifications();
        logger.info("Certifications trouvées: " + availableCerts);

        for (String certId : availableCerts) {
            loadCertification(certId);
        }

        logger.info("=== Fin du chargement des certifications ===");
        logger.info("Certifications chargées: " + certifications.keySet());
        printStatus();
    }

    /**
     * Scanne dynamiquement le dossier /certifications/ pour trouver toutes les certifications disponibles
     */
    private List<String> scanAvailableCertifications() {
        List<String> certs = new ArrayList<>();

        try {
            // Scanner le dossier resources/certifications
            String resourcePath = "/certifications/";
            URL resourceUrl = getClass().getResource(resourcePath);

            if (resourceUrl != null) {
                // En environnement de développement (fichiers système)
                java.io.File file = new java.io.File(resourceUrl.toURI());
                if (file.exists() && file.isDirectory()) {
                    String[] directories = file.list();
                    if (directories != null) {
                        for (String dir : directories) {
                            // Vérifier que le dossier contient un config.json
                            String configPath = resourcePath + dir + "/config.json";
                            if (getClass().getResource(configPath) != null) {
                                certs.add(dir);
                                logger.fine("Certification trouvée (scan dossier): " + dir);
                            }
                        }
                    }
                }
            } else {
                // Fallback: scanner via classpath (pour JAR)
                scanCertificationsFromClasspath(certs);
            }
        } catch (Exception e) {
            logger.warning("Erreur lors du scan des certifications: " + e.getMessage());
            // Fallback sur une liste par défaut
            scanCertificationsFromClasspath(certs);
        }

        return certs;
    }

    /**
     * Scan via le classpath pour trouver les certifications
     */
    private void scanCertificationsFromClasspath(List<String> certs) {
        try {
            // Lister toutes les ressources commençant par /certifications/
            var enumeration = getClass().getClassLoader().getResources("certifications");

            while (enumeration.hasMoreElements()) {
                URL url = enumeration.nextElement();
                java.io.File file = new java.io.File(url.toURI());
                if (file.exists() && file.isDirectory()) {
                    for (String dir : file.list()) {
                        String configPath = "/certifications/" + dir + "/config.json";
                        if (getClass().getResource(configPath) != null) {
                            certs.add(dir);
                            logger.fine("Certification trouvée (scan classpath): " + dir);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Erreur scan classpath: " + e.getMessage());
        }

        // Si aucun résultat, tenter de charger les certifications connues via leur config.json
        if (certs.isEmpty()) {
            loadKnownCertificationsFromConfig(certs);
        }
    }

    /**
     * Tente de charger les certifications en testant l'existence des fichiers config.json
     */
    private void loadKnownCertificationsFromConfig(List<String> certs) {
        // Liste des noms potentiels à tester
        String[] possibleCerts = {
                "java21", "java17", "java11", "java8",
                "ocp17", "ocp21", "oca8",
                "spring6", "springboot3", "quarkus", "jakartaee",
                "android", "kotlin",
                "aws_ccp", "aws_saa", "aws_dev",
                "az900", "az204",
                "gcp_dl",
                "docker", "cka", "ckad", "cks", "podman",
                "terraform", "ansible", "puppet", "chef", "crossplane",
                "jenkins", "gitlab", "github_actions", "maven", "gradle",
                "prometheus", "grafana", "elk", "datadog", "newrelic",
                "devsecops", "sonarqube", "trivy", "snyk", "vault"
        };

        for (String certId : possibleCerts) {
            String configPath = "/certifications/" + certId + "/config.json";
            if (getClass().getResource(configPath) != null) {
                certs.add(certId);
                logger.fine("Certification trouvée (test config): " + certId);
            }
        }
    }

    private void loadCertification(String certId) {
        logger.info("----------------------------------------");
        logger.info("Chargement de la certification: " + certId);

        try {
            // Charger la configuration
            String configPath = "/certifications/" + certId + "/config.json";
            logger.fine("Chemin config: " + configPath);

            InputStream configStream = getClass().getResourceAsStream(configPath);
            if (configStream == null) {
                logger.warning("Configuration non trouvée pour " + certId);
                return;
            }

            Certification cert = objectMapper.readValue(configStream, Certification.class);
            certifications.put(certId, cert);
            logger.info("✓ Certification '" + cert.getName() + "' chargée");
            logger.info("  - Version: " + cert.getVersion());
            logger.info("  - Questions totales attendues: " + cert.getTotalQuestions());
            logger.info("  - Durée examen: " + cert.getExamDurationMinutes() + " min");
            logger.info("  - Seuil réussite: " + cert.getPassingScore() + "%");

            // Charger les questions dynamiquement
            List<Question> allQuestions = loadQuestionsDynamically(certId, cert);

            certificationQuestions.put(certId, allQuestions);

            int loadedCount = allQuestions.size();
            logger.info("  TOTAL: " + loadedCount + " / " + cert.getTotalQuestions() + " questions chargées");

            if (loadedCount < cert.getTotalQuestions()) {
                logger.warning("  ⚠ Attention: " + (cert.getTotalQuestions() - loadedCount) + " questions manquantes");
            }

        } catch (Exception e) {
            logger.severe("✗ Erreur lors du chargement de " + certId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Charge dynamiquement tous les fichiers JSON du dossier questions/
     */
    private List<Question> loadQuestionsDynamically(String certId, Certification cert) {
        List<Question> allQuestions = new ArrayList<>();

        try {
            // Scanner le dossier des questions
            String questionsDirPath = "/certifications/" + certId + "/questions/";
            URL dirUrl = getClass().getResource(questionsDirPath);

            List<String> jsonFiles = new ArrayList<>();

            if (dirUrl != null) {
                // En environnement de développement
                java.io.File dir = new java.io.File(dirUrl.toURI());
                if (dir.exists() && dir.isDirectory()) {
                    java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (java.io.File file : files) {
                            jsonFiles.add(file.getName());
                        }
                    }
                }
            }

            // Si aucun fichier trouvé via le scan, utiliser les thèmes de config.json
            if (jsonFiles.isEmpty()) {
                jsonFiles = getJsonFilesFromThemes(cert);
            }

            // Si toujours aucun fichier, scanner via classpath
            if (jsonFiles.isEmpty()) {
                jsonFiles = scanJsonFilesFromClasspath(certId);
            }

            logger.info("  Fichiers JSON trouvés: " + jsonFiles);

            for (String fileName : jsonFiles) {
                String questionsPath = questionsDirPath + fileName;
                InputStream questionsStream = getClass().getResourceAsStream(questionsPath);

                if (questionsStream != null) {
                    try {
                        List<Question> themeQuestions = objectMapper.readValue(questionsStream, new TypeReference<List<Question>>() {
                        });

                        // Déterminer le thème à partir du nom du fichier ou du contenu
                        String themeName = extractThemeName(fileName, cert);
                        for (Question q : themeQuestions) {
                            if (q.getTheme() == null || q.getTheme().isEmpty()) {
                                q.setTheme(themeName);
                            }
                        }

                        allQuestions.addAll(themeQuestions);
                        logger.info("    ✓ " + fileName + ": " + themeQuestions.size() + " questions");

                    } catch (Exception e) {
                        logger.severe("    ✗ Erreur lecture " + fileName + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.warning("Erreur scan dossier questions: " + e.getMessage());
            // Fallback: utiliser les thèmes de config.json
            allQuestions = loadQuestionsFromThemes(certId, cert);
        }

        return allQuestions;
    }

    /**
     * Récupère les noms de fichiers JSON à partir des thèmes dans config.json
     */
    private List<String> getJsonFilesFromThemes(Certification cert) {
        List<String> files = new ArrayList<>();
        if (cert.getThemes() != null) {
            for (Certification.ThemeInfo theme : cert.getThemes()) {
                String fileName = sanitizeFileName(theme.getName()) + ".json";
                files.add(fileName);
            }
        }
        return files;
    }

    /**
     * Scanne les fichiers JSON via le classpath
     */
    private List<String> scanJsonFilesFromClasspath(String certId) {
        List<String> files = new ArrayList<>();
        try {
            String questionsPath = "certifications/" + certId + "/questions/";
            var enumeration = getClass().getClassLoader().getResources(questionsPath);

            while (enumeration.hasMoreElements()) {
                URL url = enumeration.nextElement();
                java.io.File dir = new java.io.File(url.toURI());
                if (dir.exists() && dir.isDirectory()) {
                    java.io.File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
                    if (jsonFiles != null) {
                        for (java.io.File file : jsonFiles) {
                            files.add(file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Erreur scan classpath JSON: " + e.getMessage());
        }
        return files;
    }

    /**
     * Charge les questions en utilisant les thèmes de config.json
     */
    private List<Question> loadQuestionsFromThemes(String certId, Certification cert) {
        List<Question> allQuestions = new ArrayList<>();

        if (cert.getThemes() != null) {
            for (Certification.ThemeInfo theme : cert.getThemes()) {
                String fileName = sanitizeFileName(theme.getName()) + ".json";
                String questionsPath = "/certifications/" + certId + "/questions/" + fileName;
                InputStream questionsStream = getClass().getResourceAsStream(questionsPath);

                if (questionsStream != null) {
                    try {
                        List<Question> themeQuestions = objectMapper.readValue(questionsStream, new TypeReference<List<Question>>() {
                        });
                        for (Question q : themeQuestions) {
                            if (q.getTheme() == null || q.getTheme().isEmpty()) {
                                q.setTheme(theme.getName());
                            }
                        }
                        allQuestions.addAll(themeQuestions);
                        logger.info("    ✓ " + fileName + ": " + themeQuestions.size() + " questions");
                    } catch (Exception e) {
                        logger.warning("    ✗ Erreur lecture " + fileName + ": " + e.getMessage());
                    }
                } else {
                    logger.warning("    ✗ Fichier non trouvé: " + questionsPath);
                }
            }
        }

        return allQuestions;
    }

    /**
     * Extrait le nom du thème à partir du nom du fichier
     */
    private String extractThemeName(String fileName, Certification cert) {
        // Enlever l'extension .json
        String baseName = fileName.replace(".json", "");

        // Chercher dans les thèmes de la certification
        if (cert.getThemes() != null) {
            for (Certification.ThemeInfo theme : cert.getThemes()) {
                String sanitizedTheme = sanitizeFileName(theme.getName());
                if (baseName.equals(sanitizedTheme) || baseName.endsWith("_" + sanitizedTheme)) {
                    return theme.getName();
                }
            }
        }

        // Fallback: utiliser le nom du fichier
        return baseName.replace("_", " ");
    }

    /**
     * Nettoie un nom pour en faire un nom de fichier valide
     */
    private String sanitizeFileName(String name) {
        return name.toLowerCase()
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("ç", "c")
                .replace("ô", "o")
                .replace("î", "i")
                .replace("û", "u")
                .replace("'", "")
                .replace(" ", "_")
                .replace("-", "_")
                .replace("(", "")
                .replace(")", "")
                .replace("/", "_");
    }

    public Certification getCertification(String certId) {
        Certification cert = certifications.get(certId);
        if (cert == null) {
            logger.warning("Certification '" + certId + "' non trouvée");
            logger.info("Certifications disponibles: " + certifications.keySet());
        }
        return cert;
    }

    public List<Certification> getAllCertifications() {
        logger.info("Liste des certifications disponibles (" + certifications.size() + "):");
        for (Map.Entry<String, Certification> entry : certifications.entrySet()) {
            int qty = certificationQuestions.getOrDefault(entry.getKey(), Collections.emptyList()).size();
            logger.info("  - " + entry.getKey() + ": " + entry.getValue().getName() + " (" + qty + " questions)");
        }
        return new ArrayList<>(certifications.values());
    }

    public List<Question> getQuestions(String certId) {
        List<Question> questions = certificationQuestions.getOrDefault(certId, Collections.emptyList());
        if (questions.isEmpty()) {
            logger.warning("Aucune question trouvée pour la certification '" + certId + "'");
        } else {
            logger.fine("Récupération de " + questions.size() + " questions pour " + certId);
        }
        return questions;
    }

    public List<Question> getQuestionsByTheme(String certId, String theme) {
        List<Question> filtered = getQuestions(certId).stream()
                .filter(q -> theme.equals(q.getTheme()))
                .collect(Collectors.toList());
        logger.fine("Thème '" + theme + "' pour " + certId + ": " + filtered.size() + " questions");
        return filtered;
    }

    public List<Question> getRandomQuestions(String certId, int count) {
        List<Question> allQuestions = getQuestions(certId);
        if (allQuestions.isEmpty()) {
            logger.severe("Impossible de générer des questions: aucune question disponible pour '" + certId + "'");
            return Collections.emptyList();
        }

        if (count > allQuestions.size()) {
            logger.warning("Nombre de questions demandé (" + count + ") supérieur au disponible (" + allQuestions.size() + "), utilisation de toutes les questions");
            count = allQuestions.size();
        }

        List<Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        List<Question> selected = shuffled.subList(0, count);
        logger.info("Génération de " + count + " questions aléatoires pour " + certId);
        return selected;
    }

    public List<Question> getRandomQuestionsByThemes(String certId, Map<String, Integer> themeCounts) {
        List<Question> selected = new ArrayList<>();
        logger.info("Génération de questions par thèmes pour " + certId + ": " + themeCounts);

        for (Map.Entry<String, Integer> entry : themeCounts.entrySet()) {
            String theme = entry.getKey();
            int requested = entry.getValue();

            List<Question> themeQuestions = getQuestionsByTheme(certId, theme);
            if (!themeQuestions.isEmpty()) {
                List<Question> shuffled = new ArrayList<>(themeQuestions);
                Collections.shuffle(shuffled);
                int take = Math.min(requested, shuffled.size());
                selected.addAll(shuffled.subList(0, take));
                logger.fine("  - " + theme + ": " + take + "/" + requested + " questions ajoutées");
            } else {
                logger.warning("  - " + theme + ": AUCUNE question trouvée!");
            }
        }

        Collections.shuffle(selected);
        logger.info("Total généré: " + selected.size() + " questions");
        return selected;
    }

    public List<String> getThemes(String certId) {
        Certification cert = certifications.get(certId);
        if (cert == null) {
            logger.warning("Certification '" + certId + "' non trouvée pour lister les thèmes");
            return Collections.emptyList();
        }
        List<String> themes = cert.getThemes().stream()
                .map(Certification.ThemeInfo::getName)
                .collect(Collectors.toList());
        logger.fine("Thèmes pour " + certId + ": " + themes);
        return themes;
    }

    public int getThemeQuestionCount(String certId, String theme) {
        int count = (int) getQuestionsByTheme(certId, theme).size();
        logger.fine("Thème '" + theme + "' pour " + certId + ": " + count + " questions disponibles");
        return count;
    }

    public boolean reloadCertification(String certId) {
        logger.info("Rechargement de la certification: " + certId);
        certificationQuestions.remove(certId);
        certifications.remove(certId);
        loadCertification(certId);
        boolean success = certificationQuestions.containsKey(certId);
        if (success) {
            logger.info("✓ Rechargement réussi pour " + certId);
        } else {
            logger.severe("✗ Échec du rechargement pour " + certId);
        }
        return success;
    }

    public void reloadAllCertifications() {
        logger.info("Rechargement complet de toutes les certifications");
        certificationQuestions.clear();
        certifications.clear();
        loadAllCertifications();
    }

    public void printStatus() {
        logger.info("=== STATUT DU QuestionLoader ===");
        logger.info("Certifications chargées: " + certifications.size());
        for (Map.Entry<String, List<Question>> entry : certificationQuestions.entrySet()) {
            logger.info("  - " + entry.getKey() + ": " + entry.getValue().size() + " questions");
        }
        logger.info("================================");
    }
}