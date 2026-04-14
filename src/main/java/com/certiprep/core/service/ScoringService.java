package com.certiprep.core.service;

import com.certiprep.core.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScoringService {

    private static ScoringService instance;

    private ScoringService() {
    }

    public static synchronized ScoringService getInstance() {
        if (instance == null) {
            instance = new ScoringService();
        }
        return instance;
    }

    public void calculateScore(ExamSession session, List<Question> questions) {
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        int correctCount = 0;

        for (UserAnswer answer : session.getUserAnswers()) {
            Question q = questionMap.get(answer.getQuestionId());
            boolean isCorrect = (q != null && answer.getSelectedAnswer() == q.getCorrect());
            answer.setCorrect(isCorrect);
            if (isCorrect) {
                correctCount++;
            }
        }

        session.setScore(correctCount);

        // Calcul du pourcentage
        int totalQuestions = session.getTotalQuestions();
        double percentage = totalQuestions > 0 ? (correctCount * 100.0 / totalQuestions) : 0;
        session.setPassed(percentage >= getPassingThreshold(session.getCertificationId()));
    }

    private double getPassingThreshold(String certificationId) {
        Certification cert = QuestionLoader.getInstance().getCertification(certificationId);
        return cert != null ? cert.getPassingScore() : 68;
    }

    public List<ThemeStats> calculateThemeStats(ExamSession session, List<Question> questions) {
        Map<String, ThemeStats> statsMap = new HashMap<>();
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        for (UserAnswer answer : session.getUserAnswers()) {
            Question q = questionMap.get(answer.getQuestionId());
            if (q == null) continue;

            String theme = q.getTheme();
            ThemeStats stats = statsMap.computeIfAbsent(theme, ThemeStats::new);

            if (answer.getSelectedAnswer() == -1) {
                stats.addSkipped();
            } else if (answer.isCorrect()) {
                stats.addCorrect();
            } else {
                stats.addWrong();
            }
        }

        return new ArrayList<>(statsMap.values());
    }

    public List<Question> getWrongQuestions(ExamSession session, List<Question> questions) {
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Question> wrongQuestions = new ArrayList<>();

        for (UserAnswer answer : session.getUserAnswers()) {
            if (!answer.isCorrect() && answer.getSelectedAnswer() != -1) {
                Question q = questionMap.get(answer.getQuestionId());
                if (q != null) {
                    wrongQuestions.add(q);
                }
            }
        }

        return wrongQuestions;
    }

    public List<Question> getSkippedQuestions(ExamSession session, List<Question> questions) {
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Question> skippedQuestions = new ArrayList<>();

        for (UserAnswer answer : session.getUserAnswers()) {
            if (answer.getSelectedAnswer() == -1) {
                Question q = questionMap.get(answer.getQuestionId());
                if (q != null) {
                    skippedQuestions.add(q);
                }
            }
        }

        return skippedQuestions;
    }

    public Map<String, Double> getDifficultyAnalysis(ExamSession session, List<Question> questions) {
        Map<String, Integer> difficultyCorrect = new HashMap<>();
        Map<String, Integer> difficultyTotal = new HashMap<>();
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        for (UserAnswer answer : session.getUserAnswers()) {
            Question q = questionMap.get(answer.getQuestionId());
            if (q == null || answer.getSelectedAnswer() == -1) continue;

            String difficulty = q.getDifficulty();
            difficultyTotal.put(difficulty, difficultyTotal.getOrDefault(difficulty, 0) + 1);
            if (answer.isCorrect()) {
                difficultyCorrect.put(difficulty, difficultyCorrect.getOrDefault(difficulty, 0) + 1);
            }
        }

        Map<String, Double> percentages = new HashMap<>();
        for (String diff : difficultyTotal.keySet()) {
            int correct = difficultyCorrect.getOrDefault(diff, 0);
            int total = difficultyTotal.get(diff);
            percentages.put(diff, total > 0 ? (correct * 100.0 / total) : 0);
        }

        return percentages;
    }
}