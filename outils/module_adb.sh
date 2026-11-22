#!/bin/bash
# Fabrique le module Magisk « ADB Wi-Fi » avec la clé publique ADB de CET ordinateur.
#
#   bash outils/module_adb.sh
#
# Résultat : modules/adb-wifi-ma-cle.zip, à installer dans Magisk (guide, étape 7).
# La clé publique (~/.android/adbkey.pub) n'a rien de secret. Ne partage jamais adbkey sans « .pub ».
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
KEY="$HOME/.android/adbkey.pub"
if [ ! -s "$KEY" ]; then
  # adb crée la clé la première fois qu'il démarre.
  for a in adb "$HOME/platform-tools/adb"; do command -v "$a" >/dev/null 2>&1 && "$a" start-server >/dev/null 2>&1 && break; done
fi
[ -s "$KEY" ] || { echo "Clé introuvable : $KEY. Lance une fois « ~/platform-tools/adb devices » puis recommence."; exit 1; }
command -v zip >/dev/null || { echo "La commande zip manque (sous Linux : sudo apt install zip)."; exit 1; }
T=$(mktemp -d)
cp -R "$RACINE/modules/adb-wifi/." "$T/"
rm -f "$T/.gitignore"
cp "$KEY" "$T/adb_keys"
chmod 755 "$T/service.sh" "$T/META-INF/com/google/android/update-binary"
OUT="$RACINE/modules/adb-wifi-ma-cle.zip"
rm -f "$OUT"
(cd "$T" && zip -q -r -X "$OUT" module.prop service.sh adb_keys META-INF)
rm -rf "$T"
echo "Module prêt : $OUT"
