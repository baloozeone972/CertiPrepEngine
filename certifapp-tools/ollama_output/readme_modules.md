Here are the 5 README files for the CertifApp project:

---

**MODULE 1: certif-domain**
# CertifDomain
## Responsabilité
The certif-domain module is responsible for defining the domain model of th[2D[K
the CertifApp application. This includes records, enums, and services that [K
encapsulate the business logic.

## Règles strictes (ce qui est interdit dans ce module)
* No framework dependencies (e.g., Spring, JPA) allowed.
* Only Java 21 features are used.
* No external libraries or dependencies are permitted.

## Packages et leur rôle
* `certif.domain.records`: Contains the domain records (entities) such as Q[1D[K
Question, Certification, ExamSession, UserAnswer, etc.
* `certif.domain.enums`: Defines enums for DifficultyLevel, ExamMode, Expla[5D[K
ExplanationStatus, and QuestionType.
* `certif.domain.services`: Provides services that encapsulate business log[3D[K
logic, such as ScoringService and SM2AlgorithmService.
* `certif.domain.ports`: Defines ports (interfaces) that are used by other [K
modules to interact with the domain model.

## Dépendances Maven
* None

## Commande de build isolé
`mvn clean package -Dmaven.test.skip=true`

## Lancement des tests
No specific test command is required, as the module does not have any exter[5D[K
external dependencies or services that require testing.

## Développeur recommandé
Java developers with experience in Java 21 and its features are recommended[11D[K
recommended to work on this module.

---

**MODULE 2: certif-application**
# CertifApplication
## Responsabilité
The certif-application module is responsible for implementing the use cases[5D[K
cases of the CertifApp application. This includes interfaces and implementa[10D[K
implementations for MVP, Learning, Coaching, and Community use cases.

## Règles strictes (ce qui est interdit dans ce module)
* Can only depend on the `certif-domain` module.
* No external libraries or dependencies are permitted.

## Packages et leur rôle
* `certif.application.mvp`: Contains interfaces and implementations for MVP[3D[K
MVP use cases such as ListCertifications, StartExam, SubmitAnswer, etc.
* `certif.application.learning`: Provides interfaces and implementations fo[2D[K
for Learning use cases like GetCourse, GetFlashcards, and GetAdaptivePlan.
* `certif.application.coaching`: Defines interfaces and implementations for[3D[K
for Coaching use cases such as RunDiagnostic, GetCertPath, and GetWeeklyRep[12D[K
GetWeeklyReport.
* `certif.application.community`: Contains interfaces and implementations f[1D[K
for Community use cases like JoinChallenge, CreateGroup, and GetLeaderboard[14D[K
GetLeaderboard.

## Dépendances Maven
* `certif-domain`

## Commande de build isolé
`mvn clean package -Dmaven.test.skip=true`

## Lancement des tests
No specific test command is required, as the module does not have any exter[5D[K
external dependencies or services that require testing.

## Développeur recommandé
Java developers with experience in Java 21 and its features are recommended[11D[K
recommended to work on this module.

---

**MODULE 3: certif-infrastructure**
# CertifInfrastructure
## Responsabilité
The certif-infrastructure module is responsible for providing the underlyin[9D[K
underlying infrastructure for the CertifApp application. This includes JPA [K
entities, Spring Data repositories, Flyway migrations, adapters, and config[6D[K
configuration.

## Règles strictes (ce qui est interdit dans ce module)
* Can depend on both `certif-domain` and `certif-application` modules.
* No external libraries or dependencies are permitted.

## Packages et leur rôle
* `certif.infrastructure.jpa`: Contains JPA entities that map to the domain[6D[K
domain model records.
* `certif.infrastructure.repositories`: Provides Spring Data repositories f[1D[K
for interacting with the database.
* `certif.infrastructure.migrations`: Defines Flyway migrations for schema [K
updates and migrations.
* `certif.infrastructure.adapters`: Contains adapters for integrating with [K
external services, such as PdfExportAdapter (iText7) and JobMarketAdapter ([1D[K
(Indeed API).
* `certif.infrastructure.configuration`: Provides configuration for JPA, Fl[2D[K
Flyway, and caching using Caffeine.

## Dépendances Maven
* `certif-domain`
* `certif-application`

## Commande de build isolé
`mvn clean package -Dmaven.test.skip=true`

## Lancement des tests
No specific test command is required, as the module does not have any exter[5D[K
external dependencies or services that require testing.

## Développeur recommandé
Java developers with experience in Java 21 and its features are recommended[11D[K
recommended to work on this module.

---

**MODULE 4: certif-api-rest**
# CertifApiRest
## Responsabilité
The certify-api-rest module is responsible for providing the REST API endpo[5D[K
endpoints for the CertifApp application. This includes controllers, securit[7D[K
security, documentation, and WebSocket integration.

## Règles strictes (ce qui est interdit dans ce module)
* Can depend on both `certif-application` and `certif-infrastructure` modul[5D[K
modules.
* No external libraries or dependencies are permitted.

## Packages et leur rôle
* `certif.api.rest.controllers`: Contains REST API controllers for Certific[8D[K
Certification, Exam, Learning, Community, Coaching, Interview, User, Admin,[6D[K
Admin, and Webhook endpoints.
* `certif.api.rest.security`: Provides Spring Security 6 configuration with[4D[K
with JWT authentication (access token valid for 15 minutes, refresh token v[1D[K
valid for 7 days).
* `certif.api.rest.documentation`: Generates OpenAPI 3 documentation using [K
SpringDoc.
* `certif.api.rest.websocket`: Enables WebSocket integration for GroupStudy[10D[K
GroupStudy and ChallengeLive endpoints.

## Dépendances Maven
* `certif-application`
* `certif-infrastructure`

## Commande de build isolé
`mvn clean package -Dmaven.test.skip=true`

## Lancement des tests
No specific test command is required, as the module does not have any exter[5D[K
external dependencies or services that require testing.

## Développeur recommandé
Java developers with experience in Java 21 and its features are recommended[11D[K
recommended to work on this module.

---

**MODULE 5: certif-ai**
# CertifAi
## Responsabilité
The certify-ai module is responsible for integrating AI/ML capabilities int[3D[K
into the CertifApp application. This includes LangChain4j routing, Explanat[8D[K
ExplanationEnricher services, and other AI-related features.

## Règles strictes (ce qui est interdit dans ce module)
* Can depend on both `certif-domain` and `certif-application` modules.
* No external libraries or dependencies are permitted.

## Packages et leur rôle
* `certif.ai.langchain`: Routes requests to LangChain4j local (dev) or Clau[4D[K
Claude API Anthropic (prod).
* `certif.ai.services`: Provides ExplanationEnricher, CourseGenerator, Flas[4D[K
FlashcardGenerator, and ChatAssistant services.
* `certif.ai.coaching`: Defines CertPathAdvisor, WeeklyCoachReport, Intervi[7D[K
InterviewSimulator, and DiagnosticAnalyzer services.
* `certif.ai.rag`: Contains VectorStoreAdapter (pgvector), DocumentIngester[16D[K
DocumentIngester, and RetrievalService components.

## Dépendances Maven
* `certif-domain`
* `certif-application`

## Commande de build isolé
`mvn clean package -Dmaven.test.skip=true`

## Lancement des tests
No specific test command is required, as the module does not have any exter[5D[K
external dependencies or services that require testing.

## Développeur recommandé
AI/ML developers with experience in LangChain4j and its features are recomm[6D[K
recommended to work on this module.

