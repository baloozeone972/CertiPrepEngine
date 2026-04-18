#!/bin/bash
# =============================================================================
# run.sh — Lancement rapide CertifApp Ollama Tasks
# Usage : ./run.sh [all|normalize|migrate|readme|javadoc|ci|docker]
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAIN_SCRIPT="$SCRIPT_DIR/certifapp-ollama-tasks.sh"

# Couleurs
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Vérifier que le script principal existe
if [ ! -f "$MAIN_SCRIPT" ]; then
  echo -e "${YELLOW}Script principal non trouvé, téléchargement...${NC}"
  exit 1
fi

chmod +x "$MAIN_SCRIPT"

case "${1:-all}" in
  all)
    echo -e "${CYAN}Lancement de TOUTES les tâches Ollama en parallèle...${NC}"
    "$MAIN_SCRIPT"
    ;;
  normalize)
    echo -e "${CYAN}Tâche : Normalisation JSON (codellama)${NC}"
    ollama run codellama "$(cat ollama_prompts/p1_normalize.txt)" > ollama_output/normalize_questions.py
    echo -e "${GREEN}Terminé : ollama_output/normalize_questions.py${NC}"
    ;;
  migrate)
    echo -e "${CYAN}Tâche : Migration SQL (deepseek-coder:33b)${NC}"
    ollama run deepseek-coder:33b-instruct-q4_K_M "$(cat ollama_prompts/p2_migrate_sql.txt)" > ollama_output/migrate_to_flyway.py
    echo -e "${GREEN}Terminé : ollama_output/migrate_to_flyway.py${NC}"
    ;;
  readme)
    echo -e "${CYAN}Tâche : README modules (llama3)${NC}"
    ollama run llama3 "$(cat ollama_prompts/p3_readme_modules.txt)" > ollama_output/readme_modules.md
    echo -e "${GREEN}Terminé : ollama_output/readme_modules.md${NC}"
    ;;
  javadoc)
    echo -e "${CYAN}Tâche : Javadoc domain (llama3)${NC}"
    ollama run llama3 "$(cat ollama_prompts/p4_javadoc.txt)" > ollama_output/javadoc_domain.java
    echo -e "${GREEN}Terminé : ollama_output/javadoc_domain.java${NC}"
    ;;
  ci)
    echo -e "${CYAN}Tâche : GitHub Actions (llama3)${NC}"
    ollama run llama3 "$(cat ollama_prompts/p5_github_actions.txt)" > ollama_output/ci.yml
    echo -e "${GREEN}Terminé : ollama_output/ci.yml${NC}"
    ;;
  docker)
    echo -e "${CYAN}Tâche : Docker Compose (llama3)${NC}"
    ollama run llama3 "$(cat ollama_prompts/p6_docker_compose.txt)" > ollama_output/docker-compose.yml
    echo -e "${GREEN}Terminé : ollama_output/docker-compose.yml${NC}"
    ;;
  status)
    echo -e "${CYAN}Tâches Ollama en cours :${NC}"
    ps aux | grep "ollama run" | grep -v grep || echo "Aucune tâche en cours"
    echo ""
    echo -e "${CYAN}Fichiers générés :${NC}"
    ls -lh ollama_output/ 2>/dev/null || echo "Dossier ollama_output/ vide"
    ;;
  logs)
    echo -e "${CYAN}Derniers logs :${NC}"
    tail -n 20 ollama_logs/*.log 2>/dev/null || echo "Aucun log disponible"
    ;;
  *)
    echo "Usage : ./run.sh [all|normalize|migrate|readme|javadoc|ci|docker|status|logs]"
    echo ""
    echo "  all        Lance toutes les tâches en parallèle (défaut)"
    echo "  normalize  Normalisation JSON explication→explanation"
    echo "  migrate    Script migration JSON→SQL Flyway"
    echo "  readme     README des 5 modules Maven"
    echo "  javadoc    Javadoc des classes domain"
    echo "  ci         GitHub Actions workflow"
    echo "  docker     docker-compose.yml local"
    echo "  status     Affiche les tâches en cours et fichiers générés"
    echo "  logs       Affiche les derniers logs"
    ;;
esac
