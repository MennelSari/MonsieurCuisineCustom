# Rooter le Smart depuis un Mac, sans TWRP

> ⚠️ **Méthode avancée, à tes risques.** Écrire dans la mémoire du robot peut le rendre
> inutilisable. Fais d'abord une **sauvegarde complète** et relis chaque commande. Si tu ne
> comprends pas une étape, arrête-toi et demande de l'aide (voir « Se faire aider par une IA » dans
> le README).

Le guide de Camybrith ([../GUIDE.md](../GUIDE.md)) passe par TWRP pour copier des fichiers. Sur
certains Smart, **le tactile de TWRP ne répond pas** (l'écran est tourné et aucun appui n'est pris
en compte). Cette variante s'en passe : on patche le démarrage (`boot`) avec Magisk **sur le Mac**,
et on y ajoute un petit script qui, au premier démarrage, installe les applis et ouvre l'ADB Wi-Fi.

Testé sur un SKMS 1200 A1, Android 8.1 (V01.00.83), appli Lidl 2.22, avec un Mac Apple Silicon.

## Outils

* [mtkclient](https://github.com/bkerler/mtkclient) pour lire et écrire la mémoire par USB
  (câble **de données** sur le port « Android USB », jamais « Power PCB USB ») ;
* l'APK officiel de [Magisk](https://github.com/topjohnwu/Magisk/releases) ;
* un Linux ARM64 (Docker ou colima sur Mac) pour lancer `magiskboot` ;
* `e2fsprogs` (`brew install e2fsprogs`) pour écrire dans l'image de la partition `cache`.

## Étapes

1. **Sauvegarde** : lis avec mtkclient toutes les partitions système (au minimum `boot`, `cache`,
   `nvram`, `proinfo`, `system`, `vendor`, `seccfg`, `lk`, `boot1`/`boot2`) et vérifie leur taille.
   **Ne jamais écrire** `preloader`, `system`, `nvram` ni `proinfo`.
2. **Cache avec les applis** : copie de `cache.img` dans laquelle on ajoute `magisk.apk`,
   `navbar.apk` et `ftpserver.apk` (ou `httpserver.apk`) avec `debugfs` (script
   `outils/preparer_cache.sh` adapté). Écris cette image dans `cache`, relis-la et compare.
3. **Patch Magisk sur le Mac** : extrais de l'APK Magisk `boot_patch.sh`, `util_functions.sh`,
   `libmagiskboot.so` (arm64, pour le Mac) et `libmagiskinit.so`, `libmagisk.so`, `libinit-ld.so`
   (**armeabi-v7a**, l'architecture du robot). Lance `boot_patch.sh` dans le Linux ARM64 avec
   `KEEPVERITY=true KEEPFORCEENCRYPT=true PREINITDEVICE=cache`.
4. **Script de premier démarrage** : ajoute au ramdisk patché
   * `overlay.d/mcroot.rc` (ce dossier : lance le script une fois le démarrage terminé) ;
   * `mcroot.sh` **à la racine du ramdisk** (pas dans `overlay.d/sbin` : sur ce robot, Magisk
     remplace `/sbin` au démarrage et le script disparaîtrait) ;
   * `mcroot_adb_keys` : **ta** clé publique ADB (`~/.android/adbkey.pub`), à ne jamais publier.

   ```
   magiskboot cpio ramdisk.cpio "add 0644 overlay.d/mcroot.rc mcroot.rc" \
     "add 0750 mcroot.sh mcroot.sh" "add 0640 mcroot_adb_keys mcroot_adb_keys"
   magiskboot repack boot.img boot-patche.img
   ```
5. **Vérifie** avant d'écrire : noyau identique à l'original, taille = celle de la partition `boot`,
   en-tête `ANDROID!`.
6. **Écris `boot`** avec mtkclient, relis et compare octet par octet. Redémarre : l'appli Lidl
   revient, puis le script installe les applis (journal dans `/cache/mcroot.log`).
7. **Active le débogage USB** dans les réglages Android (menu usine, voir le guide), puis
   `adb connect IP-du-robot:5555` et `adb shell su -c id` doit répondre `uid=0(root)`.
8. Dans Magisk, valide la **configuration supplémentaire** (le robot redémarre).

**Retour arrière** : réécris ton `boot.img` d'origine (et `cache.img`) depuis la sauvegarde.
