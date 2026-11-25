#!/system/bin/sh
# Lancé à chaque démarrage par mcroot.rc (argument : dossier de Magisk) (root, contexte Magisk). Remplace l'étape TWRP :
# 1. la première fois, installe navbar, Magisk et FTP Server depuis /cache ;
# 2. à chaque démarrage, autorise la clé ADB du Mac et ouvre ADB en Wi-Fi (port 5555) ;
# 3. donne le root au shell ADB dans Magisk.
# Journal : /cache/mcroot.log
MAGISKTMP=${1:-/sbin}
exec >> /cache/mcroot.log 2>&1
echo "=== $(date) début (Magisk dans $MAGISKTMP)"
sleep 15

FAIT=/data/adb/mcroot_applis_installees
if [ ! -f "$FAIT" ]; then
  ok=1
  for a in navbar magisk ftpserver; do
    src=/cache/$a.apk
    if [ ! -f "$src" ]; then echo "absent : $src"; ok=0; continue; fi
    tmp=/data/local/tmp/mcroot_$a.apk
    cp "$src" "$tmp" && chown 2000:2000 "$tmp" && chmod 644 "$tmp"
    chcon u:object_r:shell_data_file:s0 "$tmp"
    echo "installation de $a :"
    pm install -r "$tmp" || ok=0
    rm -f "$tmp"
  done
  if [ $ok = 1 ]; then touch "$FAIT"; echo "applis installées"; else echo "au moins une installation a échoué, nouvel essai au prochain démarrage"; fi
fi

KEYS=/data/misc/adb/adb_keys
mkdir -p /data/misc/adb
[ -f "$KEYS" ] || touch "$KEYS"
while IFS= read -r key; do
  [ -n "$key" ] || continue
  grep -qF "${key%% *}" "$KEYS" || { echo "$key" >> "$KEYS"; echo "clé ADB ajoutée"; }
done < /mcroot_adb_keys
chown 1000:2000 "$KEYS"; chmod 640 "$KEYS"; restorecon "$KEYS"
settings put global adb_enabled 1
setprop service.adb.tcp.port 5555
stop adbd
start adbd

"$MAGISKTMP/magisk" --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES(2000,2,0,1,1)" && echo "root accordé au shell ADB"
echo "fin : adbd=$(getprop init.svc.adbd) port=$(getprop service.adb.tcp.port) ip=$(ip -4 addr show wlan0 2>/dev/null | grep -o 'inet [0-9.]*')"
