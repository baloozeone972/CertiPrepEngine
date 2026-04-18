Here are the complete Javadoc comments for each class and method:

**ScoringService.java**
```java
/**
 * This service provides various scoring-related functions.
 *
 * @author CertifApp Team
 * @since 1.0
 */
public class ScoringService {
    /**
     * Calculate the percentage of correct answers out of total questions.
     *
     * @param correctCount the number of correct answers
     * @param totalQuestions the total number of questions
     * @return the percentage as a decimal value (e.g., 0.8 for 80%)
     */
    public double calculatePercentage(int correctCount, int totalQuestions)[15D[K
totalQuestions);

    /**
     * Check if the given percentage indicates a passed score.
     *
     * @param percentage the percentage to check
     * @param passingScore the minimum passing score
     * @return true if the percentage is above or equal to the passing scor[4D[K
score
     */
    public boolean isPassed(double percentage, int passingScore);

    /**
     * Calculate theme statistics (e.g., correct answers, total questions) [K
based on user answers and questions.
     *
     * @param answers a list of user answers
     * @param questions a list of questions
     * @return a map of theme IDs to ThemeStats objects
     */
    public Map<String, ThemeStats> calculateThemeStats(List<UserAnswer> ans[3D[K
answers, List<Question> questions);

    /**
     * Get the difficulty analysis for each question based on user answers.[8D[K
answers.
     *
     * @param answers a list of user answers
     * @return a map of question IDs to difficulty levels (e.g., EASY, HARD[4D[K
HARD)
     */
    public Map<String, Double> getDifficultyAnalysis(List<UserAnswer> answe[5D[K
answers);

    /**
     * Get the questions that were answered incorrectly.
     *
     * @param answers a list of user answers
     * @param questions a list of questions
     * @return a list of question IDs that were answered incorrectly
     */
    public List<Question> getIncorrectQuestions(List<UserAnswer> answers, L[1D[K
List<Question> questions);

    /**
     * Get the questions that were skipped.
     *
     * @param answers a list of user answers
     * @param questions a list of questions
     * @return a list of question IDs that were skipped
     */
    public List<Question> getSkippedQuestions(List<UserAnswer> answers, Lis[3D[K
List<Question> questions);

    /**
     * Build an exam summary object based on the given session and question[8D[K
questions.
     *
     * @param session the exam session to summarize
     * @param questions a list of questions
     * @return an ExamSummary object
     */
    public ExamSummary buildExamSummary(ExamSession session, List<Question>[14D[K
List<Question> questions);
}
```

**SM2AlgorithmService.java**
```java
/**
 * This service provides the Spaced Repetition Algorithm (SM2) functionalit[12D[K
functionality.
 *
 * @author CertifApp Team
 * @since 1.0
 */
public class SM2AlgorithmService {
    /**
     * Calculate the next review date based on the given parameters.
     *
     * @param repetitions the number of repetitions
     * @param easeFactor the ease factor value
     * @param intervalDays the interval days between reviews
     * @param quality the question quality score
     * @return an SM2Result object containing the next review date and othe[4D[K
other relevant data
     */
    public SM2Result calculateNextReview(int repetitions, double easeFactor[10D[K
easeFactor, int intervalDays, int quality);

    /**
     * Update the ease factor value based on the given quality score.
     *
     * @param easeFactor the current ease factor value
     * @param quality the question quality score
     * @return the updated ease factor value
     */
    public double updateEaseFactor(double easeFactor, int quality);

    /**
     * Calculate the next interval date based on the given parameters.
     *
     * @param repetitions the number of repetitions
     * @param currentInterval the current interval days
     * @param easeFactor the ease factor value
     * @return the next interval date
     */
    public int calculateNextInterval(int repetitions, int currentInterval, [K
double easeFactor);

    /**
     * Check if a question is due for review based on its schedule.
     *
     * @param nextReviewDate the scheduled next review date
     * @return true if the question is due for review
     */
    public boolean isDue(LocalDate nextReviewDate);

    /**
     * Get the questions that are due for review based on their schedules.
     *
     * @param schedules a list of user schedules
     * @return a list of question IDs that are due for review
     */
    public List<UUID> getDueQuestions(List<UserSM2Schedule> schedules);

    /**
     * Estimate the number of days until the next ready question is reached[7D[K
reached based on given schedules.
     *
     * @param schedules a list of user schedules
     * @param passingScore the minimum passing score
     * @return the estimated number of days until the next ready question i[1D[K
is reached
     */
    public int estimateDaysUntilReady(List<UserSM2Schedule> schedules, int [K
passingScore);
}
```

**Question.java (Record Java 21)**
```java
/**
 * This record represents a question in the CertifApp system.
 *
 * @author CertifApp Team
 * @since 1.0
 */
public record Question(
    UUID id, String legacyId, String certificationId, String themeId,
    String statement, DifficultyLevel difficulty, QuestionType type,
    List<QuestionOption> options, String explanationOriginal,
    String explanationEnriched, ExplanationStatus explanationStatus
) {
    /**
     * Get the correct option for this question.
     *
     * @return an Optional containing the correct option (empty if none)
     */
    public Optional<QuestionOption> getCorrectOption();

    /**
     * Get a list of incorrect options for this question.
     *
     * @return a list of QuestionOption objects representing the incorrect [K
options
     */
    public List<QuestionOption> getIncorrectOptions();

    /**
     * Check if this question has an enriched explanation.
     *
     * @return true if the question has an enriched explanation
     */
    public boolean hasEnrichedExplanation();

    /**
     * Check if this question is ready for production (i.e., has a valid st[2D[K
statement and difficulty level).
     *
     * @return true if the question is ready for production
     */
    public boolean isReadyForProduction();
}
```

**ExamSession.java (Record Java 21)**
```java
/**
 * This record represents an exam session in the CertifApp system.
 *
 * @author CertifApp Team
 * @since 1.0
 */
public record ExamSession(
    UUID id, UUID userId, String certificationId, ExamMode mode,
    LocalDateTime startedAt, LocalDateTime endedAt,
    int totalQuestions, int correctCount, double percentage,
    boolean passed, List<UserAnswer> answers
) {
    /**
     * Calculate the duration of this exam session in seconds.
     *
     * @return the duration in seconds
     */
    public long getDurationSeconds();

    /**
     * Check if this exam session is completed.
     *
     * @return true if the session is completed
     */
    public boolean isCompleted();

    /**
     * Check if this exam session is abandoned (i.e., not completed).
     *
     * @return true if the session is abandoned
     */
    public boolean isAbandoned();

    /**
     * Get a map of theme IDs to correct answer counts for this exam sessio[6D[K
session.
     *
     * @return a map of theme IDs to integer values representing the number[6D[K
number of correct answers
     */
    public Map<String, Integer> getScoreByTheme();
}
```

I hope this helps! Let me know if you have any further questions.

