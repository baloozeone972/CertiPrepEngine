package com.certiprep.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class Question {

    @JsonProperty("id")
    private String id;

    @JsonProperty("theme")
    private String theme;

    @JsonProperty("theme_label")
    private String themeLabel;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("question")
    private String question;

    @JsonProperty("options")
    private List<String> options;

    @JsonProperty("correct")
    private int correct;

    @JsonProperty("explanation")
    private String explanation;

    // Constructeurs
    public Question() {
    }

    public Question(String id, String theme, String themeLabel, String difficulty,
                    String question, List<String> options, int correct, String explanation) {
        this.id = id;
        this.theme = theme;
        this.themeLabel = themeLabel;
        this.difficulty = difficulty;
        this.question = question;
        this.options = options;
        this.correct = correct;
        this.explanation = explanation;
    }

    // Getters et Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getThemeLabel() {
        return themeLabel;
    }

    public void setThemeLabel(String themeLabel) {
        this.themeLabel = themeLabel;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrect() {
        return correct;
    }

    public void setCorrect(int correct) {
        this.correct = correct;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCorrectAnswer() {
        return options != null && correct >= 0 && correct < options.size()
                ? options.get(correct) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question question = (Question) o;
        return Objects.equals(id, question.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Question{" +
                "id='" + id + '\'' +
                ", theme='" + theme + '\'' +
                ", difficulty='" + difficulty + '\'' +
                '}';
    }
}