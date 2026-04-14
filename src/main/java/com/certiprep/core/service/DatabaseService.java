package com.certiprep.core.service;

import com.certiprep.core.model.ExamSession;
import com.certiprep.core.model.UserAnswer;
import com.certiprep.core.utils.LoggerUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseService {

    private static final Logger logger = LoggerUtil.getLogger(DatabaseService.class);
    private static DatabaseService instance;
    private final String dbPath = "data/certiprep.db";
    private Connection connection;

    private DatabaseService() {
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    public void initializeDatabase() {
        try {
            // Créer le dossier data s'il n'existe pas
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("data"));

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            createTables();
            logger.info("Base de données initialisée avec succès");
        } catch (Exception e) {
            logger.severe("Erreur lors de l'initialisation de la base de données");
        }
    }

    private void createTables() throws SQLException {
        String createSessionsTable = """
                CREATE TABLE IF NOT EXISTS exam_sessions (
                    session_id TEXT PRIMARY KEY,
                    certification_id TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT,
                    duration_minutes INTEGER,
                    total_questions INTEGER,
                    score INTEGER,
                    passed INTEGER,
                    mode TEXT,
                    percentage REAL
                )
                """;

        String createAnswersTable = """
                CREATE TABLE IF NOT EXISTS user_answers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    question_id TEXT NOT NULL,
                    selected_answer INTEGER,
                    is_correct INTEGER,
                    response_time_ms INTEGER,
                    theme TEXT,
                    FOREIGN KEY (session_id) REFERENCES exam_sessions(session_id)
                )
                """;

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_session_id ON user_answers(session_id)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSessionsTable);
            stmt.execute(createAnswersTable);
            stmt.execute(createIndex);
        }
    }

    public void saveSession(ExamSession session) {
        String sql = """
                INSERT OR REPLACE INTO exam_sessions 
                (session_id, certification_id, start_time, end_time, duration_minutes, 
                 total_questions, score, passed, mode, percentage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, session.getSessionId());
            pstmt.setString(2, session.getCertificationId());
            pstmt.setString(3, session.getStartTime().toString());
            pstmt.setString(4, session.getEndTime() != null ? session.getEndTime().toString() : null);
            pstmt.setInt(5, session.getDurationMinutes());
            pstmt.setInt(6, session.getTotalQuestions());
            pstmt.setInt(7, session.getScore());
            pstmt.setInt(8, session.isPassed() ? 1 : 0);
            pstmt.setString(9, session.getMode().name());
            pstmt.setDouble(10, session.getPercentage());
            pstmt.executeUpdate();

            // Sauvegarder les réponses
            saveAnswers(session);

            logger.info("Session sauvegardée: {}" + "session.getSessionId()");
        } catch (SQLException e) {
            logger.severe("Erreur lors de la sauvegarde de la session");
        }
    }

    private void saveAnswers(ExamSession session) throws SQLException {
        String deleteSql = "DELETE FROM user_answers WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
            pstmt.setString(1, session.getSessionId());
            pstmt.executeUpdate();
        }

        String insertSql = """
                INSERT INTO user_answers 
                (session_id, question_id, selected_answer, is_correct, response_time_ms, theme)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            for (UserAnswer answer : session.getUserAnswers()) {
                pstmt.setString(1, session.getSessionId());
                pstmt.setString(2, answer.getQuestionId());
                pstmt.setInt(3, answer.getSelectedAnswer());
                pstmt.setInt(4, answer.isCorrect() ? 1 : 0);
                pstmt.setLong(5, answer.getResponseTimeMs());
                pstmt.setString(6, answer.getTheme());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<ExamSession> getSessions(String certificationId) {
        List<ExamSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM exam_sessions WHERE certification_id = ? ORDER BY start_time DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, certificationId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                ExamSession session = new ExamSession();
                session.setSessionId(rs.getString("session_id"));
                session.setCertificationId(rs.getString("certification_id"));
                session.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
                String endTime = rs.getString("end_time");
                if (endTime != null) {
                    session.setEndTime(LocalDateTime.parse(endTime));
                }
                session.setDurationMinutes(rs.getInt("duration_minutes"));
                session.setTotalQuestions(rs.getInt("total_questions"));
                session.setScore(rs.getInt("score"));
                session.setPassed(rs.getInt("passed") == 1);
                session.setMode(ExamSession.ExamMode.valueOf(rs.getString("mode")));
                session.setUserAnswers(getAnswersForSession(session.getSessionId()));
                sessions.add(session);
            }
        } catch (SQLException e) {
            logger.severe("Erreur lors du chargement des sessions");
        }

        return sessions;
    }

    private List<UserAnswer> getAnswersForSession(String sessionId) {
        List<UserAnswer> answers = new ArrayList<>();
        String sql = "SELECT * FROM user_answers WHERE session_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                UserAnswer answer = new UserAnswer();
                answer.setQuestionId(rs.getString("question_id"));
                answer.setSelectedAnswer(rs.getInt("selected_answer"));
                answer.setCorrect(rs.getInt("is_correct") == 1);
                answer.setResponseTimeMs(rs.getLong("response_time_ms"));
                answer.setTheme(rs.getString("theme"));
                answers.add(answer);
            }
        } catch (SQLException e) {
            logger.severe("Erreur lors du chargement des réponses");
        }

        return answers;
    }

    public void deleteSession(String sessionId) {
        String deleteAnswers = "DELETE FROM user_answers WHERE session_id = ?";
        String deleteSession = "DELETE FROM exam_sessions WHERE session_id = ?";

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement pstmt = connection.prepareStatement(deleteAnswers)) {
                pstmt.setString(1, sessionId);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = connection.prepareStatement(deleteSession)) {
                pstmt.setString(1, sessionId);
                pstmt.executeUpdate();
            }

            connection.commit();
            logger.info("Session supprimée: {}" + "sessionId");
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                logger.severe("Erreur lors du rollback");
            }
            logger.severe("Erreur lors de la suppression de la session");
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.severe("Erreur reset autoCommit");
            }
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Base de données fermée");
            }
        } catch (SQLException e) {
            logger.severe("Erreur lors de la fermeture de la base de données");
        }
    }

    public Connection getConnection() {
        return connection;
    }
}