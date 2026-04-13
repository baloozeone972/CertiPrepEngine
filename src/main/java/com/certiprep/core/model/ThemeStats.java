package com.certiprep.core.model;

public class ThemeStats {

    private String themeName;
    private int totalQuestions;
    private int correctAnswers;
    private int wrongAnswers;
    private int skippedAnswers;

    public ThemeStats(String themeName) {
        this.themeName = themeName;
        this.totalQuestions = 0;
        this.correctAnswers = 0;
        this.wrongAnswers = 0;
        this.skippedAnswers = 0;
    }

    public String getThemeName() { return themeName; }
    public void setThemeName(String themeName) { this.themeName = themeName; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public int getCorrectAnswers() { return correctAnswers; }
    public void setCorrectAnswers(int correctAnswers) { this.correctAnswers = correctAnswers; }

    public int getWrongAnswers() { return wrongAnswers; }
    public void setWrongAnswers(int wrongAnswers) { this.wrongAnswers = wrongAnswers; }

    public int getSkippedAnswers() { return skippedAnswers; }
    public void setSkippedAnswers(int skippedAnswers) { this.skippedAnswers = skippedAnswers; }

    public double getPercentage() {
        return totalQuestions > 0 ? (correctAnswers * 100.0 / totalQuestions) : 0;
    }

    public void addCorrect() {
        correctAnswers++;
        totalQuestions++;
    }

    public void addWrong() {
        wrongAnswers++;
        totalQuestions++;
    }

    public void addSkipped() {
        skippedAnswers++;
        totalQuestions++;
    }
}