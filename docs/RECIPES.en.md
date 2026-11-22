# Importing your own recipes

🇫🇷 **[Version française](RECETTES.md)**

Camybrith Cooking can convert for the Smart recipes **that you own**, written for a Thermomix:
speeds, temperatures, Varoma and dough mode are adapted automatically, and every changed setting
is flagged "to check". They show up in a separate tab and are never erased by the Lidl recipe sync.

The project ships no recipes and does not explain how to get them from any website: only use
recipes you are allowed to copy (your own, your family's…).

## The `data.json` file format

A list of objects. Each object has an identifier (`id`) and a recipe in
[schema.org Recipe](https://schema.org/Recipe) format under `schema`. Robot settings are written in
the step text, **between `<nobr>` and `</nobr>`**, separated by slashes. The settings syntax is the
French one (`vitesse` = speed).

```json
[
  {
    "id": "soup1",
    "title": "Carrot soup",
    "author": "Grandma",
    "ingredients": ["300 g carrots", "500 g water", "1 pinch of salt"],
    "tags": ["vegetarian"],
    "schema": {
      "name": "Carrot soup",
      "recipeYield": "4 servings",
      "prepTime": "PT10M",
      "totalTime": "PT35M",
      "recipeCategory": ["Soups"],
      "recipeInstructions": [
        {"@type": "HowToStep", "text": "Put the carrots in the bowl and chop <nobr>5 sec/vitesse 5</nobr>."},
        {"@type": "HowToStep", "text": "Add the water and cook <nobr>20 min/100°C/vitesse 1</nobr>. Blend <nobr>1 min/vitesse 5-10</nobr>."}
      ]
    }
  }
]
```

Optional fields: `title` (otherwise `schema.name`), `author`, `ingredients` (otherwise
`schema.recipeIngredient`), `tags`, `rating_score`, `rating_count`. Steps can be grouped into
sections (`HowToSection` with `name` and `itemListElement`).

Recognized settings, for example: `15-30 sec/vitesse 7`, `10 min/100°C/vitesse 1`,
`20 min/Varoma/vitesse 1`, `Pétrin/2 min` (dough mode), `Turbo/0.5 sec/3-5 fois` (times). A time
range becomes the shortest duration; a setting without a duration is left "to set by hand".

## Photos (optional)

An `images/` folder next to `data.json`, with one JPEG photo per recipe named after its
identifier: `images/soup1.jpg`.

## Sending and importing

```
~/platform-tools/adb -s 192.168.1.42:5555 shell mkdir -p /sdcard/Android/data/app.toque/files/import
~/platform-tools/adb -s 192.168.1.42:5555 push data.json images /sdcard/Android/data/app.toque/files/import/
```

Then in the app: **Settings**, **Recipes from other machines**, **Import recipes** (or reopen the
app). To update, send the file again: recipes are replaced, favorites kept.

**Always read the "to check" steps before starting them**: the conversion respects the Smart's
safety limits, but a Thermomix and a Monsieur Cuisine do not behave exactly the same.
