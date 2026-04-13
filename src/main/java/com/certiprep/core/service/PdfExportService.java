package com.certiprep.core.service;

import com.certiprep.core.model.ExamSession;
import com.certiprep.core.model.Question;
import com.certiprep.core.model.ThemeStats;
import com.certiprep.core.model.UserAnswer;
import com.certiprep.core.utils.LoggerUtil;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfExportService {

    private static final Logger logger = LoggerUtil.getLogger(PdfExportService.class);
    private static PdfExportService instance;

    private PdfExportService() {}

    public static synchronized PdfExportService getInstance() {
        if (instance == null) {
            instance = new PdfExportService();
        }
        return instance;
    }

    public boolean exportResults(ExamSession session, List<ThemeStats> themeStats,
                                 List<Question> wrongQuestions, String filePath) {
        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(50, 50, 50, 50);

            // Titre
            Paragraph title = new Paragraph("CertiPrep Engine - Rapport d'examen")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(title);

            // Informations générales
            document.add(new Paragraph("Certification: " + session.getCertificationId()).setFontSize(12));
            document.add(new Paragraph("Date: " + session.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).setFontSize(12));
            document.add(new Paragraph("Mode: " + session.getMode()).setFontSize(12));
            document.add(new Paragraph("Durée: " + formatDuration(session.getDurationSeconds())).setFontSize(12));
            document.add(new Paragraph(" "));

            // Score
            Paragraph scorePara = new Paragraph()
                    .add(new Paragraph("Score: " + session.getScore() + " / " + session.getTotalQuestions())
                            .setFontSize(16)
                            .setBold())
                    .add(new Paragraph("Pourcentage: " + String.format("%.1f", session.getPercentage()) + "%")
                            .setFontSize(16))
                    .add(new Paragraph("Statut: " + (session.isPassed() ? "RÉUSSI ✓" : "ÉCHEC ✗"))
                            .setFontSize(16)
                            .setBold()
                            .setFontColor(session.isPassed() ? ColorConstants.GREEN : ColorConstants.RED));
            document.add(scorePara);
            document.add(new Paragraph(" "));

            // Statistiques par thème
            document.add(new Paragraph("Détail par thème").setFontSize(14).setBold().setMarginBottom(10));

            Table themeTable = new Table(UnitValue.createPercentArray(new float[]{40, 15, 15, 15, 15}));
            themeTable.setWidth(UnitValue.createPercentValue(100));
            themeTable.addHeaderCell(createHeaderCell("Thème"));
            themeTable.addHeaderCell(createHeaderCell("Correct"));
            themeTable.addHeaderCell(createHeaderCell("Incorrect"));
            themeTable.addHeaderCell(createHeaderCell("Non répondu"));
            themeTable.addHeaderCell(createHeaderCell("%"));

            for (ThemeStats stats : themeStats) {
                themeTable.addCell(new Cell().add(new Paragraph(stats.getThemeName())));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getCorrectAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getWrongAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getSkippedAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.format("%.1f", stats.getPercentage()) + "%")));
            }
            document.add(themeTable);
            document.add(new Paragraph(" "));

            // Questions erronées
            if (!wrongQuestions.isEmpty()) {
                document.add(new Paragraph("Questions à réviser").setFontSize(14).setBold().setMarginBottom(10));

                for (int i = 0; i < Math.min(wrongQuestions.size(), 20); i++) {
                    Question q = wrongQuestions.get(i);
                    document.add(new Paragraph((i + 1) + ". " + q.getQuestion()).setBold().setMarginTop(10));
                    document.add(new Paragraph("Réponse correcte: " + q.getCorrectAnswer()).setMarginLeft(20));
                    document.add(new Paragraph("Explication: " + q.getExplanation()).setMarginLeft(20).setFontColor(ColorConstants.DARK_GRAY));
                }

                if (wrongQuestions.size() > 20) {
                    document.add(new Paragraph("... et " + (wrongQuestions.size() - 20) + " autres questions à réviser")
                            .setFontColor(ColorConstants.GRAY));
                }
            }

            document.close();
            logger.info("PDF exporté avec succès: {}"+ filePath);
            return true;

        } catch (Exception e) {
            logger.severe("Erreur lors de l'export PDF");
            return false;
        }
    }

    // Ajouter cette méthode à PdfExportService.java

    public boolean exportDetailedResults(ExamSession session, List<ThemeStats> themeStats,
                                         List<Question> wrongQuestions, List<Question> allQuestions,
                                         String filePath) {
        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(50, 50, 50, 50);

            // ========== PAGE 1 : RÉSUMÉ ==========

            // Titre
            Paragraph title = new Paragraph("CertiPrep Engine - Rapport d'examen")
                    .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(20);
            document.add(title);

            // Informations générales
            document.add(new Paragraph("Certification: " + session.getCertificationId()).setFontSize(12));
            document.add(new Paragraph("Date: " + session.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).setFontSize(12));
            document.add(new Paragraph("Mode: " + session.getMode()).setFontSize(12));
            document.add(new Paragraph("Durée: " + formatDuration(session.getDurationSeconds())).setFontSize(12));
            document.add(new Paragraph(" "));

            // Score
            Paragraph scorePara = new Paragraph()
                    .add(new Paragraph("Score: " + session.getScore() + " / " + session.getTotalQuestions()).setFontSize(16).setBold())
                    .add(new Paragraph("Pourcentage: " + String.format("%.1f", session.getPercentage()) + "%").setFontSize(16))
                    .add(new Paragraph("Statut: " + (session.isPassed() ? "RÉUSSI ✓" : "ÉCHEC ✗")).setFontSize(16).setBold()
                            .setFontColor(session.isPassed() ? ColorConstants.GREEN : ColorConstants.RED));
            document.add(scorePara);
            document.add(new Paragraph(" "));

            // Tableau par thème
            document.add(new Paragraph("Détail par thème").setFontSize(14).setBold().setMarginBottom(10));

            Table themeTable = new Table(UnitValue.createPercentArray(new float[]{40, 15, 15, 15, 15}));
            themeTable.setWidth(UnitValue.createPercentValue(100));
            themeTable.addHeaderCell(createHeaderCell("Thème"));
            themeTable.addHeaderCell(createHeaderCell("Correct"));
            themeTable.addHeaderCell(createHeaderCell("Incorrect"));
            themeTable.addHeaderCell(createHeaderCell("Non répondu"));
            themeTable.addHeaderCell(createHeaderCell("%"));

            for (ThemeStats stats : themeStats) {
                themeTable.addCell(new Cell().add(new Paragraph(stats.getThemeName())));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getCorrectAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getWrongAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.valueOf(stats.getSkippedAnswers()))));
                themeTable.addCell(new Cell().add(new Paragraph(String.format("%.1f%%", stats.getPercentage()))));
            }
            document.add(themeTable);

            document.add(new Paragraph(" "));

            // ========== PAGE 2 : QUESTIONS ERRONÉES ==========

            if (!wrongQuestions.isEmpty()) {
                document.add(new Paragraph("Questions à réviser").setFontSize(14).setBold().setMarginBottom(10));

                for (int i = 0; i < wrongQuestions.size(); i++) {
                    Question q = wrongQuestions.get(i);
                    document.add(new Paragraph((i + 1) + ". " + q.getQuestion()).setBold().setMarginTop(10));
                    document.add(new Paragraph("Réponse correcte: " + q.getCorrectAnswer()).setMarginLeft(20));
                    document.add(new Paragraph("Explication: " + q.getExplanation()).setMarginLeft(20).setFontColor(ColorConstants.DARK_GRAY));
                    document.add(new Paragraph(" "));
                }
            }

            // ========== PAGE 3 : TOUTES LES QUESTIONS AVEC RÉPONSES ==========

            document.add(new Paragraph("Détail complet des réponses").setFontSize(14).setBold().setMarginBottom(10));
            document.add(new Paragraph(" "));

            // Créer une map des réponses utilisateur
            Map<String, UserAnswer> answerMap = new HashMap<>();
            for (UserAnswer answer : session.getUserAnswers()) {
                answerMap.put(answer.getQuestionId(), answer);
            }

            for (int i = 0; i < allQuestions.size(); i++) {
                Question q = allQuestions.get(i);
                UserAnswer userAnswer = answerMap.get(q.getId());

                String statusIcon = "";
                if (userAnswer != null) {
                    if (userAnswer.getSelectedAnswer() == q.getCorrect()) {
                        statusIcon = "✓ ";
                    } else if (userAnswer.getSelectedAnswer() == -1) {
                        statusIcon = "○ ";
                    } else {
                        statusIcon = "✗ ";
                    }
                }

                document.add(new Paragraph((i + 1) + ". " + statusIcon + q.getQuestion()).setBold().setMarginTop(8));

                if (userAnswer != null && userAnswer.getSelectedAnswer() != -1) {
                    document.add(new Paragraph("  Votre réponse: " + q.getOptions().get(userAnswer.getSelectedAnswer())).setMarginLeft(20));
                } else {
                    document.add(new Paragraph("  Votre réponse: Non répondue").setMarginLeft(20).setFontColor(ColorConstants.RED));
                }

                document.add(new Paragraph("  Réponse correcte: " + q.getCorrectAnswer()).setMarginLeft(20));
                document.add(new Paragraph("  " + q.getExplanation()).setMarginLeft(20).setFontColor(ColorConstants.DARK_GRAY));
            }

            document.close();
            logger.info("PDF détaillé exporté avec succès: " + filePath);
            return true;

        } catch (Exception e) {
            logger.severe("Erreur lors de l'export PDF: " + e.getMessage());
            return false;
        }
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private Cell createHeaderCell(String text) {
        Cell cell = new Cell();
        cell.add(new Paragraph(text).setBold());
        cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
        return cell;
    }

}