#!/usr/bin/env python3
# =============================================================================
# certif-enricher.py
# Enrichissement du corpus de certifications CertifApp :
#   1. Génère des questions à réponses multiples (60%) + QCM simples (40%)
#   2. Complète/génère les fichiers README.md manquants ou incomplets
#   3. Gère le statut de chaque certification (déjà traité = ignoré)
#   4. Interface de sélection interactive ou par argument
#
# Format questions existantes (Phase 1 rapport) :
#   {id, theme, theme_label, difficulty, question, options, correct(int), explanation}
#   Anomalie connue : certaines certifs utilisent "explication" au lieu de "explanation"
#
# Format questions MULTI ajoutées :
#   {id, theme, theme_label, difficulty, type, question, options,
#    correct(int=premier index), correct_multiple(list[int]), explanation}
#
# Usage :
#   python3 certif-enricher.py                         # menu interactif
#   python3 certif-enricher.py --cert android
#   python3 certif-enricher.py --cert android cka terraform
#   python3 certif-enricher.py --all
#   python3 certif-enricher.py --cert android --theme "Security"
#   python3 certif-enricher.py --cert android --readme-only
#   python3 certif-enricher.py --cert android --questions-only
#   python3 certif-enricher.py --status                # voir ce qui a été fait
#   python3 certif-enricher.py --reset android         # réinitialiser le statut
# =============================================================================

import argparse
import json
import math
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path

# =============================================================================
# CONFIGURATION
# =============================================================================

# Dossiers de certifications à chercher (dans l'ordre de priorité)
CERT_BASE_DIRS = [
    "src/main/resources/certifications",
    "certif-infrastructure/src/main/resources/certifications",
    "resources/certifications",
    "certifications",
]

# Backend LLM — auto-détection dans cet ordre :
#   1. LM Studio  → http://localhost:1234  (démarrer Local Server dans LM Studio)
#   2. Ollama     → http://localhost:11434 (ollama serve + modèle chargé)
#   3. MLX direct → venv ~/.mlx-qwen      (./qwen-mlx.sh --setup)

# LM Studio (OpenAI-compatible)
LM_STUDIO_URL   = "http://localhost:1234/v1/chat/completions"
LM_STUDIO_MODEL = "qwen2.5-14b-instruct"

# Ollama (OpenAI-compatible depuis v0.1.24)
OLLAMA_URL   = "http://localhost:11434/v1/chat/completions"
OLLAMA_MODEL = "qwen2.5:14b"          # ajuste selon 'ollama list'
# Modèles Ollama alternatifs si qwen2.5:14b absent :
OLLAMA_FALLBACK_MODELS = [
    "deepseek-coder:33b-instruct-q4_K_M",
    "llama3:latest",
    "qwen2.5-coder:7b",
    "deepseek-coder:6.7b",
]

# MLX direct (fallback si LM Studio non actif)
MLX_VENV      = Path.home() / ".mlx-qwen"
MODEL_4BIT    = "mlx-community/Qwen2.5-14B-Instruct-4bit"
MODEL_8BIT    = "mlx-community/Qwen2.5-14B-Instruct-8bit"

# Paramètres d'enrichissement
DEFAULT_RATIO   = 35   # % de questions à ajouter par thème
MULTI_PERCENT   = 60   # % des nouvelles questions en multi-réponses
SINGLE_PERCENT  = 40   # % des nouvelles questions en QCM simple

# Fichier de statut (mémorise ce qui a déjà été traité)
STATUS_FILE = Path(".certif-enricher-status.json")

# Template README (structure attendue dans le projet)
README_SECTIONS = [
    "Description", "Organisme", "Validité", "Programme",
    "Reconnaissance", "Salaires", "Statistiques", "Liens"
]

# Toutes les certifications connues du projet (extrait de QuestionLoader.java)
ALL_KNOWN_CERTS = [
    "java21", "java17", "java11", "java8",
    "ocp17", "ocp21", "oca8",
    "spring6", "springboot3", "quarkus", "jakartaee",
    "android", "kotlin",
    "aws_ccp", "aws_saa", "aws_dev",
    "az900", "az204",
    "gcp_dl",
    "docker", "cka", "ckad", "cks", "podman",
    "terraform", "ansible", "puppet", "chef", "crossplane",
    "jenkins", "gitlab", "github_actions", "maven", "gradle",
    "prometheus", "grafana", "elk", "datadog", "newrelic",
    "devsecops", "sonarqube", "trivy", "snyk", "vault"
]

# Couleurs terminal
C = {
    "r": "\033[0;31m", "g": "\033[0;32m", "y": "\033[1;33m",
    "b": "\033[0;34m", "c": "\033[0;36m", "m": "\033[0;35m",
    "w": "\033[1;37m", "x": "\033[0m"
}
def col(c, t): return f"{C.get(c,'')}{t}{C['x']}"

# =============================================================================
# GESTION DU STATUT
# =============================================================================

def load_status() -> dict:
    if STATUS_FILE.exists():
        try:
            return json.loads(STATUS_FILE.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {}

def save_status(status: dict):
    STATUS_FILE.write_text(
        json.dumps(status, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

def mark_done(status: dict, cert_id: str, operation: str, details: dict = None):
    if cert_id not in status:
        status[cert_id] = {}
    status[cert_id][operation] = {
        "done": True,
        "timestamp": datetime.now().isoformat(),
        "details": details or {}
    }
    save_status(status)

def is_done(status: dict, cert_id: str, operation: str) -> bool:
    return status.get(cert_id, {}).get(operation, {}).get("done", False)

def print_status_table(status: dict, cert_root: Path):
    available = list_certs(cert_root)
    print(col("c", "\n╔══════════════════════════════════════════════════════════════════╗"))
    print(col("c",   "║                   STATUT DES CERTIFICATIONS                     ║"))
    print(col("c",   "╚══════════════════════════════════════════════════════════════════╝"))
    print(f"\n{'ID':<20} {'Questions':<12} {'README':<12} {'Dernière action'}")
    print("─" * 70)
    for cert_id in sorted(available):
        q_done   = status.get(cert_id, {}).get("questions", {}).get("done", False)
        r_done   = status.get(cert_id, {}).get("readme",    {}).get("done", False)
        q_ts     = status.get(cert_id, {}).get("questions", {}).get("timestamp", "—")[:10]
        r_ts     = status.get(cert_id, {}).get("readme",    {}).get("timestamp", "—")[:10]
        q_icon   = col("g", "✅ " + q_ts) if q_done else col("y", "⬜ en attente")
        r_icon   = col("g", "✅ " + r_ts) if r_done else col("y", "⬜ en attente")
        print(f"  {cert_id:<18} {q_icon:<22} {r_icon:<22}")
    print()

# =============================================================================
# DÉTECTION DU DOSSIER CERTIFICATIONS
# =============================================================================

def find_cert_root(override: str = None) -> Path:
    if override:
        p = Path(override)
        if p.exists():
            return p.resolve()
        raise FileNotFoundError(f"Dossier spécifié introuvable : {override}")

    for candidate in CERT_BASE_DIRS:
        p = Path(candidate)
        if p.is_dir() and any(
            (p / d / "config.json").exists()
            for d in (os.listdir(p) if p.exists() else [])
        ):
            return p.resolve()

    # Recherche récursive
    for root, dirs, files in os.walk(".", followlinks=False):
        if "certifications" in Path(root).parts[-1:]:
            if any((Path(root) / d / "config.json").exists() for d in dirs):
                return Path(root).resolve()

    raise FileNotFoundError(
        "Dossier 'certifications/' introuvable.\n"
        f"Lance depuis la racine du projet. Cherché dans : {CERT_BASE_DIRS}"
    )

def list_certs(cert_root: Path) -> list:
    return sorted([
        d.name for d in cert_root.iterdir()
        if d.is_dir() and (d / "config.json").exists()
    ])

def load_config(cert_root: Path, cert_id: str) -> dict:
    path = cert_root / cert_id / "config.json"
    if not path.exists():
        raise FileNotFoundError(f"config.json manquant : {path}")
    return json.loads(path.read_text(encoding="utf-8"))

# =============================================================================
# SÉLECTION DU MODÈLE MLX
# =============================================================================

def auto_model() -> str:
    try:
        out = subprocess.run(["vm_stat"], capture_output=True, text=True).stdout
        for line in out.splitlines():
            if "Pages free" in line:
                pages = int(re.sub(r"[^\d]", "", line.split(":")[1]))
                return MODEL_8BIT if pages * 4096 // 1048576 > 14000 else MODEL_4BIT
    except Exception:
        pass
    return MODEL_4BIT

# =============================================================================
# APPEL LLM — LM Studio (prioritaire) ou MLX direct (fallback)
# =============================================================================

def _server_alive(base_url: str, timeout: int = 2) -> bool:
    """Vérifie si un serveur OpenAI-compatible répond."""
    try:
        models_url = base_url.replace("/chat/completions", "/models")
        req = urllib.request.Request(models_url)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status == 200
    except Exception:
        return False

def _get_first_model(base_url: str, default: str) -> str:
    """Récupère le premier modèle disponible sur un serveur OpenAI-compatible."""
    try:
        models_url = base_url.replace("/chat/completions", "/models")
        req = urllib.request.Request(models_url)
        with urllib.request.urlopen(req, timeout=2) as resp:
            data = json.loads(resp.read().decode())
            models = data.get("data", [])
            if models:
                return models[0].get("id", default)
    except Exception:
        pass
    return default

def _get_ollama_model() -> str:
    """Retourne le meilleur modèle disponible dans Ollama."""
    try:
        req = urllib.request.Request("http://localhost:11434/api/tags")
        with urllib.request.urlopen(req, timeout=2) as resp:
            data = json.loads(resp.read().decode())
            available = [m.get("name","") for m in data.get("models", [])]
            # Priorité : qwen2.5:14b > fallbacks dans l'ordre
            for candidate in [OLLAMA_MODEL] + OLLAMA_FALLBACK_MODELS:
                if any(candidate in a or a.startswith(candidate.split(":")[0]) for a in available):
                    return candidate
            if available:
                return available[0]
    except Exception:
        pass
    return OLLAMA_MODEL

def detect_backend() -> tuple:
    """
    Détecte le backend disponible.
    Retourne (backend_name, api_url, model_id) ou ("none", None, None).
    """
    # 1. LM Studio
    if _server_alive(LM_STUDIO_URL):
        model = _get_first_model(LM_STUDIO_URL, LM_STUDIO_MODEL)
        return ("lmstudio", LM_STUDIO_URL, model)

    # 2. Ollama API compatible OpenAI
    if _server_alive(OLLAMA_URL):
        model = _get_ollama_model()
        return ("ollama", OLLAMA_URL, model)

    # 3. MLX direct
    if MLX_VENV.exists():
        return ("mlx", None, MODEL_4BIT)

    return ("none", None, None)

# Cache du backend détecté (évite la re-détection à chaque appel)
_BACKEND_CACHE: tuple | None = None

def lm_studio_available() -> bool:
    """Compatibilité : vérifie qu'un backend quelconque est disponible."""
    backend, _, _ = _get_backend()
    return backend != "none"

def _get_backend() -> tuple:
    global _BACKEND_CACHE
    if _BACKEND_CACHE is None:
        _BACKEND_CACHE = detect_backend()
    return _BACKEND_CACHE

def get_lm_studio_model() -> str:
    """Compatibilité : retourne le modèle du backend actif."""
    _, _, model = _get_backend()
    return model or LM_STUDIO_MODEL

def call_lm_studio(prompt: str, system: str,
                   max_tokens: int = 3000, temp: float = 0.3) -> str:
    """Appelle un serveur OpenAI-compatible (LM Studio ou Ollama)."""
    backend, api_url, model_id = _get_backend()

    payload = json.dumps({
        "model": model_id,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user",   "content": prompt}
        ],
        "max_tokens": max_tokens,
        "temperature": temp,
        "stream": False
    }).encode("utf-8")

    req = urllib.request.Request(
        api_url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=600) as resp:
        data = json.loads(resp.read().decode())
    return data["choices"][0]["message"]["content"].strip()

def call_mlx(prompt: str, system: str, model: str,
             max_tokens: int = 3000, temp: float = 0.3) -> str:
    """Appelle Qwen2.5-14B via MLX direct (venv ~/.mlx-qwen)."""
    if not MLX_VENV.exists():
        raise RuntimeError(
            "MLX non installé et LM Studio non disponible.\n"
            "Solutions :\n"
            "  1. Ouvrir LM Studio, charger Qwen2.5-14B, démarrer le serveur (onglet Local Server)\n"
            "  2. Ou installer MLX : ./qwen-mlx.sh --setup && ./qwen-mlx.sh --download 4bit"
        )
    py = MLX_VENV / "bin" / "python3"
    script = f"""
from mlx_lm import load, generate
model, tokenizer = load({json.dumps(model)})
messages = [
    {{"role": "system", "content": {json.dumps(system)}}},
    {{"role": "user",   "content": {json.dumps(prompt)}}}
]
p = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
r = generate(model, tokenizer, prompt=p, max_tokens={max_tokens}, temp={temp}, verbose=False)
print(r)
"""
    res = subprocess.run([str(py), "-c", script],
                         capture_output=True, text=True, timeout=600)
    if res.returncode != 0:
        raise RuntimeError(f"Erreur MLX :\n{res.stderr[:400]}")
    return res.stdout.strip()

def qwen(prompt: str, system: str, model: str,
         max_tokens: int = 3000, temp: float = 0.3) -> str:
    """
    Point d'entrée unique LLM.
    Priorité : LM Studio (serveur local) → MLX direct (venv Python)
    """
    if lm_studio_available():
        return call_lm_studio(prompt, system, max_tokens, temp)
    else:
        return call_mlx(prompt, system, model, max_tokens, temp)

def parse_json(raw: str) -> list | dict:
    raw = raw.strip()
    for delim in ["```json", "```"]:
        if delim in raw:
            raw = raw.split(delim)[1].split("```")[0].strip()
            break
    # Chercher le premier [ ou {
    for char, end in [("[", "]"), ("{", "}")]:
        i = raw.find(char)
        j = raw.rfind(end)
        if i >= 0 and j > i:
            return json.loads(raw[i:j+1])
    return json.loads(raw)

# =============================================================================
# LECTURE DES QUESTIONS EXISTANTES
# =============================================================================

def load_questions(cert_root: Path, cert_id: str, theme_name: str) -> tuple:
    """Retourne (questions, fichier_source) pour un thème."""
    q_dir = cert_root / cert_id / "questions"
    if not q_dir.exists():
        return [], None

    for jf in sorted(q_dir.glob("*.json")):
        try:
            qs = json.loads(jf.read_text(encoding="utf-8"))
            themes = {q.get("theme", "") for q in qs}
            if any(theme_name.lower() in t.lower() for t in themes):
                return qs, jf
        except Exception:
            continue
    return [], None

def get_id_prefix(cert_id: str, theme_name: str) -> str:
    prefixes = {
        "android": "AND", "ocp21": "OCP", "ocp17": "OCP17",
        "java21": "JAVA21", "java17": "JAVA17",
        "aws_ccp": "AWS-CCP", "aws_saa": "AWS-SAA", "aws_dev": "AWS-DEV",
        "az900": "AZ900", "az204": "AZ204",
        "springboot3": "SB3", "spring6": "SP6", "quarkus": "QRK",
        "docker": "DCK", "cka": "CKA", "ckad": "CKAD", "cks": "CKS",
        "terraform": "TF", "ansible": "ANS", "jenkins": "JKS",
        "gitlab": "GL", "github_actions": "GHA",
        "prometheus": "PROM", "grafana": "GRF",
        "elk": "ELK", "datadog": "DD", "newrelic": "NR",
        "sonarqube": "SQ", "devsecops": "DSO",
        "kotlin": "KOT", "gcp_dl": "GCP",
    }
    cp = prefixes.get(cert_id, cert_id[:4].upper())
    words = theme_name.split()
    tp = "".join(w[0].upper() for w in words[:3]) if len(words) >= 2 else theme_name[:3].upper()
    return f"{cp}-{tp}"

def next_seq(qs: list, prefix: str) -> int:
    pat = re.compile(rf"{re.escape(prefix)}-(\d+)$")
    nums = [int(m.group(1)) for q in qs if (m := pat.match(q.get("id", "")))]
    return max(nums, default=0) + 1

# =============================================================================
# VALIDATION D'UNE QUESTION
# =============================================================================

def validate_q(q: dict) -> tuple:
    errors = []
    for f in ["id", "theme", "theme_label", "difficulty", "question", "options"]:
        if not q.get(f):
            errors.append(f"champ manquant: {f}")

    opts = q.get("options", [])
    if len(opts) not in (4, 5):
        errors.append(f"doit avoir 4 ou 5 options (trouvé {len(opts)})")

    is_multi = isinstance(q.get("correct_multiple"), list)
    if is_multi:
        cm = q.get("correct_multiple", [])
        if len(cm) < 2:
            errors.append("correct_multiple: au moins 2 indices")
        for idx in cm:
            if not isinstance(idx, int) or not (0 <= idx < len(opts)):
                errors.append(f"index invalide: {idx}")
    else:
        c = q.get("correct")
        if not isinstance(c, int) or not (0 <= c < len(opts)):
            errors.append(f"correct invalide: {c}")

    expl = q.get("explanation") or q.get("explication", "")
    if len(expl) < 60:
        errors.append("explication trop courte")

    if q.get("difficulty") not in ("easy", "medium", "hard"):
        errors.append(f"difficulty invalide: {q.get('difficulty')}")

    return len(errors) == 0, errors

# =============================================================================
# GÉNÉRATION DES QUESTIONS
# =============================================================================

SYSTEM_QUESTIONS = (
    "Tu es un expert certifié en informatique. "
    "Tu crées du contenu pédagogique de haute qualité pour une plateforme commerciale. "
    "Réponds UNIQUEMENT en JSON valide, sans texte avant ou après."
)

def make_gen_prompt(cert_name, cert_id, theme_name, sub_themes,
                    existing_q, multi_count, single_count,
                    id_prefix, start_num):
    sub_str = "\n".join(
        f"  - {n}: {c} questions existantes"
        for n, c in sorted(sub_themes.items(), key=lambda x: -x[1])
    )
    examples = []
    for q in existing_q[:2]:
        examples.append({
            "id": q.get("id"), "theme_label": q.get("theme_label"),
            "difficulty": q.get("difficulty"), "question": q.get("question"),
            "options": q.get("options"), "correct": q.get("correct"),
            "explanation": (q.get("explanation") or q.get("explication",""))[:120]
        })
    total = multi_count + single_count

    return f"""Certification : {cert_name} ({cert_id})
Thème principal : {theme_name}

Sous-thèmes couverts :
{sub_str}

MISSION : Génère exactement {total} questions d'examen de haute qualité.

RÉPARTITION :
- {multi_count} questions MULTI_CHOICE (plusieurs bonnes réponses)
- {single_count} questions SINGLE_CHOICE (une seule bonne réponse)

RÈGLES MULTI_CHOICE :
- L'énoncé DOIT contenir "Sélectionnez TOUTES les affirmations correctes" OU
  "Quelles sont les DEUX/TROIS propositions vraies concernant..."
- Exactement 2 ou 3 bonnes réponses parmi 4-5 options
- correct = premier index de correct_multiple (rétrocompatibilité)
- correct_multiple = [idx1, idx2] ou [idx1, idx2, idx3]
- L'explication justifie chaque option (correcte ET incorrecte)

RÈGLES SINGLE_CHOICE :
- correct = index 0-3 de la bonne réponse
- correct_multiple = null

RÈGLES QUALITÉ :
- Style officiel de la certification {cert_name}
- Difficulté variée : ~20% easy, ~50% medium, ~30% hard
- Explication min 100 mots couvrant TOUTES les options
- Code Kotlin/Java si pertinent
- IDs : {id_prefix}-{start_num:03d} à {id_prefix}-{start_num+total-1:03d}
- Pas de doublons avec les exemples ci-dessous

EXEMPLES DE STYLE :
{json.dumps(examples, ensure_ascii=False, indent=2)}

FORMAT JSON (tableau de {total} questions) :
[
  {{
    "id": "{id_prefix}-{start_num:03d}",
    "theme": "{theme_name}",
    "theme_label": "sous-thème précis",
    "difficulty": "medium",
    "type": "MULTI_CHOICE",
    "question": "Sélectionnez TOUTES les affirmations correctes concernant X :",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correct": 0,
    "correct_multiple": [0, 2],
    "explanation": "A est correct car... B est incorrect car... C est correct car..."
  }},
  {{
    "id": "{id_prefix}-{start_num+1:03d}",
    "theme": "{theme_name}",
    "theme_label": "sous-thème précis",
    "difficulty": "easy",
    "type": "SINGLE_CHOICE",
    "question": "Question à réponse unique ?",
    "options": ["A", "B", "C", "D"],
    "correct": 1,
    "correct_multiple": null,
    "explanation": "B est correct car..."
  }}
]

Génère UNIQUEMENT le tableau JSON des {total} questions."""


def generate_questions(cert_name, cert_id, theme_name,
                       existing_q, multi_count, single_count,
                       model) -> list:
    sub_themes = {}
    for q in existing_q:
        t = q.get("theme_label", "unknown")
        sub_themes[t] = sub_themes.get(t, 0) + 1

    id_prefix = get_id_prefix(cert_id, theme_name)
    start_num = next_seq(existing_q, id_prefix)

    print(col("b", f"    Génération : {multi_count}M + {single_count}S (IDs depuis {id_prefix}-{start_num:03d})"))

    all_new = []
    rem_multi, rem_single = multi_count, single_count
    batch_num = 0

    while rem_multi > 0 or rem_single > 0:
        batch_num += 1
        bm = min(3, rem_multi)   # Réduit : 5→3 pour éviter HTTP 400 (prompt trop long)
        bs = min(2, rem_single)  # Réduit : 3→2
        if bm + bs == 0:
            break

        print(col("c", f"    Lot {batch_num} : {bm}M + {bs}S..."), end=" ", flush=True)
        t0 = time.time()

        prompt = make_gen_prompt(
            cert_name, cert_id, theme_name, sub_themes,
            existing_q, bm, bs, id_prefix,
            start_num + len(all_new)
        )

        try:
            raw  = qwen(prompt, SYSTEM_QUESTIONS, model, max_tokens=3000, temp=0.35)
            batch = parse_json(raw)
            if not isinstance(batch, list):
                batch = [batch]

            valid = []
            for q in batch:
                is_multi = isinstance(q.get("correct_multiple"), list)
                ok, errs = validate_q(q)
                if ok:
                    q["type"] = "MULTI_CHOICE" if is_multi else "SINGLE_CHOICE"
                    if is_multi:
                        q["correct"] = q["correct_multiple"][0]
                    else:
                        q["correct_multiple"] = None
                    # Normaliser explication / explication → explanation
                    if not q.get("explanation") and q.get("explication"):
                        q["explanation"] = q.pop("explication")
                    valid.append(q)
                else:
                    print(col("y", f"\n      ⚠ {q.get('id','?')} : {'; '.join(errs)}"))

            all_new.extend(valid)
            elapsed = time.time() - t0
            got_m = sum(1 for q in valid if q.get("type") == "MULTI_CHOICE")
            got_s = len(valid) - got_m
            print(col("g", f"✅ {len(valid)}/{bm+bs} ({got_m}M+{got_s}S) en {elapsed:.0f}s"))
            rem_multi  -= bm
            rem_single -= bs

        except urllib.error.HTTPError as e:
            elapsed = time.time() - t0
            print(col("r", f"❌ HTTP {e.code} — prompt trop long, lot réduit automatiquement"))
            # Réessayer avec un seul item si le lot entier échoue
            if bm + bs > 1:
                print(col("y", f"   Réessai avec 1M+1S..."), end=" ", flush=True)
                try:
                    mini_prompt = make_gen_prompt(
                        cert_name, cert_id, theme_name, sub_themes,
                        existing_q, 1, 1, id_prefix,
                        start_num + len(all_new)
                    )
                    raw2 = qwen(mini_prompt, SYSTEM_QUESTIONS, model, max_tokens=2000, temp=0.35)
                    mini_batch = parse_json(raw2)
                    if not isinstance(mini_batch, list):
                        mini_batch = [mini_batch]
                    for q in mini_batch:
                        is_multi = isinstance(q.get("correct_multiple"), list)
                        ok, _ = validate_q(q)
                        if ok:
                            q["type"] = "MULTI_CHOICE" if is_multi else "SINGLE_CHOICE"
                            if is_multi: q["correct"] = q["correct_multiple"][0]
                            else: q["correct_multiple"] = None
                            all_new.append(q)
                    print(col("g", f"✅ {len(mini_batch)} récupérées"))
                except Exception as e2:
                    print(col("r", f"❌ {e2}"))
            rem_multi  -= bm
            rem_single -= bs

        except (json.JSONDecodeError, ValueError) as e:
            elapsed = time.time() - t0
            print(col("r", f"❌ JSON invalide ({elapsed:.0f}s) : {str(e)[:80]}"))
            print(col("y", "   Lot ignoré — passage au suivant"))
            rem_multi  -= bm
            rem_single -= bs

        except Exception as e:
            elapsed = time.time() - t0
            print(col("r", f"❌ {e} ({elapsed:.0f}s)"))
            rem_multi  -= bm
            rem_single -= bs

        time.sleep(1)

    return all_new

# =============================================================================
# GÉNÉRATION DU README
# =============================================================================

SYSTEM_README = (
    "Tu es un expert en certifications informatiques professionnelles. "
    "Tu rédiges de la documentation de haute qualité en français. "
    "Réponds UNIQUEMENT avec le contenu Markdown demandé, sans JSON ni balises."
)

README_TEMPLATE = """# {name}

## 📋 Description

{description}

## 🏛️ Organisme délivrant

**{organisme}**

## 📅 Validité

- **Valable {duree}**
- Renouvellement par examen

## 📚 Programme détaillé

### Thèmes principaux

{themes_table}

### Compétences évaluées

{competences}

## 💼 Reconnaissance professionnelle

### Niveau de reconnaissance

{etoiles}

### Valeur sur le marché

{valeur_marche}

### Salaires indicatifs (France)

{salaires_table}

## 🎯 Objectif

{objectif}

## 🚀 Débouchés

{debouches}

## 📊 Statistiques

- **Taux de réussite** : {taux_reussite}
- **Temps de préparation recommandé** : {duree_prep}
- **Format examen** : {format_examen}

## 🌍 Marchés francophones

{marches_francophones}

## 🔗 Liens utiles

{liens}
"""

def readme_is_complete(path: Path) -> bool:
    """Vérifie si le README contient toutes les sections attendues."""
    if not path.exists():
        return False
    content = path.read_text(encoding="utf-8")
    required = ["## 📋", "## 🏛️", "## 📅", "## 📚", "## 💼", "## 📊", "## 🔗"]
    return all(s in content for s in required)

def generate_readme(cert_id: str, config: dict, model: str) -> str:
    name     = config.get("name", cert_id)
    desc     = config.get("description", "")
    themes   = config.get("themes", [])
    q_count  = config.get("examQuestionCount", "?")
    duration = config.get("examDurationMinutes", "?")
    passing  = config.get("passingScore", "?")

    themes_md = "| Thème | Questions |\n|---|---|\n"
    themes_md += "\n".join(f"| {t['name']} | {t.get('count','?')} |" for t in themes)

    prompt = f"""Génère un fichier README.md COMPLET pour la certification suivante.
Respecte EXACTEMENT le format de l'exemple CKA fourni ci-dessous.

CERTIFICATION :
- ID       : {cert_id}
- Nom      : {name}
- Desc     : {desc}
- Thèmes   : {themes_md}
- Examen   : {q_count} questions, {duration} min, score {passing}%

EXIGENCES :
1. Section "Description" : 2-3 paragraphes sur la valeur de la certification
2. Section "Organisme" : nom officiel + contexte
3. Section "Validité" : durée + renouvellement
4. Section "Programme" : tableau des thèmes + liste des compétences évaluées
5. Section "Reconnaissance" :
   - Niveau ⭐ (1-5 étoiles selon la notoriété)
   - Valeur dans les pays francophones : France, Maroc, Tunisie, Sénégal, Québec, Suisse, Belgique
6. Section "Salaires" : tableau France avec Junior/Confirmé/Senior ET fourchettes Québec
7. Section "Objectif" : 1-2 phrases percutantes
8. Section "Débouchés" : liste de 4-6 métiers avec salaires indicatifs
9. Section "Statistiques" : taux de réussite, durée de préparation, format examen
10. Section "Marchés francophones" : potentiel et tendances par région
11. Section "Liens" : liens officiels de la certification

EXEMPLE DE FORMAT (certification CKA) :
```
# Certified Kubernetes Administrator (CKA)

## 📋 Description
La certification CKA (Certified Kubernetes Administrator) est une certification **pratique**...

## 🏛️ Organisme délivrant
**Cloud Native Computing Foundation (CNCF)** - Fondation open source sous la Linux Foundation

## 📅 Validité
- **Valable 2 ans**
- Renouvellement par examen ou certification supérieure (CKS)

## 📚 Programme détaillé
### Thèmes principaux
| Thème | Pourcentage |
|---|---|
| Cluster Architecture | 25% |

### Compétences évaluées
- Installer et configurer un cluster Kubernetes
...

## 💼 Reconnaissance professionnelle
### Niveau de reconnaissance
⭐⭐⭐⭐⭐ (Très élevé - Standard de facto pour Kubernetes)

### Valeur sur le marché
- **France** : Très recherchée...

### Salaires indicatifs (France)
| Expérience | Salaire moyen |
|---|---|
| DevOps Junior K8s | 50 000 - 60 000 € |

## 📊 Statistiques
- **Taux de réussite** : ~60%
- **Temps de préparation recommandé** : 100-150 heures
- **Format examen** : Pratique (terminal), 17 questions, 120 minutes

## 🌍 Marchés francophones
[détails par région]

## 🔗 Liens utiles
- [CNCF CKA](https://www.cncf.io/certification/cka/)
```

Génère le README complet pour {name}. Réponds UNIQUEMENT avec le contenu Markdown."""

    return qwen(prompt, SYSTEM_README, model, max_tokens=3000, temp=0.4)

# =============================================================================
# TRAITEMENT D'UNE CERTIFICATION
# =============================================================================

def process_cert(cert_root, cert_id, config, theme_filter,
                 ratio, model, do_questions, do_readme,
                 preview, apply, status, output_dir) -> dict:

    cert_name = config.get("name", cert_id)
    themes    = config.get("themes", [])
    results   = {"questions": {}, "readme": None}

    # ── README ────────────────────────────────────────────────────────────────
    if do_readme:
        readme_path = cert_root / cert_id / "README.md"

        if is_done(status, cert_id, "readme") and readme_is_complete(readme_path):
            print(col("y", f"  ⏭  README déjà complet ({readme_path.name})"))
        else:
            incomplete = not readme_is_complete(readme_path)
            reason = "absent" if not readme_path.exists() else "incomplet"
            print(col("b", f"  📝 README {reason} → génération..."))

            if not preview:
                try:
                    t0  = time.time()
                    md  = generate_readme(cert_id, config, model)
                    elapsed = time.time() - t0

                    # Écrire dans le dossier de la certification
                    if apply:
                        readme_path.write_text(md, encoding="utf-8")
                        print(col("g", f"  ✅ README écrit : {readme_path} ({elapsed:.0f}s)"))
                    else:
                        out = output_dir / f"{cert_id}_README.md"
                        out.write_text(md, encoding="utf-8")
                        print(col("g", f"  ✅ README généré : {out} ({elapsed:.0f}s)"))

                    mark_done(status, cert_id, "readme",
                              {"file": str(readme_path), "elapsed": elapsed})
                    results["readme"] = "generated"
                except Exception as e:
                    print(col("r", f"  ❌ README erreur : {e}"))
                    results["readme"] = f"error: {e}"
            else:
                print(col("m", f"  [PREVIEW] README serait {'écrasé' if not incomplete else 'créé'}"))
                results["readme"] = "preview"

    # ── QUESTIONS ─────────────────────────────────────────────────────────────
    if do_questions:
        if is_done(status, cert_id, "questions") and not preview:
            print(col("y", "  ⏭  Questions déjà enrichies (utilise --force pour refaire)"))
            results["questions"] = {"status": "already_done"}
        else:
            theme_results = {}

            for theme_info in themes:
                theme_name   = theme_info.get("name", "")
                theme_count  = theme_info.get("count", 0)

                if theme_filter and theme_filter.lower() not in theme_name.lower():
                    continue

                print(col("b", f"\n  ── {theme_name} (config: {theme_count}q) ──"))

                existing_q, src_file = load_questions(cert_root, cert_id, theme_name)

                if not existing_q:
                    print(col("y", f"    ⚠ Aucune question pour '{theme_name}'"))
                    theme_results[theme_name] = {"status": "not_found"}
                    continue

                current  = len(existing_q)
                to_add   = math.ceil(current * ratio / 100)
                multi_n  = math.ceil(to_add * MULTI_PERCENT  / 100)
                single_n = to_add - multi_n

                # Vérifier les multi déjà présentes
                already_multi = sum(
                    1 for q in existing_q
                    if isinstance(q.get("correct_multiple"), list)
                )

                print(f"    Existant : {current}q ({already_multi} multi déjà) | fichier : {src_file.name}")
                print(f"    À ajouter : +{to_add} (+{ratio}%) → {multi_n}M + {single_n}S")

                if preview:
                    print(col("m", "    [PREVIEW] Pas d'appel MLX"))
                    theme_results[theme_name] = {"status": "preview", "would_add": to_add}
                    continue

                new_q = generate_questions(
                    cert_name, cert_id, theme_name,
                    existing_q, multi_n, single_n, model
                )

                if not new_q:
                    theme_results[theme_name] = {"status": "failed"}
                    continue

                got_m = sum(1 for q in new_q if q.get("type") == "MULTI_CHOICE")
                got_s = len(new_q) - got_m
                print(col("g", f"    ✅ {len(new_q)} générées : {got_m}M + {got_s}S"))

                # Sauvegarder
                # Nettoyer le nom de thème : supprimer / \ : * ? " < > | et espaces
                safe_theme = theme_name.lower()
                import re as _re
                safe_theme = _re.sub(r'[/\\:*?"<>|\s]+', '_', safe_theme)
                safe_theme = safe_theme.strip('_')
                out_file = output_dir / f"{cert_id}_{safe_theme}_new.json"
                out_file.parent.mkdir(parents=True, exist_ok=True)
                out_file.write_text(
                    json.dumps(new_q, ensure_ascii=False, indent=2),
                    encoding="utf-8"
                )
                print(col("b", f"    💾 Généré : {out_file}"))

                # Intégrer si --apply
                if apply and src_file:
                    merged = existing_q + new_q
                    src_file.write_text(
                        json.dumps(merged, ensure_ascii=False, indent=2),
                        encoding="utf-8"
                    )
                    print(col("g", f"    ✅ Intégré dans {src_file.name} ({len(merged)}q total)"))

                theme_results[theme_name] = {
                    "status": "ok",
                    "existing": current, "added": len(new_q),
                    "multi": got_m, "single": got_s,
                    "output": str(out_file)
                }

            results["questions"] = theme_results

            # Marquer comme fait seulement si toutes les thèmes ont été traités
            done_count = sum(1 for v in theme_results.values() if v.get("status") == "ok")
            if done_count > 0 and not theme_filter:
                mark_done(status, cert_id, "questions", {"themes": done_count})

    return results

# =============================================================================
# MENU INTERACTIF
# =============================================================================

def interactive_menu(cert_root: Path, status: dict) -> tuple:
    """Retourne (cert_ids, options) via menu interactif."""
    available = list_certs(cert_root)

    print(col("c", "\n╔══════════════════════════════════════════════════════════════════╗"))
    print(col("c",   "║            CertifApp — Enrichissement certifications            ║"))
    print(col("c",   "╚══════════════════════════════════════════════════════════════════╝\n"))

    # Afficher les certifications avec leur statut
    print(col("w", "Certifications disponibles :\n"))
    by_status = {"todo": [], "partial": [], "done": []}
    for cert_id in available:
        q_done = is_done(status, cert_id, "questions")
        r_done = is_done(status, cert_id, "readme")
        if q_done and r_done:
            by_status["done"].append(cert_id)
        elif q_done or r_done:
            by_status["partial"].append(cert_id)
        else:
            by_status["todo"].append(cert_id)

    for i, cert_id in enumerate(available, 1):
        q_done = is_done(status, cert_id, "questions")
        r_done = is_done(status, cert_id, "readme")
        if q_done and r_done:
            icon = col("g", "✅")
        elif q_done or r_done:
            icon = col("y", "⚡")
        else:
            icon = col("r", "⬜")
        print(f"  {i:2d}. {icon}  {cert_id}")

    print()
    print("Légende : ✅ complété  ⚡ partiel  ⬜ non traité")
    print()
    print("Commandes :")
    print("  Numéros séparés par espace (ex: 1 3 5)")
    print("  'all'    → toutes les certifications")
    print("  'todo'   → uniquement les non traitées")
    print("  'q'      → quitter")
    print()

    choice = input("Votre choix : ").strip().lower()
    if choice == "q":
        sys.exit(0)
    if choice == "all":
        selected = available
    elif choice == "todo":
        selected = by_status["todo"]
    else:
        indices = []
        for part in choice.split():
            try:
                idx = int(part) - 1
                if 0 <= idx < len(available):
                    indices.append(idx)
            except ValueError:
                # C'est peut-être un ID direct
                if part in available:
                    selected_ids = [part]
                    break
        else:
            selected = [available[i] for i in indices]

    if not selected:
        print(col("y", "Aucune certification sélectionnée."))
        sys.exit(0)

    print(col("g", f"\nSélectionnées : {', '.join(selected)}"))
    print()

    # Options
    do_q = input("Générer les questions multi-réponses ? [O/n] : ").strip().lower() != "n"
    do_r = input("Compléter/générer les README ?          [O/n] : ").strip().lower() != "n"

    apply_choice = input("Intégrer directement dans le projet ? [o/N] : ").strip().lower()
    do_apply = apply_choice in ("o", "y", "oui", "yes")

    force = input("Refaire même si déjà traité ?          [o/N] : ").strip().lower()
    do_force = force in ("o", "y", "oui", "yes")

    return selected, {
        "do_questions": do_q,
        "do_readme": do_r,
        "apply": do_apply,
        "force": do_force,
    }

# =============================================================================
# RAPPORT FINAL
# =============================================================================

def print_report(all_results: dict):
    print(col("c", "\n╔══════════════════════════════════════════════════════════════════╗"))
    print(col("c",   "║                       RAPPORT FINAL                            ║"))
    print(col("c",   "╚══════════════════════════════════════════════════════════════════╝\n"))

    for cert_id, res in all_results.items():
        print(col("w", f"  {cert_id}"))

        # README
        readme_r = res.get("readme")
        if readme_r == "generated":
            print(col("g",  "    README    : ✅ généré"))
        elif readme_r == "preview":
            print(col("m",  "    README    : 👁 preview"))
        elif readme_r and readme_r.startswith("error"):
            print(col("r",  f"    README    : ❌ {readme_r}"))

        # Questions
        q_res = res.get("questions", {})
        if isinstance(q_res, dict):
            total_added = sum(
                v.get("added", 0) for v in q_res.values()
                if isinstance(v, dict) and v.get("status") == "ok"
            )
            total_multi = sum(
                v.get("multi", 0) for v in q_res.values()
                if isinstance(v, dict)
            )
            if total_added > 0:
                pct = round(total_multi * 100 / total_added) if total_added else 0
                print(col("g", f"    Questions : ✅ +{total_added} ({pct}% multi)"))
                for theme, tv in q_res.items():
                    if isinstance(tv, dict) and tv.get("status") == "ok":
                        print(f"      {theme:<35} +{tv.get('added',0):3d} "
                              f"({tv.get('multi',0)}M+{tv.get('single',0)}S)")
            elif q_res.get("status") == "already_done":
                print(col("y",  "    Questions : ⏭  déjà traité"))

        print()

# =============================================================================
# POINT D'ENTRÉE
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Enrichit les certifications CertifApp (questions multi + README)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemples :
  python3 certif-enricher.py                          # menu interactif
  python3 certif-enricher.py --cert android
  python3 certif-enricher.py --cert android cka terraform
  python3 certif-enricher.py --all
  python3 certif-enricher.py --cert android --theme "Security"
  python3 certif-enricher.py --cert android --readme-only
  python3 certif-enricher.py --cert android --questions-only
  python3 certif-enricher.py --cert android --preview
  python3 certif-enricher.py --cert android --apply       # intègre dans le projet
  python3 certif-enricher.py --status                      # voir l'état
  python3 certif-enricher.py --reset android               # réinitialiser
        """
    )
    parser.add_argument("--cert",           nargs="+",  metavar="ID")
    parser.add_argument("--all",            action="store_true")
    parser.add_argument("--theme",          metavar="THEME")
    parser.add_argument("--ratio",          type=int,   default=DEFAULT_RATIO)
    parser.add_argument("--model",          choices=["4bit","8bit"], default="auto")
    parser.add_argument("--preview",        action="store_true")
    parser.add_argument("--apply",          action="store_true")
    parser.add_argument("--force",          action="store_true",
                        help="Refaire même si déjà traité")
    parser.add_argument("--readme-only",    action="store_true",  dest="readme_only")
    parser.add_argument("--questions-only", action="store_true",  dest="questions_only")
    parser.add_argument("--status",         action="store_true")
    parser.add_argument("--reset",          nargs="+",  metavar="CERT_ID")
    parser.add_argument("--output",         default="./certif-generated")
    parser.add_argument("--cert-dir",       metavar="PATH", dest="cert_dir")

    args = parser.parse_args()

    # Localiser le projet
    try:
        cert_root = find_cert_root(args.cert_dir)
    except FileNotFoundError as e:
        print(col("r", f"❌ {e}")); sys.exit(1)

    status     = load_status()
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # ── Commandes spéciales ───────────────────────────────────────────────────
    if args.status:
        print_status_table(status, cert_root)
        return

    if args.reset:
        for cid in args.reset:
            status.pop(cid, None)
            print(col("g", f"✅ Statut réinitialisé : {cid}"))
        save_status(status)
        return

    # ── Sélection des certifications ──────────────────────────────────────────
    interactive = not args.cert and not args.all

    if interactive:
        cert_ids, opts = interactive_menu(cert_root, status)
        do_q   = opts["do_questions"]
        do_r   = opts["do_readme"]
        apply  = opts["apply"]
        force  = opts["force"]
    else:
        cert_ids = list_certs(cert_root) if args.all else args.cert
        do_q   = not args.readme_only
        do_r   = not args.questions_only
        apply  = args.apply
        force  = args.force

    do_r = do_r if not args.questions_only else False
    do_q = do_q if not args.readme_only    else False

    if not cert_ids:
        print(col("r", "❌ Aucune certification sélectionnée")); sys.exit(1)

    # Sélectionner le modèle
    model = (MODEL_8BIT if args.model == "8bit" else
             MODEL_4BIT if args.model == "4bit" else
             auto_model())

    # Header
    print(col("c", "\n╔══════════════════════════════════════════════════════════════════╗"))
    print(col("c",   "║            CertifApp — Enrichissement certifications            ║"))
    print(col("c",   "╚══════════════════════════════════════════════════════════════════╝"))
    print(col("b",  f"\nCertifications : {', '.join(cert_ids)}"))
    print(col("b",  f"Dossier        : {cert_root}"))
    print(col("b",  f"Modèle MLX     : {model}"))
    print(col("b",  f"Mode           : {'preview' if args.preview else 'apply' if apply else 'generate-only'}"))
    print(col("b",  f"Ratio          : +{args.ratio}% ({MULTI_PERCENT}%M / {SINGLE_PERCENT}%S)"))
    print()

    # Détecter et afficher le backend LLM disponible
    if do_q and not args.preview:
        backend_name, backend_url, backend_model = detect_backend()
        if backend_name == "lmstudio":
            print(col("g", f"✅ LM Studio actif        — modèle : {backend_model}"))
        elif backend_name == "ollama":
            print(col("g", f"✅ Ollama actif           — modèle : {backend_model}"))
        elif backend_name == "mlx":
            print(col("g", f"✅ MLX disponible         — modèle : {model}"))
        else:
            print(col("r", "❌ Aucun backend LLM disponible."))
            print()
            print(col("y", "  ── Option 1 : LM Studio ──────────────────────────────────────"))
            print(col("y", "     1. Ouvrir LM Studio"))
            print(col("y", "     2. Onglet Search → télécharger 'Qwen2.5-14B-Instruct MLX 4bit'"))
            print(col("y", "     3. Onglet Local Server → sélectionner le modèle → Start Server"))
            print(col("y", "     4. Vérifier : curl http://localhost:1234/v1/models"))
            print()
            print(col("y", "  ── Option 2 : Ollama (déjà installé) ─────────────────────────"))
            print(col("y", "     ollama serve  (dans un terminal séparé)"))
            print(col("y", "     # Modèles disponibles sur ton Mac :"))
            print(col("y", "     # qwen2.5-coder:7b, deepseek-coder:6.7b, llama3, codellama"))
            print(col("b", "     → Pour utiliser Ollama, modifier OLLAMA_MODEL dans le script"))
            print()
            print(col("y", "  ── Option 3 : MLX direct ──────────────────────────────────────"))
            print(col("y", "     ./qwen-mlx.sh --setup && ./qwen-mlx.sh --download 4bit"))
            sys.exit(1)

    # Confirmation --apply
    if apply and not args.preview:
        print(col("y", "⚠️  Mode --apply : les fichiers source du projet seront modifiés !"))
        if input("Confirmer ? (o/N) : ").strip().lower() not in ("o", "y"):
            print("Annulé."); sys.exit(0)

    # ── Traitement ────────────────────────────────────────────────────────────
    all_results = {}
    for cert_id in cert_ids:
        print(col("c", f"\n{'═'*60}"))
        print(col("c", f"  {cert_id}"))
        print(col("c", f"{'═'*60}"))

        try:
            config = load_config(cert_root, cert_id)
        except FileNotFoundError as e:
            print(col("r", f"  ❌ {e}")); continue

        print(f"  {config.get('name',cert_id)}")

        # Sauter si déjà traité et pas --force
        if not force:
            q_skip = do_q and is_done(status, cert_id, "questions")
            r_skip = do_r and is_done(status, cert_id, "readme") and \
                     readme_is_complete(cert_root / cert_id / "README.md")
            if q_skip and r_skip:
                print(col("y", "  ⏭  Déjà complètement traité. Utilise --force pour refaire."))
                all_results[cert_id] = {"status": "skipped"}
                continue

        result = process_cert(
            cert_root, cert_id, config,
            theme_filter=args.theme,
            ratio=args.ratio, model=model,
            do_questions=do_q, do_readme=do_r,
            preview=args.preview, apply=apply,
            status=status, output_dir=output_dir
        )
        all_results[cert_id] = result

    # ── Rapport ───────────────────────────────────────────────────────────────
    print_report(all_results)

    report_path = output_dir / f"report-{int(time.time())}.json"
    report_path.write_text(
        json.dumps(all_results, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )
    print(col("b", f"📋 Rapport JSON : {report_path}"))

    if not apply and not args.preview:
        print(col("y", f"\nℹ️  Fichiers générés dans : {output_dir}/"))
        print(col("y", "   Ajoute --apply pour les intégrer dans le projet."))

if __name__ == "__main__":
    main()
