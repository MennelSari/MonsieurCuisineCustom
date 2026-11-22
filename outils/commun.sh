# Partie commune aux scripts de ce dossier (à ne pas lancer seul).
# Trouve adb, l'adresse du robot, et vérifie que le robot répond.
#
# L'adresse est demandée une fois puis gardée dans outils/robot.conf (fichier propre à ton
# installation, jamais envoyé sur GitHub). Pour la changer : modifie ce fichier ou supprime-le.
# Tu peux aussi la donner pour une seule commande : IP=192.168.1.50 bash outils/installer.sh

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RACINE=$(cd "$DIR/.." && pwd)

if command -v adb >/dev/null 2>&1; then ADB=adb
elif [ -x "$HOME/platform-tools/adb" ]; then ADB="$HOME/platform-tools/adb"
elif [ -x "$RACINE/platform-tools/adb" ]; then ADB="$RACINE/platform-tools/adb"
else
  echo "Je ne trouve pas adb. Télécharge les « Platform Tools » de Google (voir le guide, étape 7)"
  echo "et décompresse-les dans ton dossier personnel : il faut un fichier ~/platform-tools/adb."
  exit 1
fi

CONF="$DIR/robot.conf"
if [ -z "${IP:-}" ] && [ -s "$CONF" ]; then . "$CONF"; fi
if [ -z "${IP:-}" ]; then
  echo "Adresse IP du robot sur ton réseau Wi-Fi (exemple : 192.168.1.42)."
  echo "Tu la trouves dans les réglages Android du robot (Wi-Fi > ton réseau) ou sur ta box."
  read -r -p "Adresse du robot : " IP
  IP=$(echo "$IP" | tr -d ' ')
  case "$IP" in *[!0-9.]*|'') echo "Adresse invalide : « $IP »."; exit 1 ;; esac
  echo "IP=$IP" > "$CONF"
  echo "(adresse gardée dans outils/robot.conf)"
fi

A="$ADB -s $IP:5555"

connecter() {
  "$ADB" connect "$IP:5555" >/dev/null 2>&1
  if ! $A shell true >/dev/null 2>&1; then
    echo "Le robot ne répond pas sur $IP:5555."
    echo "Vérifie qu'il est allumé, sur le même Wi-Fi que l'ordinateur, que le module « ADB Wi-Fi »"
    echo "est actif dans Magisk, et que ta clé a bien été ajoutée (guide, étape 7)."
    echo "Si l'adresse a changé : supprime outils/robot.conf et relance."
    exit 1
  fi
}

root_ok() {
  $A shell "su -c id" 2>/dev/null | grep -q "uid=0"
}

exiger_root() {
  if ! root_ok; then
    echo "Le robot refuse l'accès root à l'ordinateur."
    echo "Dans Magisk sur le robot : onglet Superutilisateur, bouton +, choisis « Shell » et active-le (guide, étape 7)."
    exit 1
  fi
}
