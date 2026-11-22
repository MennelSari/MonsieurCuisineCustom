#!/bin/bash
# Étape 3 du guide : glisse dans ta copie de la partition cache les fichiers dont le robot aura
# besoin (ton boot d'origine et les trois applis). Ta sauvegarde n'est pas modifiée : le script
# travaille sur une copie, cache_avec_applis.img.
#
#   bash outils/preparer_cache.sh  dossier_de_sauvegarde  dossier_des_apk
#
# dossier_de_sauvegarde doit contenir cache.img et boot.img (lus avec SP Flash Tool, étape 2).
# dossier_des_apk doit contenir magisk.apk, navbar.apk et httpserver.apk (étape 1).
# Il faut les outils e2fsprogs : sous Windows dans Ubuntu (WSL) « sudo apt install e2fsprogs »,
# sur Mac « brew install e2fsprogs ».
set -eu
SAUV=${1:?Usage : bash $0 dossier_de_sauvegarde dossier_des_apk}
APKS=${2:?Usage : bash $0 dossier_de_sauvegarde dossier_des_apk}
for d in /opt/homebrew/opt/e2fsprogs/sbin /usr/local/opt/e2fsprogs/sbin /sbin /usr/sbin; do [ -d "$d" ] && PATH="$d:$PATH"; done
command -v debugfs >/dev/null && command -v e2fsck >/dev/null || { echo "debugfs et e2fsck manquent : installe e2fsprogs (voir en tête du script)."; exit 1; }

taille() { wc -c < "$1" | tr -d ' '; }
[ "$(taille "$SAUV/cache.img")" = 314572800 ] || { echo "$SAUV/cache.img ne fait pas 314 572 800 octets : relis-le (étape 2)."; exit 1; }
[ "$(taille "$SAUV/boot.img")" = 16777216 ] || { echo "$SAUV/boot.img ne fait pas 16 777 216 octets : relis-le (étape 2)."; exit 1; }
[ "$(head -c 8 "$SAUV/boot.img")" = "ANDROID!" ] || { echo "$SAUV/boot.img n'est pas une image de démarrage Android : relis-la (étape 2)."; exit 1; }
for f in magisk navbar httpserver; do [ -s "$APKS/$f.apk" ] || { echo "Il manque $APKS/$f.apk (étape 1)."; exit 1; }; done

OUT="$SAUV/cache_avec_applis.img"
T=$(mktemp -d)
cp "$SAUV/cache.img" "$OUT"
cp "$SAUV/boot.img" "$T/boot.img"
for f in magisk navbar httpserver; do cp "$APKS/$f.apk" "$T/$f.apk"; done
chmod 644 "$T"/*

# Le cache lu sur un robot allumé a souvent un journal à rejouer : on le remet d'aplomb d'abord.
e2fsck -fy "$OUT" >/dev/null 2>&1 || true
for f in boot.img magisk.apk navbar.apk httpserver.apk; do
  debugfs -w -R "rm /$f" "$OUT" >/dev/null 2>&1 || true
  debugfs -w -R "write $T/$f $f" "$OUT" >/dev/null 2>&1
done
mkdir "$T/relu"
for f in boot.img magisk.apk navbar.apk httpserver.apk; do
  debugfs -R "dump /$f $T/relu/$f" "$OUT" >/dev/null 2>&1
  cmp -s "$T/$f" "$T/relu/$f" || { echo "ÉCHEC : $f relu différent. N'utilise pas $OUT."; rm -rf "$T"; exit 1; }
  echo "  $f : écrit et relu, identique"
done
if ! e2fsck -fn "$OUT" >/dev/null 2>&1; then echo "ÉCHEC : le système de fichiers a une erreur. N'utilise pas $OUT."; rm -rf "$T"; exit 1; fi
rm -rf "$T"
echo "Image prête : $OUT (même taille que la partition, système de fichiers vérifié)."
