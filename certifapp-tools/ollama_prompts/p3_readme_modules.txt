Génère les fichiers README.md complets pour les 5 modules Maven du projet CertifApp.
Produis les 5 README à la suite, chacun séparé par "---MODULE---".

Contexte du projet :
- Application SaaS de préparation aux certifications professionnelles
- Stack : Java 21, Spring Boot 3.3, PostgreSQL, LangChain4j, Angular 18, Kotlin/Compose Android
- Architecture hexagonale stricte
- Marché francophone mondial (France, DOM-TOM, Maghreb, Afrique, Suisse, Québec)

MODULE 1 : certif-domain
- Java 21 pur, ZÉRO dépendance framework (pas Spring, pas JPA)
- Contient : Records Java 21 (Question, Certification, ExamSession, UserAnswer, ThemeStats, Flashcard, Course, UserPath)
- Enums : DifficultyLevel, ExamMode, ExplanationStatus, QuestionType
- Services domain purs : ScoringService, SM2AlgorithmService
- Ports hexagonaux (interfaces) : QuestionRepository, ExamSessionRepository, CourseRepository, etc.
- Exceptions métier typées

MODULE 2 : certif-application
- Dépend uniquement de certif-domain
- Contient les use cases (interfaces + implémentations)
- Use cases MVP : ListCertifications, StartExam, SubmitAnswer, SubmitExam, GetResults, Register, Authenticate
- Use cases Learning : GetCourse, GetFlashcards, GetAdaptivePlan, ReviewFlashcard
- Use cases Coaching : RunDiagnostic, GetCertPath, GetWeeklyReport
- Use cases Community : JoinChallenge, CreateGroup, GetLeaderboard

MODULE 3 : certif-infrastructure
- Dépend de certif-domain + certif-application
- Entités JPA + repositories Spring Data
- Migrations Flyway (V1 à V5)
- Adapters : PdfExportAdapter (iText7), MailAdapter, JobMarketAdapter (Indeed API)
- Configuration : JPA, Flyway, Cache Caffeine

MODULE 4 : certif-api-rest
- Dépend de certif-application + certif-infrastructure
- Spring Boot 3.3 application principale
- Controllers REST : Certification, Exam, Learning, Community, Coaching, Interview, User, Admin, Webhook
- Sécurité : Spring Security 6 + JWT (access 15min, refresh 7j)
- Documentation : SpringDoc OpenAPI 3
- WebSocket : GroupStudy, ChallengeLive

MODULE 5 : certif-ai
- Dépend de certif-domain + certif-application
- LangChain4j : routing Ollama local (dev) / Claude API Anthropic (prod)
- Services IA : ExplanationEnricher, CourseGenerator, FlashcardGenerator, ChatAssistant
- Services coaching : CertPathAdvisor, WeeklyCoachReport, InterviewSimulator, DiagnosticAnalyzer
- RAG : VectorStoreAdapter (pgvector), DocumentIngester, RetrievalService
- Prompts : templates Mustache par use case

Format pour chaque README :
# {nom-module}
## Responsabilité
## Règles strictes (ce qui est interdit dans ce module)
## Packages et leur rôle
## Dépendances Maven
## Commande de build isolé
## Lancement des tests
## Développeur recommandé
