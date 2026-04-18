# CLAUDE.md — CertifApp

Fichier de contexte projet pour Claude Code, Continue et tout agent IA travaillant sur ce repo.
Lire intégralement avant toute intervention sur le code.

---

## Présentation du projet

CertifApp est la migration commerciale de CertiPrep Engine, une application JavaFX de
préparation aux certifications professionnelles.

**Actif principal** : 4000+ questions couvrant une dizaine de certifications complètes
(OCP Java 21, AWS CLF/SAA, Spring Professional, Kubernetes CKA/CKAD, Docker DCA).

**Cible** : SaaS multiplateforme francophone (France, DOM-TOM, Maghreb, Afrique
subsaharienne, Suisse, Québec) avec modèle freemium + abonnement Pro.

**Stack source** : JavaFX + Java 17/21 + SQLite
**Stack cible** : Spring Boot 3 + Angular 18 PWA + Android Kotlin/Compose

---

## Architecture des modules Maven

```
certif-parent/
├── certif-domain/          # Entités + règles métier — zéro dépendance framework
├── certif-application/     # Use cases + ports (interfaces)
├── certif-infrastructure/  # JPA, Flyway, adapters, migration SQLite→PostgreSQL
├── certif-api-rest/        # Spring Boot, controllers REST, DTOs, sécurité JWT
└── certif-ai/              # LangChain4j, ModelRouter, services IA
```

### Règle d'or des couches
```
domain ← application ← infrastructure
domain ← application ← api-rest
domain ← application ← ai
```
Aucune dépendance ne remonte vers les couches supérieures.
`certif-domain` n'importe jamais Spring, JPA, ou tout autre framework.

---

## Commandes de build et lancement

### Prérequis
```bash
java --version          # Java 21 obligatoire
mvn --version           # Maven 3.9+
docker --version        # Docker Desktop actif
ollama list             # Modèles locaux disponibles
```

### Build complet
```bash
mvn clean install                          # Compile + tests tous les modules
mvn clean install -DskipTests              # Build rapide sans tests
mvn clean install -pl certif-domain        # Build d'un seul module
```

### Lancement local
```bash
# Démarrer la base de données PostgreSQL locale
docker-compose up -d db

# Lancer le backend Spring Boot
cd certif-api-rest
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Lancer le frontend Angular
cd certif-web
ng serve

# Backend disponible sur : http://localhost:8080
# Frontend disponible sur : http://localhost:4200
# Swagger UI            : http://localhost:8080/swagger-ui.html
```

### Tests
```bash
mvn test                                   # Tous les tests unitaires
mvn verify                                 # Tests unitaires + intégration
mvn test -pl certif-domain                 # Tests d'un module spécifique
mvn test -Dtest=QuestionServiceTest        # Un test précis
```

### Migration base de données
```bash
# Flyway applique automatiquement les migrations au démarrage
# Scripts dans : certif-infrastructure/src/main/resources/db/migration/
# Format       : V{version}__{description}.sql
# Exemple      : V1__init_schema.sql, V2__seed_certifications.sql
```

---

## Conventions de code — NON NÉGOCIABLES

### Nommage
```
Classes, interfaces, enums  : PascalCase en anglais
Méthodes, variables         : camelCase en anglais
Constantes                  : UPPER_SNAKE_CASE
Packages                    : com.certifapp.{module}.{layer}
Tables BDD                  : snake_case (questions, exam_sessions, user_answers)
Colonnes BDD                : snake_case (created_at, difficulty_level, cert_domain)
```

### Structure d'un package par feature (dans chaque module)
```
com.certifapp.domain/
├── model/          # Entités domain (Records Java 21)
├── port/           # Interfaces (ports hexagonaux)
│   ├── input/      # Use case interfaces
│   └── output/     # Repository interfaces
└── exception/      # Exceptions métier typées
```

### Format obligatoire de chaque fichier produit
Tout fichier généré par un agent IA doit commencer par :
```java
// certif-domain/src/main/java/com/certifapp/domain/model/Question.java
```

### Java 21 — utiliser systématiquement
```java
// Records pour les DTOs et Value Objects
public record QuestionDto(UUID id, String statement, List<OptionDto> options) {}

// Pattern Matching
if (result instanceof CorrectAnswer correct) { ... }

// Text Blocks pour les requêtes SQL longues
String sql = """
    SELECT q.* FROM questions q
    WHERE q.certification_id = :certId
    """;

// Virtual Threads pour les appels LLM (bloquants)
Thread.ofVirtual().start(() -> llmService.enrich(question));
```

### Règles Spring Boot
```java
// Injection par constructeur UNIQUEMENT (pas @Autowired sur le champ)
@Service
@RequiredArgsConstructor
public class ExamSessionService {
    private final SessionRepository sessionRepository;
    private final QuestionSelector questionSelector;
}

// Pas de logique métier dans les controllers
// Controllers : validation @Valid + délégation au use case + mapping DTO

// Transactions au niveau service uniquement
@Transactional(readOnly = true)
public ExamDto findById(UUID id) { ... }
```

### Tests — règles strictes
```java
// Nomenclature : methodName_stateUnderTest_expectedBehavior
@Test
void startSession_withValidExam_returnsActiveSession() { ... }

// Structure AAA obligatoire
void someTest() {
    // Arrange
    var exam = ExamFixture.validExam();
    // Act
    var result = service.startSession(exam.id(), userId);
    // Assert
    assertThat(result.status()).isEqualTo(SessionStatus.ACTIVE);
}

// Testcontainers pour tout test touchant la BDD
@Testcontainers
class QuestionRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
}
```

---

## Routing LLM — quel modèle pour quelle tâche

| Tâche | Modèle à utiliser |
|---|---|
| Architecture, décisions structurantes | Claude Max (claude.ai ou CLI) |
| Génération code Java Spring Boot | deepseek-coder:33b via Continue/OpenClaw |
| Debugging logique complexe | DeepSeek-R1 via Continue |
| Refactoring, nettoyage | codellama via Continue |
| Documentation Javadoc, README | llama3 via Continue/OpenClaw |
| Configs YAML, Docker, GitHub Actions | llama3 via OpenClaw |
| Vérifications rapides syntaxe | gemma via Continue |
| Enrichissement questions (pédagogie) | Claude Max uniquement |

**Règle d'escalade** : 2 tentatives locales infructueuses → escalader au modèle supérieur.

### Commandes OpenClaw types
```bash
# Générer un CRUD complet
openclaw "Génère le CRUD Spring Boot pour [Entité] : controller REST, \
service, repository JPA, DTOs Record Java 21, tests JUnit 5. \
Architecture hexagonale. Chemin complet en en-tête de chaque fichier."

# Générer les tests d'un service existant
openclaw "Génère les tests JUnit 5 + Mockito complets pour le fichier \
[chemin/fichier.java]. Couverture > 80%. Nomenclature AAA."

# Générer la documentation
openclaw "Génère la Javadoc complète pour tous les fichiers du package \
[package]. Format standard Oracle."
```

---

## Module certif-ai — règles spécifiques

### ModelRouter — configuration par profil Spring
```yaml
# application-local.yml
certif:
  ai:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: deepseek-coder:33b-instruct-q4_K_M

# application-prod.yml
certif:
  ai:
    provider: anthropic
    anthropic:
      model: claude-opus-4-6
```

### Human-in-the-loop obligatoire
Toute génération IA (enrichissement de questions, génération de nouvelles questions)
passe par une validation humaine avant persistance. Jamais de sauvegarde automatique
de contenu généré par IA sans approbation explicite d'un administrateur.

### Statuts de questions enrichies
```java
public enum ExplanationStatus {
    ORIGINAL,           // Texte importé depuis JavaFX, non relu
    AI_DRAFT,           // Enrichi par IA, en attente de validation humaine
    HUMAN_VALIDATED     // Validé par un relecteur humain
}
```

---

## Base de données — règles Flyway

```
certif-infrastructure/src/main/resources/db/migration/
├── V1__init_schema.sql              # Création de toutes les tables
├── V2__seed_certifications.sql      # Données de référence (certifications)
├── V3__seed_questions_ocp.sql       # Import des 500+ questions OCP
└── V4__seed_questions_autres.sql    # Import des autres certifications
```

Règles :
- Un script Flyway ne se modifie JAMAIS après sa première exécution en prod
- Tout changement de schéma = nouveau script Vn
- Les seeds de questions sont idempotents (`INSERT ... ON CONFLICT DO NOTHING`)

---

## Variables d'environnement requises

```bash
# Base de données
SUPABASE_URL=https://xxx.supabase.co
SUPABASE_KEY=xxx

# IA
CERTIF_AI_PROVIDER=ollama                          # local : ollama | prod : anthropic
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=deepseek-coder:33b-instruct-q4_K_M
ANTHROPIC_API_KEY=sk-ant-xxx                       # prod uniquement

# Paiement
STRIPE_SECRET_KEY=sk_test_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx

# JWT
JWT_SECRET=xxx                                     # min 256 bits
JWT_EXPIRATION_MS=900000                           # 15 minutes
JWT_REFRESH_EXPIRATION_MS=604800000                # 7 jours
```

Ne jamais committer de valeurs réelles. Utiliser `.env` (ignoré par `.gitignore`).

---

## CI/CD — GitHub Actions

```
.github/workflows/
├── ci.yml              # Pull Request : build + tests + sonar
├── deploy-staging.yml  # Push sur develop : déploiement auto staging
└── deploy-prod.yml     # Tag release : déploiement prod avec validation manuelle
```

Le pipeline CI doit passer avant tout merge sur `main` ou `develop`.

---

## Structure Git — branches

```
main          # Production — merge via PR uniquement, tag de release obligatoire
develop       # Intégration — base de toutes les feature branches
feature/*     # Fonctionnalités (ex: feature/adaptive-learning)
fix/*         # Corrections de bugs
migration/*   # Phases de migration JavaFX (ex: migration/phase1-domain)
```

---

## Checklist avant chaque PR

- [ ] `mvn clean install` passe sans erreur
- [ ] Couverture de tests > 80% sur le module modifié
- [ ] Aucun `// TODO` ni `// à compléter` dans le code livré
- [ ] Javadoc sur toutes les classes et méthodes publiques
- [ ] Chemin complet en en-tête de chaque fichier nouveau
- [ ] Aucune donnée sensible dans le code ou les logs
- [ ] Migration Flyway créée si changement de schéma BDD

---

*Dernière mise à jour : 2026 — généré pour le projet CertifApp*
*Maintenir ce fichier à jour à chaque décision d'architecture majeure.*
