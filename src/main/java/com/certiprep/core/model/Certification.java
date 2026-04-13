package com.certiprep.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Certification {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version;

    @JsonProperty("description")
    private String description;

    @JsonProperty("totalQuestions")
    private int totalQuestions;

    @JsonProperty("examDurationMinutes")
    private int examDurationMinutes;

    @JsonProperty("examQuestionCount")
    private int examQuestionCount;

    @JsonProperty("passingScore")
    private int passingScore;

    @JsonProperty("themes")
    private List<ThemeInfo> themes;

    public static class ThemeInfo {
        private String name;
        private int count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public int getExamDurationMinutes() { return examDurationMinutes; }
    public void setExamDurationMinutes(int examDurationMinutes) { this.examDurationMinutes = examDurationMinutes; }

    public int getExamQuestionCount() { return examQuestionCount; }
    public void setExamQuestionCount(int examQuestionCount) { this.examQuestionCount = examQuestionCount; }

    public int getPassingScore() { return passingScore; }
    public void setPassingScore(int passingScore) { this.passingScore = passingScore; }

    public List<ThemeInfo> getThemes() { return themes; }
    public void setThemes(List<ThemeInfo> themes) { this.themes = themes; }

    public int getPassingQuestionsCount() {
        return (int) Math.ceil(examQuestionCount * passingScore / 100.0);
    }
}