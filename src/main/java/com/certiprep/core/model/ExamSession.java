package com.certiprep.core.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExamSession {

    private String sessionId;
    private String certificationId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private int totalQuestions;
    private int score;
    private boolean passed;
    private ExamMode mode;
    private List<UserAnswer> userAnswers;

    public ExamSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.startTime = LocalDateTime.now();
        this.userAnswers = new ArrayList<>();
    }

    public ExamSession(String certificationId, ExamMode mode, int totalQuestions, int durationMinutes) {
        this();
        this.certificationId = certificationId;
        this.mode = mode;
        this.totalQuestions = totalQuestions;
        this.durationMinutes = durationMinutes;
    }

    // Getters et Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCertificationId() {
        return certificationId;
    }

    public void setCertificationId(String certificationId) {
        this.certificationId = certificationId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public ExamMode getMode() {
        return mode;
    }

    public void setMode(ExamMode mode) {
        this.mode = mode;
    }

    public List<UserAnswer> getUserAnswers() {
        return userAnswers;
    }

    public void setUserAnswers(List<UserAnswer> userAnswers) {
        this.userAnswers = userAnswers;
    }

    public int getAnsweredCount() {
        return (int) userAnswers.stream().filter(a -> a.getSelectedAnswer() != -1).count();
    }

    public int getCorrectCount() {
        return (int) userAnswers.stream().filter(UserAnswer::isCorrect).count();
    }

    public double getPercentage() {
        return totalQuestions > 0 ? (getCorrectCount() * 100.0 / totalQuestions) : 0;
    }

    public long getDurationSeconds() {
        if (endTime == null) return 0;
        return java.time.Duration.between(startTime, endTime).getSeconds();
    }

    public enum ExamMode {
        EXAM, FREE, REVISION
    }
}