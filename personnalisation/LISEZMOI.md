# Personnaliser Camybrith Cooking

🇬🇧 **[English version](README.en.md)**

Tout se change sans rien casser, et tout se remet d'origine en une commande.

## 1. Les sons (le plus simple)

Voir [sons/LISEZMOI.md](sons/LISEZMOI.md) : tu déposes tes fichiers dans `personnalisation/sons`,
puis `bash outils/sons.sh envoyer`. Aucune recompilation, aucun risque.

Les sons fournis sont fabriqués par `marque/sons.py` (des notes synthétisées, aucun échantillon
extérieur). Tu peux modifier ce script pour fabriquer les tiens :
`python3 personnalisation/marque/sons.py dossier_de_sortie` (il faut `pip install numpy`).

## 2. L'animation de démarrage du robot (ton logo)

C'est ce qui s'affiche pendant que le robot s'allume. Trois possibilités.

**Ton logo, en fondu, à partir d'une simple image** (PNG avec fond transparent de préférence) :

```
python3 personnalisation/animation_depuis_image.py mon_logo.png
bash personnalisation/fabriquer_module_animation.sh
bash outils/animation.sh installer modules/camybrith_animation-perso.zip
```

La couleur de fond se choisit en deuxième argument, en hexadécimal : `... mon_logo.png ffffff`
pour un fond blanc. Ouvre `personnalisation/bootanimation_apercu.png` pour voir le résultat
avant de l'envoyer. Il faut Python 3 et Pillow (`pip install pillow`).

**Une animation complète que tu as déjà** (un fichier `bootanimation.zip` au format Android,
1280 x 800) :

```
bash personnalisation/fabriquer_module_animation.sh mon_animation.zip
bash outils/animation.sh installer modules/camybrith_animation-perso.zip
```

**L'animation Camybrith Cooking modifiée** : le dessin (cocotte, vapeur, nom, couleurs) est dans
`marque/marque.py` et la chronologie dans `marque/bootanim.py`.
`python3 personnalisation/marque/bootanim.py personnalisation/bootanimation.zip` la refabrique.

Dans tous les cas, l'animation apparaît au prochain redémarrage. Pour retrouver celle de Lidl :
`bash outils/animation.sh retirer`, puis redémarre.

> Le tout premier logo, celui qui s'affiche une fraction de seconde à l'allumage, est dans une
> partition à part. Sur le Smart, il ne se voit quasiment pas : le projet n'y touche pas, et il
> vaut mieux ne pas y toucher.

## 3. Le nom, l'icône et l'écran d'ouverture de l'appli (pour les plus curieux)

Ceux-là sont dans l'appli elle-même : il faut la recompiler. Pas besoin d'installer Android
Studio, GitHub le fait pour toi :

1. Crée ton propre exemplaire du projet avec le bouton **Fork** en haut de la page GitHub.
2. Modifie dans ton exemplaire, directement sur le site (icône crayon) :
   * le nom : `android:label` dans `app/src/main/AndroidManifest.xml` ;
   * l'icône : `app/src/main/res/drawable/ic_launcher.xml` (dessin vectoriel Android) ;
   * l'écran d'ouverture : `app/src/main/kotlin/app/toque/ui/SplashView.kt` (couleurs et textes) ;
   * les sons fournis par défaut : les fichiers de `app/src/main/assets/sons`.
3. Change le numéro de version dans `app/build.gradle.kts` (par exemple `1.0.0` devient `1.0.1`,
   et les deux `versionCode` augmentent de 2).
4. Dans l'onglet **Actions** de ton exemplaire, active les workflows. À chaque changement de
   version, GitHub fabrique les APK et les publie dans tes **Releases**.
5. Télécharge-les dans `apk/` et lance `bash outils/installer.sh`.

Important : garde le même nom de paquet (`app.toque`) et la même signature
(`app/signature/toque.p12`), sinon la nouvelle appli ne pourra pas parler au pilote moteur.
