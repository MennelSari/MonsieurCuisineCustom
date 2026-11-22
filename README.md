# Camybrith Cooking

**Une nouvelle interface, rapide et moderne, pour le robot Lidl Monsieur Cuisine Smart (SKMS 1200 A1).**
Gratuite, libre (licence MIT), sans compte, sans abonnement, et elle marche même sans Internet.

Camybrith Cooking remplace l'appli d'origine sur la tablette du robot. Elle commande elle-même le
moteur, la chauffe et la balance, et garde les recettes officielles Monsieur Cuisine.

> **Projet indépendant**, sans lien avec Lidl, SilverCrest ou Hoyer. Il faut « rooter » le robot,
> ce qui annule probablement la garantie et comporte un petit risque de le rendre inutilisable si
> on se trompe d'étape. Le guide est écrit pour l'éviter, avec une sauvegarde complète avant de
> toucher à quoi que ce soit, mais tu fais tout **à tes risques**. Testé sur un Smart SKMS 1200 A1
> (Android 8.1, version système V01.00.83 de juin 2022) en novembre 2026.

## Installer

**Commence ici : [docs/GUIDE.md](docs/GUIDE.md)**, le guide pas à pas, écrit pour quelqu'un qui
n'a jamais fait ce genre de manipulation.

Il te faut :
* un **PC sous Windows** (pour l'outil qui écrit dans la mémoire du robot) ;
* un câble **USB-A mâle vers USB-A mâle** (USB 2.0) et un petit tournevis Torx ;
* le Wi-Fi de la maison ;
* deux à trois heures, sans se presser.

Une fois le robot rooté, tout le reste (installation, mises à jour, sons, logo) se fait **par le
Wi-Fi**, depuis Windows (avec Ubuntu, voir le guide), un Mac ou Linux.

## Ce que ça change

* **Une interface pensée pour l'écran et la molette du robot**, fluide sur sa petite tablette.
* **Les recettes Monsieur Cuisine du Smart**, téléchargées depuis le site public de Lidl, la nuit
  ou à la demande, avec leurs réglages, photos, notes et avis.
* **Une vraie recherche** : elle ignore les accents, les pluriels et les fautes de frappe. Recherche
  par ingrédients, filtres, favoris.
* **Cuisson guidée** : chaque étape se lance d'un appui, la suivante s'affiche toute seule, la
  pesée est intégrée (molette ou boutons +10 g et +50 g pour atteindre le poids voulu).
* **Tes propres recettes** écrites pour un autre robot (fichier JSON, voir
  [docs/RECETTES.md](docs/RECETTES.md)) : les vitesses, températures, Varoma et pétrin sont
  convertis pour le Smart, dans un onglet à part.
* **Cuisson manuelle**, vapeur, rissoler, pétrir, Turbo, balance.
* **Couvercle ouvert = pause**, et reprise d'un appui quand on le referme.
* **Hors ligne** : rien ne dépend d'un serveur.
* **Tout se personnalise** : les sons, l'animation de démarrage avec ton logo, et même le nom et
  l'icône (voir [personnalisation/LISEZMOI.md](personnalisation/LISEZMOI.md)).

## Sécurité de la cuisson

Les limites de l'appli d'origine sont toutes conservées (vitesse 3 au plus en chauffe et en sens
inverse, durées maximales, conditions du Turbo). Le couvercle, les erreurs et la fraîcheur des
informations de la base sont vérifiés **avant** chaque démarrage ; une base qui ne répond plus
arrête tout ; une alerte reste affichée tant qu'on ne l'a pas lue ; rien ne redémarre tout seul
après une pause.

La commande du moteur tourne dans **une appli à part** (le « pilote moteur ») : si l'interface
plante, la cuisson en cours n'est pas coupée. En plus, la carte du robot arrête elle-même le
moteur environ 4 secondes après la dernière commande reçue.

## Organisation du dépôt

```
docs/GUIDE.md        le guide d'installation pas à pas (commence ici)
docs/RECETTES.md     importer tes propres recettes
docs/SMART.md        notes techniques sur le Smart (protocole, programmes, essais)
outils/              scripts à lancer depuis l'ordinateur : installer, sons, animation, barre…
modules/             modules Magisk : ADB par le Wi-Fi, animation de démarrage
personnalisation/    tes sons, ton logo, et les outils pour les fabriquer
core/                logique en Kotlin pur, testée sans Android (protocole, sécurité, recettes)
commun/              code partagé entre l'interface et le pilote
pilote/              appli « pilote moteur » (app.toque.pilote)
app/                 appli interface (app.toque)
```

## Fabriquer les APK soi-même

Les APK sont fabriqués et publiés automatiquement par GitHub (onglet **Actions**, page
**Releases**). Pour les fabriquer sur ton ordinateur : JDK 17, SDK Android, puis

```
./gradlew :core:test :pilote:assembleRelease :app:assembleRelease
./gradlew -Pprudent=true :app:assembleRelease     # version prudente
```

La version **rapide** est compilée par Android avant la première ouverture (c'est ce que fait
`outils/installer.sh`) : sur ce robot, la compilation « au fil de l'eau » d'Android fait planter
l'interface. La version **prudente** n'est jamais compilée : plus lente, mais à l'abri de ce
problème.

## Ajouter une langue

L'appli est écrite en français, avec l'anglais en plus. Chaque langue est un fichier de
[`app/src/main/assets/lang/`](app/src/main/assets/lang) : copie `en.json` en `de.json` par exemple,
change `"_name"` et traduis les valeurs (les clés sont les textes français, garde les `%s` et `%d`).

## Remerciements et licence

Camybrith Cooking est une adaptation au Smart de **Toque**
([Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)), écrite pour le
Monsieur Cuisine Connect. Merci à Wuki60 et aux contributeurs de Toque.

La méthode de root vient de la communauté du dépôt
[EliasKotlyar/Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack)
(discussion n° 38, en particulier depau et ihrapsa). Le root utilise
[Magisk](https://github.com/topjohnwu/Magisk).

Licence MIT : voir [LICENSE](LICENSE). Aucun code, recette, image ou fichier de Lidl n'est inclus
dans ce dépôt.
