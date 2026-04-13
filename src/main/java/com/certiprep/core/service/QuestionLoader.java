package com.certiprep.core.service;

import com.certiprep.core.model.Certification;
import com.certiprep.core.model.Question;
import com.certiprep.core.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.logging.Logger;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class QuestionLoader {

    private static final Logger logger = LoggerUtil.getLogger(QuestionLoader.class);
    private static QuestionLoader instance;
    private final ObjectMapper objectMapper;
    private Map<String, List<Question>> certificationQuestions;
    private Map<String, Certification> certifications;

    private QuestionLoader() {
        this.objectMapper = new ObjectMapper();
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
        try {
            // Charger Java 21 certification
            loadCertification("java21");

        } catch (Exception e) {
            logger.severe("Erreur lors du chargement des certifications");
        }
    }

    private void loadCertification(String certId) {
        try {
            // Charger la configuration
            String configPath = "/certifications/" + certId + "/config.json";
            InputStream configStream = getClass().getResourceAsStream(configPath);
            if (configStream == null) {
                logger.severe("Configuration non trouvée pour {}: {}");
                return;
            }

            Certification cert = objectMapper.readValue(configStream, Certification.class);
            certifications.put(certId, cert);
            logger.info("Certification {} chargée");

            // Charger les questions par thème
            List<Question> allQuestions = new ArrayList<>();

            // Liste des fichiers de questions attendus
            String[] questionFiles = {
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

            for (String fileName : questionFiles) {
                String questionsPath = "/certifications/" + certId + "/questions/" + fileName;
                InputStream questionsStream = getClass().getResourceAsStream(questionsPath);

                if (questionsStream != null) {
                    try {
                        List<Question> themeQuestions = objectMapper.readValue(questionsStream, new TypeReference<List<Question>>() {});
                        allQuestions.addAll(themeQuestions);
                        logger.info("Chargé {} questions depuis {}");
                    } catch (Exception e) {
                        logger.severe("Erreur lecture du fichier {}");
                    }
                } else {
                    logger.warning("Fichier de questions non trouvé: {}");
                }
            }

            certificationQuestions.put(certId, allQuestions);
            logger.info("Total {} questions chargées pour la certification {}");

        } catch (Exception e) {
            logger.severe("Erreur lors du chargement de la certification {}");
        }
    }

    public Certification getCertification(String certId) {
        Certification cert = certifications.get(certId);
        if (cert == null) {
            logger.warning("Certification {} non trouvée");
        }
        return cert;
    }

    public List<Certification> getAllCertifications() {
        return new ArrayList<>(certifications.values());
    }

    public List<Question> getQuestions(String certId) {
        List<Question> questions = certificationQuestions.getOrDefault(certId, Collections.emptyList());
        if (questions.isEmpty()) {
            logger.warning("Aucune question trouvée pour la certification {}");
        }
        return questions;
    }

    public List<Question> getQuestionsByTheme(String certId, String theme) {
        return getQuestions(certId).stream()
                .filter(q -> theme.equals(q.getTheme()))
                .collect(Collectors.toList());
    }

    public List<Question> getRandomQuestions(String certId, int count) {
        List<Question> allQuestions = getQuestions(certId);
        if (allQuestions.isEmpty()) {
            logger.severe("Impossible de générer des questions: aucune question disponible pour {}");
            return Collections.emptyList();
        }

        if (count > allQuestions.size()) {
            logger.warning("Nombre de questions demandé ({}) supérieur au disponible ({}), utilisation de toutes les questions");
            count = allQuestions.size();
        }

        List<Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, count);
    }

    public List<Question> getRandomQuestionsByThemes(String certId, Map<String, Integer> themeCounts) {
        List<Question> selected = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : themeCounts.entrySet()) {
            List<Question> themeQuestions = getQuestionsByTheme(certId, entry.getKey());
            if (!themeQuestions.isEmpty()) {
                List<Question> shuffled = new ArrayList<>(themeQuestions);
                Collections.shuffle(shuffled);
                int take = Math.min(entry.getValue(), shuffled.size());
                selected.addAll(shuffled.subList(0, take));
                logger.fine("Ajouté {} questions du thème {}");
            } else {
                logger.warning("Aucune question trouvée pour le thème {}");
            }
        }

        Collections.shuffle(selected);
        return selected;
    }

    public List<String> getThemes(String certId) {
        Certification cert = certifications.get(certId);
        if (cert == null) {
            return Collections.emptyList();
        }
        return cert.getThemes().stream()
                .map(Certification.ThemeInfo::getName)
                .collect(Collectors.toList());
    }

    public int getThemeQuestionCount(String certId, String theme) {
        return (int) getQuestionsByTheme(certId, theme).size();
    }

    public boolean reloadCertification(String certId) {
        certificationQuestions.remove(certId);
        certifications.remove(certId);
        loadCertification(certId);
        return certificationQuestions.containsKey(certId);
    }
}