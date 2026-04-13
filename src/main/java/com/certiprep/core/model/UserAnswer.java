package com.certiprep.core.model;

public class UserAnswer {

    private String questionId;
    private int selectedAnswer;  // -1 si non répondue
    private boolean isCorrect;
    private long responseTimeMs;
    private String theme;

    public UserAnswer() {}

    public UserAnswer(String questionId, int selectedAnswer, boolean isCorrect, long responseTimeMs, String theme) {
        this.questionId = questionId;
        this.selectedAnswer = selectedAnswer;
        this.isCorrect = isCorrect;
        this.responseTimeMs = responseTimeMs;
        this.theme = theme;
    }

    // Getters et Setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public int getSelectedAnswer() { return selectedAnswer; }
    public void setSelectedAnswer(int selectedAnswer) { this.selectedAnswer = selectedAnswer; }

    public boolean isCorrect() { return isCorrect; }
    public void setCorrect(boolean correct) { isCorrect = correct; }

    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}