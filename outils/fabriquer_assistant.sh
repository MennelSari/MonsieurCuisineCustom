#!/bin/bash
# Fabrique l'assistant Claude « prêt à l'emploi » : camybrith-assistant-claude.zip
# (la compétence assistant/camybrith-installer avec le guide à jour dedans).
#   bash outils/fabriquer_assistant.sh [dossier_de_sortie]
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$RACINE/assistant}
T=$(mktemp -d)
S="$T/camybrith-installer"
mkdir -p "$S/references"
cp "$RACINE/assistant/camybrith-installer/SKILL.md" "$S/"
cp "$RACINE/docs/GUIDE.md" "$RACINE/docs/GUIDE.en.md" "$RACINE/docs/RECETTES.md" "$RACINE/docs/RECIPES.en.md" "$RACINE/docs/CONNECT.md" "$RACINE/docs/CONNECT.en.md" "$S/references/"
cp "$RACINE/personnalisation/LISEZMOI.md" "$S/references/PERSONNALISATION.md"
cp "$RACINE/personnalisation/README.en.md" "$S/references/CUSTOMIZATION.en.md"
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
rm -f "$OUT/camybrith-assistant-claude.zip"
(cd "$T" && zip -q -r -X "$OUT/camybrith-assistant-claude.zip" camybrith-installer)
rm -rf "$T"
echo "Assistant prêt : $OUT/camybrith-assistant-claude.zip"
