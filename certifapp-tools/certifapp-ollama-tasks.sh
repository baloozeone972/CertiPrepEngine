#!/bin/bash
# =============================================================================
# certifapp-ollama-tasks.sh
# Lance toutes les tâches Ollama en parallèle pendant que Claude Phase 2 tourne
# Usage : chmod +x certifapp-ollama-tasks.sh && ./certifapp-ollama-tasks.sh
# =============================================================================

set -euo pipefail

# --- Configuration ---
OUTPUT_DIR="./ollama_output"
PROMPTS_DIR="./ollama_prompts"
LOG_DIR="./ollama_logs"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Couleurs terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# --- Init ---
mkdir -p "$OUTPUT_DIR" "$PROMPTS_DIR" "$LOG_DIR"

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         CertifApp — Tâches Ollama parallèles             ║"
echo "║         Lancement : $TIMESTAMP                  ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# =============================================================================
# VÉRIFICATIONS PRÉALABLES
# =============================================================================
echo -e "${YELLOW}[CHECK] Vérification des prérequis...${NC}"

# Ollama disponible ?
if ! command -v ollama &>/dev/null; then
  echo -e "${RED}[ERREUR] Ollama non trouvé. Lance : brew install ollama${NC}"
  exit 1
fi

# Ollama serveur actif ?
if ! curl -s http://localhost:11434/api/tags &>/dev/null; then
  echo -e "${YELLOW}[INFO] Démarrage du serveur Ollama...${NC}"
  ollama serve &>/dev/null &
  sleep 3
fi

echo -e "${GREEN}[OK] Ollama actif sur http://localhost:11434${NC}"

# Vérifier les modèles disponibles
MODELS_AVAILABLE=$(ollama list 2>/dev/null | awk 'NR>1 {print $1}')

check_model() {
  local model=$1
  if echo "$MODELS_AVAILABLE" | grep -q "^${model}"; then
    echo -e "${GREEN}[OK] Modèle disponible : $model${NC}"
    return 0
  else
    echo -e "${RED}[MANQUANT] Modèle absent : $model — pull en cours...${NC}"
    ollama pull "$model"
    return 0
  fi
}

check_model "deepseek-coder:33b-instruct-q4_K_M"
check_model "llama3:latest"
check_model "codellama:latest"
check_model "gemma:latest"

echo ""

# =============================================================================
# GÉNÉRATION DES FICHIERS DE PROMPTS
# =============================================================================
echo -e "${YELLOW}[PREP] Génération des fichiers de prompts...${NC}"

# ── Prompt 1 : Normalisation JSON ─────────────────────────────────────────
cat > "$PROMPTS_DIR/p1_normalize.txt" << 'PROMPT'
Génère un script Python 3.11 complet et fonctionnel nommé normalize_questions.py.

Ce script doit :
1. Parcourir récursivement tous les fichiers .json dans le dossier passé en argument (défaut: src/main/resources/certifications/)
2. Pour chaque fichier JSON contenant un tableau de questions :
   - Remplacer le champ "explication" par "explanation" dans chaque objet question
   - Valider les champs obligatoires : id, theme, difficulty, question, options (tableau), correct (entier)
   - Valider que difficulty est dans ["easy", "medium", "hard"]
   - Valider que correct est un index valide dans options[]
3. Mode dry-run par défaut (--dry-run flag) : affiche les modifications sans écrire
4. Mode --apply : écrit les modifications dans les fichiers
5. Générer un rapport CSV rapport_normalisation.csv : fichier, nb_questions, nb_normalisees, nb_invalides, erreurs
6. Afficher un résumé console coloré à la fin

Utilise : Python 3.11+, pathlib, json, argparse, csv, sys, logging
Zéro dépendance externe (pas de pip install requis)
PROMPT

# ── Prompt 2 : Migration JSON → SQL ────────────────────────────────────────
cat > "$PROMPTS_DIR/p2_migrate_sql.txt" << 'PROMPT'
Génère un script Python 3.11 complet et fonctionnel nommé migrate_to_flyway.py.

Contexte : migration du corpus CertiPrep Engine (fichiers JSON) vers PostgreSQL via Flyway.

Structure JSON source d'une question :
{
  "id": "JAVA-001",
  "theme": "Lambda, Streams et API fonctionnelle",
  "theme_label": "Stream terminal operations",
  "difficulty": "medium",
  "question": "Quelle méthode de Stream...",
  "options": ["collect()", "toList()", "asList()", "A et B"],
  "correct": 3,
  "explanation": "Depuis Java 16..."
}

Structure config.json d'une certification :
{
  "id": "ocp21",
  "name": "Oracle Certified Professional Java SE 21",
  "totalQuestions": 500,
  "examDurationMinutes": 180,
  "examQuestionCount": 80,
  "passingScore": 68,
  "themes": [{"name": "Lambda, Streams", "count": 60}]
}

Tables PostgreSQL cibles :
- certifications(id VARCHAR PK, code, name, description, total_questions, exam_question_count, exam_duration_min, passing_score)
- certification_themes(id UUID PK, certification_id FK, code, label, question_count, display_order)
- questions(id UUID PK, legacy_id VARCHAR UNIQUE, certification_id FK, theme_id FK, statement, difficulty, explanation_original)
- question_options(id UUID PK, question_id FK, label CHAR, text, is_correct BOOLEAN, display_order)

Le script doit :
1. Lire tous les config.json et fichiers questions/*.json dans certifications/
2. Générer certif-infrastructure/src/main/resources/db/migration/V3__seed_questions.sql
3. Utiliser INSERT ... ON CONFLICT DO NOTHING (idempotent)
4. Échapper correctement les apostrophes dans le SQL (remplacer ' par '')
5. Générer des UUID reproductibles via uuid5(namespace, legacy_id) pour la traçabilité
6. Fusionner java21 et ocp21 : ignorer java21, utiliser ocp21 comme ID canonique
7. Afficher un rapport : nb certifications, nb thèmes, nb questions, nb options générés
8. Sauvegarder un fichier migration_report.json avec les statistiques

Utilise : Python 3.11+, pathlib, json, uuid, argparse
PROMPT

# ── Prompt 3 : README des 5 modules ────────────────────────────────────────
cat > "$PROMPTS_DIR/p3_readme_modules.txt" << 'PROMPT'
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
PROMPT

# ── Prompt 4 : Javadoc domaine ─────────────────────────────────────────────
cat > "$PROMPTS_DIR/p4_javadoc.txt" << 'PROMPT'
Génère la Javadoc Java complète en anglais pour les classes suivantes du module certif-domain du projet CertifApp.

Génère les blocs Javadoc /** */ complets pour chaque classe et méthode.
Format Oracle standard avec @param, @return, @throws, @since 1.0, @author CertifApp Team.

CLASSE 1 : ScoringService
```java
public class ScoringService {
    public double calculatePercentage(int correctCount, int totalQuestions)
    public boolean isPassed(double percentage, int passingScore)
    public Map<String, ThemeStats> calculateThemeStats(List<UserAnswer> answers, List<Question> questions)
    public Map<String, Double> getDifficultyAnalysis(List<UserAnswer> answers)
    public List<Question> getIncorrectQuestions(List<UserAnswer> answers, List<Question> questions)
    public List<Question> getSkippedQuestions(List<UserAnswer> answers, List<Question> questions)
    public ExamSummary buildExamSummary(ExamSession session, List<Question> questions)
}
```

CLASSE 2 : SM2AlgorithmService (algorithme de répétition espacée)
```java
public class SM2AlgorithmService {
    public SM2Result calculateNextReview(int repetitions, double easeFactor, int intervalDays, int quality)
    public double updateEaseFactor(double easeFactor, int quality)
    public int calculateNextInterval(int repetitions, int currentInterval, double easeFactor)
    public boolean isDue(LocalDate nextReviewDate)
    public List<UUID> getDueQuestions(List<UserSM2Schedule> schedules)
    public int estimateDaysUntilReady(List<UserSM2Schedule> schedules, int passingScore)
}
```

CLASSE 3 : Question (Record Java 21)
```java
public record Question(
    UUID id, String legacyId, String certificationId, String themeId,
    String statement, DifficultyLevel difficulty, QuestionType type,
    List<QuestionOption> options, String explanationOriginal,
    String explanationEnriched, ExplanationStatus explanationStatus
) {
    public Optional<QuestionOption> getCorrectOption()
    public List<QuestionOption> getIncorrectOptions()
    public boolean hasEnrichedExplanation()
    public boolean isReadyForProduction()
}
```

CLASSE 4 : ExamSession (Record Java 21)
```java
public record ExamSession(
    UUID id, UUID userId, String certificationId, ExamMode mode,
    LocalDateTime startedAt, LocalDateTime endedAt,
    int totalQuestions, int correctCount, double percentage,
    boolean passed, List<UserAnswer> answers
) {
    public long getDurationSeconds()
    public boolean isCompleted()
    public boolean isAbandoned()
    public Map<String, Integer> getScoreByTheme()
}
```
PROMPT

# ── Prompt 5 : GitHub Actions workflow ─────────────────────────────────────
cat > "$PROMPTS_DIR/p5_github_actions.txt" << 'PROMPT'
Génère le fichier GitHub Actions .github/workflows/ci.yml complet pour le projet CertifApp.

Contexte technique :
- Projet Maven multi-module Java 21 : certif-domain, certif-application, certif-infrastructure, certif-api-rest, certif-ai
- Tests avec Testcontainers (PostgreSQL 16 avec pgvector)
- Docker build multi-stage
- Déploiement sur Oracle Cloud VM (SSH)
- Registry : Docker Hub

Le workflow doit :

TRIGGER :
- push sur branches : develop, main
- pull_request vers main

JOBS (dans cet ordre) :

JOB 1 : build-and-test
- Runner : ubuntu-latest
- Setup Java 21 (temurin) avec cache Maven
- mvn clean verify (compile + tests unitaires + tests intégration Testcontainers)
- Upload rapport de tests JUnit en artifact
- Upload rapport de couverture Jacoco

JOB 2 : code-quality (dépend de build-and-test)
- SonarCloud scan
- Vérification couverture > 80% sinon fail

JOB 3 : docker-build (dépend de build-and-test, seulement sur push main/develop)
- Build image Docker multi-stage backend (eclipse-temurin:21)
- Build image Docker Angular (node:20-alpine + nginx:alpine)
- Tag : {branch}-{sha_court}
- Push sur Docker Hub

JOB 4 : deploy-staging (dépend de docker-build, seulement sur push develop)
- SSH vers VM Oracle Cloud staging
- docker-compose pull + docker-compose up -d
- Health check : curl http://staging.certifapp.com/actuator/health

JOB 5 : deploy-prod (dépend de docker-build, seulement sur push main, avec approbation manuelle)
- environment: production (nécessite approbation dans GitHub)
- SSH vers VM Oracle Cloud production
- Blue-green deploy : lancer nouveau container → health check → basculer nginx → arrêter ancien

SECRETS à documenter (liste avec description) :
DOCKER_USERNAME, DOCKER_PASSWORD, SONAR_TOKEN,
ORACLE_VM_HOST_STAGING, ORACLE_VM_HOST_PROD, ORACLE_VM_SSH_KEY,
SUPABASE_URL, SUPABASE_KEY, ANTHROPIC_API_KEY, STRIPE_SECRET_KEY

Génère le fichier YAML complet directement utilisable.
PROMPT

# ── Prompt 6 : docker-compose ──────────────────────────────────────────────
cat > "$PROMPTS_DIR/p6_docker_compose.txt" << 'PROMPT'
Génère le fichier docker-compose.yml complet pour le développement local du projet CertifApp.

Services requis :

1. db (PostgreSQL 16 avec pgvector)
   - Image : pgvector/pgvector:pg16
   - Port : 5432
   - Variables : POSTGRES_DB=certifapp, POSTGRES_USER=certif, POSTGRES_PASSWORD=certif_local
   - Volume persistant : postgres_data
   - Healthcheck : pg_isready

2. app (Spring Boot backend)
   - Build depuis ./certif-api-rest/Dockerfile
   - Port : 8080
   - Profile Spring : local
   - Variables d'environnement : DB, Supabase, Ollama, Stripe (depuis .env)
   - Dépend de : db (healthy)
   - Healthcheck : /actuator/health

3. web (Angular PWA)
   - Build depuis ./certif-web/Dockerfile
   - Port : 4200
   - Variables : API_URL=http://localhost:8080
   - Dépend de : app

4. ollama (LLM local)
   - Image : ollama/ollama:latest
   - Port : 11434
   - Volume : ollama_models pour persister les modèles téléchargés
   - Ressources : 12Go RAM max (Mac M3 avec 18Go)

5. redis (cache + sessions)
   - Image : redis:7-alpine
   - Port : 6379
   - Volume persistant

6. nginx (reverse proxy local)
   - Image : nginx:alpine
   - Port : 80
   - Config inline : / → web:4200, /api → app:8080, /ws → app:8080 (WebSocket)

Inclure :
- Fichier .env.example avec toutes les variables
- Networks : certifapp-network bridge
- Volumes nommés
- Profiles Docker : --profile ollama pour activer le service Ollama optionnellement
PROMPT

echo -e "${GREEN}[OK] 6 fichiers de prompts générés dans $PROMPTS_DIR/${NC}"
echo ""

# =============================================================================
# LANCEMENT DES TÂCHES EN PARALLÈLE
# =============================================================================
echo -e "${CYAN}[LAUNCH] Démarrage des 6 tâches Ollama en parallèle...${NC}"
echo ""

PIDS=()
TASKS=(
  "codellama:latest|p1_normalize.txt|normalize_questions.py|T1-Normalisation JSON"
  "deepseek-coder:33b-instruct-q4_K_M|p2_migrate_sql.txt|migrate_to_flyway.py|T2-Migration SQL"
  "llama3:latest|p3_readme_modules.txt|readme_modules.md|T3-README modules"
  "llama3:latest|p4_javadoc.txt|javadoc_domain.java|T4-Javadoc domain"
  "llama3:latest|p5_github_actions.txt|ci.yml|T5-GitHub Actions"
  "llama3:latest|p6_docker_compose.txt|docker-compose.yml|T6-Docker Compose"
)

run_task() {
  local model=$1
  local prompt_file=$2
  local output_file=$3
  local task_name=$4
  local log_file="$LOG_DIR/${task_name// /_}_$TIMESTAMP.log"

  echo -e "${BLUE}[START] $task_name → modèle : $model${NC}"

  ollama run "$model" "$(cat "$PROMPTS_DIR/$prompt_file")" \
    > "$OUTPUT_DIR/$output_file" \
    2> "$log_file"

  local exit_code=$?
  if [ $exit_code -eq 0 ]; then
    local size=$(wc -c < "$OUTPUT_DIR/$output_file")
    echo -e "${GREEN}[DONE] $task_name → $output_file ($size octets)${NC}"
  else
    echo -e "${RED}[FAIL] $task_name → voir $log_file${NC}"
  fi
  return $exit_code
}

# Lancer toutes les tâches en arrière-plan
for task in "${TASKS[@]}"; do
  IFS='|' read -r model prompt output name <<< "$task"
  run_task "$model" "$prompt" "$output" "$name" &
  PIDS+=($!)
  sleep 1  # éviter une surcharge instantanée sur le GPU/CPU
done

echo ""
echo -e "${YELLOW}[WAIT] ${#PIDS[@]} tâches en cours... (peut prendre 10-30 min selon les modèles)${NC}"
echo -e "${YELLOW}       Utilise 'tail -f $LOG_DIR/*.log' pour suivre la progression${NC}"
echo ""

# =============================================================================
# MONITORING DE LA PROGRESSION
# =============================================================================
monitor_progress() {
  local start_time=$SECONDS
  while true; do
    local running=0
    local done=0
    for pid in "${PIDS[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        ((running++))
      else
        ((done++))
      fi
    done

    local elapsed=$((SECONDS - start_time))
    local mins=$((elapsed / 60))
    local secs=$((elapsed % 60))

    printf "\r${CYAN}[PROGRESS] %d/%d tâches terminées | Temps écoulé : %02dm%02ds${NC}    " \
      "$done" "${#PIDS[@]}" "$mins" "$secs"

    if [ $running -eq 0 ]; then
      echo ""
      break
    fi
    sleep 5
  done
}

monitor_progress

# =============================================================================
# ATTENTE DE TOUTES LES TÂCHES ET RAPPORT FINAL
# =============================================================================
FAILED=0
for i in "${!PIDS[@]}"; do
  IFS='|' read -r model prompt output name <<< "${TASKS[$i]}"
  if wait "${PIDS[$i]}"; then
    :
  else
    echo -e "${RED}[FAIL] Tâche échouée : $name${NC}"
    ((FAILED++))
  fi
done

echo ""
echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║                    RAPPORT FINAL                        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

echo -e "${GREEN}Fichiers générés dans $OUTPUT_DIR/ :${NC}"
for task in "${TASKS[@]}"; do
  IFS='|' read -r model prompt output name <<< "$task"
  if [ -f "$OUTPUT_DIR/$output" ]; then
    local_size=$(wc -c < "$OUTPUT_DIR/$output")
    echo -e "  ${GREEN}✓${NC} $output ($local_size octets)"
  else
    echo -e "  ${RED}✗${NC} $output — MANQUANT"
  fi
done

echo ""
if [ $FAILED -eq 0 ]; then
  echo -e "${GREEN}[SUCCESS] Toutes les tâches Ollama sont terminées !${NC}"
else
  echo -e "${YELLOW}[PARTIEL] $FAILED tâche(s) ont échoué. Voir les logs dans $LOG_DIR/${NC}"
fi

echo ""
echo -e "${CYAN}Prochaines étapes :${NC}"
echo "  1. Vérifier les fichiers dans $OUTPUT_DIR/"
echo "  2. Attendre la Phase 2 dans Claude (pom.xml + DDL + OpenAPI)"
echo "  3. Copier les fichiers validés dans le repo certification-zen"
echo "     cp $OUTPUT_DIR/normalize_questions.py scripts/"
echo "     cp $OUTPUT_DIR/migrate_to_flyway.py scripts/"
echo "     cp $OUTPUT_DIR/ci.yml .github/workflows/"
echo "     cp $OUTPUT_DIR/docker-compose.yml ."
echo ""
echo -e "${YELLOW}Logs disponibles dans : $LOG_DIR/${NC}"
echo ""
