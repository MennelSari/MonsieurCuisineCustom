#!/bin/bash
# Transforme une animation de démarrage (bootanimation.zip) en module Magisk installable.
#
#   bash personnalisation/fabriquer_module_animation.sh                 → prend personnalisation/bootanimation.zip
#   bash personnalisation/fabriquer_module_animation.sh mon_anim.zip    → prend ce fichier
#
# Résultat : modules/camybrith_animation-perso.zip, à installer avec
#   bash outils/animation.sh installer modules/camybrith_animation-perso.zip
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
SRC=${1:-$RACINE/personnalisation/bootanimation.zip}
[ -s "$SRC" ] || { echo "Fichier introuvable : $SRC"; exit 1; }
SRC="$(cd "$(dirname "$SRC")" && pwd)/$(basename "$SRC")"
command -v zip >/dev/null && command -v unzip >/dev/null || { echo "Il faut les commandes zip et unzip."; exit 1; }
unzip -p "$SRC" desc.txt >/dev/null 2>&1 || { echo "$SRC n'est pas une animation de démarrage (desc.txt absent)."; exit 1; }
T=$(mktemp -d)
cp -R "$RACINE/modules/animation/." "$T/"
mkdir -p "$T/system/media"
# Android lit l'animation sans la décompresser : les images doivent être stockées sans compression.
mkdir "$T/a" && (cd "$T/a" && unzip -q "$SRC" && zip -q -0 -X -r "$T/system/media/bootanimation.zip" desc.txt $(ls -d part* | sort) $(ls audio* 2>/dev/null || true)) && rm -rf "$T/a"
chmod 644 "$T/system/media/bootanimation.zip"
chmod 755 "$T/META-INF/com/google/android/update-binary"
OUT="$RACINE/modules/camybrith_animation-perso.zip"
rm -f "$OUT"
(cd "$T" && zip -q -r -X "$OUT" module.prop system META-INF)
rm -rf "$T"
echo "Module prêt : $OUT"
echo "Installe-le avec : bash outils/animation.sh installer $OUT"
