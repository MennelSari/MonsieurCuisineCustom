# Importer tes propres recettes

Camybrith Cooking sait convertir pour le Smart des recettes **que tu possèdes**, écrites pour un
Thermomix : les vitesses, températures, Varoma et pétrin sont adaptés automatiquement, et chaque
réglage modifié est marqué « à vérifier ». Elles apparaissent dans un onglet à part et ne sont
jamais effacées par la synchronisation des recettes Lidl.

Le projet ne fournit aucune recette et n'explique pas comment en récupérer sur un site : utilise
uniquement des recettes que tu as le droit de copier (les tiennes, celles de tes proches…).

## Le format du fichier `data.json`

Une liste d'objets. Chaque objet a un identifiant (`id`) et une recette au format
[schema.org Recipe](https://schema.org/Recipe) dans `schema`. Les réglages du robot sont écrits
dans le texte des étapes, **entre `<nobr>` et `</nobr>`**, séparés par des barres obliques.

```json
[
  {
    "id": "soupe1",
    "title": "Soupe de carottes",
    "author": "Mamie",
    "ingredients": ["300 g de carottes", "500 g d'eau", "1 pincée de sel"],
    "tags": ["végétarien"],
    "schema": {
      "name": "Soupe de carottes",
      "recipeYield": "4 portions",
      "prepTime": "PT10M",
      "totalTime": "PT35M",
      "recipeCategory": ["Soupes"],
      "recipeInstructions": [
        {"@type": "HowToStep", "text": "Mettre les carottes dans le bol et hacher <nobr>5 sec/vitesse 5</nobr>."},
        {"@type": "HowToStep", "text": "Ajouter l'eau et cuire <nobr>20 min/100°C/vitesse 1</nobr>. Mixer <nobr>1 min/vitesse 5-10</nobr>."}
      ]
    }
  }
]
```

Champs facultatifs : `title` (sinon `schema.name`), `author`, `ingredients` (sinon
`schema.recipeIngredient`), `tags`, `rating_score`, `rating_count`. Les étapes peuvent être
regroupées en sections (`HowToSection` avec `name` et `itemListElement`).

Réglages reconnus, par exemple : `15-30 sec/vitesse 7`, `10 min/100°C/vitesse 1`,
`20 min/Varoma/vitesse 1`, `Pétrin/2 min`, `Turbo/0.5 sec/3-5 fois`. Une fourchette de temps
devient la durée la plus courte ; un réglage sans durée est laissé « à régler à la main ».

## Les photos (facultatif)

Un dossier `images/` à côté de `data.json`, avec une photo JPEG par recette nommée d'après son
identifiant : `images/soupe1.jpg`.

## Envoyer et importer

```
~/platform-tools/adb -s 192.168.1.42:5555 shell mkdir -p /sdcard/Android/data/app.toque/files/import
~/platform-tools/adb -s 192.168.1.42:5555 push data.json images /sdcard/Android/data/app.toque/files/import/
```

Puis dans l'appli : **Réglages**, **Recettes d'autres robots**, **Importer les recettes** (ou
rouvre l'appli). Pour mettre à jour, renvoie le fichier : les recettes sont remplacées, les
favoris gardés.

**Relis toujours les étapes « à vérifier » avant de les lancer** : la conversion respecte les
limites de sécurité du Smart, mais un Thermomix et un Monsieur Cuisine ne se comportent pas
exactement pareil.
