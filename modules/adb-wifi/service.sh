#!/system/bin/sh
# ADB Wi-Fi (Toque) : après le démarrage, autorise les clés publiques fournies avec le module
# (fichier adb_keys), puis relance adbd en écoute TCP sur le port 5555.
# ro.adb.secure n'est pas touché : tout autre ordinateur doit être accepté sur l'écran du robot.
# Pour désactiver : désactiver le module dans Magisk et redémarrer (les clés déjà ajoutées
# restent dans /data/misc/adb/adb_keys ; « Révoquer les autorisations » dans les options
# pour les développeurs les efface).
MODDIR=${0%/*}
KEYS=/data/misc/adb/adb_keys
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 5
added=0
if [ -s "$MODDIR/adb_keys" ]; then
  mkdir -p /data/misc/adb
  [ -f "$KEYS" ] || touch "$KEYS"
  while IFS= read -r key; do
    [ -n "$key" ] || continue
    if ! grep -qF "${key%% *}" "$KEYS"; then
      echo "$key" >> "$KEYS"
      added=$((added + 1))
    fi
  done < "$MODDIR/adb_keys"
  chown system:shell "$KEYS"
  chmod 640 "$KEYS"
  restorecon "$KEYS" 2>/dev/null
fi
setprop service.adb.tcp.port 5555
stop adbd
start adbd
echo "$(date) cles_ajoutees=$added port=$(getprop service.adb.tcp.port) usb=$(getprop sys.usb.config) adbd=$(getprop init.svc.adbd)" > "$MODDIR/dernier_demarrage.log"
