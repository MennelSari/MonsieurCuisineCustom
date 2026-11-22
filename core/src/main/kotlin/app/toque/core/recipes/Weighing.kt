package app.toque.core.recipes

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Pesée guidée : couleur et densité des ingrédients, quantités repérées dans le texte des étapes,
 * et état d'une pesée (encore, presque, c'est bon, trop). Logique pure, testée sans Android.
 */

/** Ce qu'on sait d'un ingrédient pour l'afficher et le peser. [color] en ARGB. */
data class IngredientInfo(
    val key: String,
    val color: Int,
    /** Masse volumique en g/ml, pour convertir les ml et cl en grammes. */
    val density: Double = 1.0,
    val liquid: Boolean = false,
)

object Ingredients {
    private class Entry(val keys: List<String>, val color: Int, val density: Double, val liquid: Boolean)

    private fun e(keys: String, color: Long, density: Double = 1.0, liquid: Boolean = false) =
        Entry(keys.split('|').map { norm(it) }, color.toInt(), density, liquid)

    /*
     * Couleur dominante de l'aliment tel qu'il tombe dans le bol. Les clés sont comparées mot à mot
     * (sans accents, au singulier ou au pluriel) ; la plus longue l'emporte : « lait de coco » avant
     * « lait », « sucre roux » avant « sucre ».
     */
    private val table = listOf(
        // Liquides
        e("eau|eau froide|eau chaude|eau tiede|glacons|glace pilee", 0xFF5DA9DD, 1.0, true),
        e("eau gazeuse", 0xFF8CC5E8, 1.0, true),
        e("lait|lait entier|lait demi ecreme|lait ecreme|lait d'avoine|lait d'amande|lait de soja|lait vegetal", 0xFFF4EEDF, 1.03, true),
        e("lait de coco|creme de coco", 0xFFF3EFE7, 1.0, true),
        e("creme|creme liquide|creme fraiche|creme entiere|creme epaisse|creme fleurette|creme semi epaisse", 0xFFF5ECD6, 1.0, true),
        e("huile|huile de tournesol|huile neutre|huile de colza|huile vegetale|huile de pepins de raisin", 0xFFE8C653, 0.92, true),
        e("huile d'olive", 0xFFB5A13A, 0.91, true),
        e("huile de sesame", 0xFFB9853A, 0.92, true),
        e("vin blanc|vin blanc sec", 0xFFE6D98E, 0.99, true),
        e("vin rouge", 0xFF7A1F2B, 0.99, true),
        e("vin|vin rose", 0xFFE7A0A0, 0.99, true),
        e("porto|madere", 0xFF8A2A2A, 1.02, true),
        e("biere", 0xFFD9A441, 1.01, true),
        e("rhum|rhum ambre|cognac|calvados|whisky", 0xFFA0602A, 0.95, true),
        e("bouillon|bouillon de legumes|bouillon de volaille|fond de veau|fond blanc|fumet", 0xFFC9A15A, 1.0, true),
        e("bouillon de boeuf", 0xFF8E5A33, 1.0, true),
        e("jus de citron|jus de citron vert", 0xFFF2E27A, 1.03, true),
        e("jus d'orange", 0xFFF5A33A, 1.04, true),
        e("jus de pomme", 0xFFE3B65A, 1.04, true),
        e("jus", 0xFFF0B24A, 1.04, true),
        e("sirop|sirop d'agave|sirop d'erable|sirop de sucre de canne", 0xFFC7782A, 1.33, true),
        e("miel", 0xFFE0A526, 1.42, true),
        e("vinaigre|vinaigre balsamique", 0xFF7A4A2A, 1.01, true),
        e("vinaigre de cidre|vinaigre de vin blanc|vinaigre blanc", 0xFFE3C98A, 1.01, true),
        e("sauce soja|sauce soja sucree", 0xFF3B2416, 1.17, true),
        e("sauce tomate|coulis de tomate|pulpe de tomate|passata|tomates concassees", 0xFFC8371F, 1.03, true),
        e("cafe|expresso", 0xFF4A2E1E, 1.0, true),
        e("the", 0xFFB5793A, 1.0, true),
        e("yaourt|yaourt nature|yaourt grec|fromage blanc|skyr|petit suisse", 0xFFF6F3EC, 1.03),
        e("beurre|beurre doux|beurre demi sel|margarine", 0xFFF2D36B, 0.91),
        e("oeuf|oeufs|jaune d'oeuf|jaunes d'oeufs", 0xFFF2B83B, 1.03),
        e("blanc d'oeuf|blancs d'oeufs", 0xFFF1EEDF, 1.03),
        // Farines, sucres, céréales
        e("farine|farine de ble|farine t45|farine t55|farine t65|farine t80|maizena|fecule|fecule de mais|fecule de pomme de terre", 0xFFF4EFE4, 0.55),
        e("farine complete|farine de seigle|farine d'epeautre|farine de sarrasin", 0xFFD8C3A0, 0.55),
        e("sucre|sucre en poudre|sucre semoule|sucre glace|sucre vanille|cassonade blonde", 0xFFF7F5EF, 0.85),
        e("sucre roux|cassonade|vergeoise|sucre complet|rapadura|muscovado", 0xFFC98B4E, 0.85),
        e("sel|gros sel|fleur de sel", 0xFFF2F2F0, 1.2),
        e("poivre|poivre noir", 0xFF3A3430),
        e("levure|levure chimique|levure de boulanger|bicarbonate", 0xFFE8DCC0),
        e("riz|riz arborio|riz basmati|riz rond|riz thai|riz long", 0xFFEFE7D2, 0.85),
        e("riz complet", 0xFFD9C7A0, 0.85),
        e("pates|spaghetti|penne|tagliatelles|macaronis|coquillettes|lasagnes|nouilles|fusilli", 0xFFF0D58C),
        e("semoule|couscous|boulgour|polenta", 0xFFEBCB7E, 0.7),
        e("quinoa", 0xFFE2D2A8, 0.8),
        e("flocons d'avoine|avoine|muesli|son d'avoine", 0xFFD8C29A, 0.4),
        e("chapelure|biscuits|speculoos|biscuits secs|petits beurre", 0xFFD9A85E),
        e("pain|pain rassis|mie de pain", 0xFFE2C48E),
        e("lentilles|lentilles vertes|lentilles brunes", 0xFF7D7A3E),
        e("lentilles corail", 0xFFE8835A),
        e("pois chiches", 0xFFD9B77A),
        e("haricots rouges", 0xFF8E2F2B),
        e("haricots blancs|flageolets", 0xFFEDE5D0),
        // Légumes
        e("tomate|tomates|tomates cerises|concentre de tomate", 0xFFD9432F),
        e("carotte|carottes", 0xFFEE8A2D),
        e("potimarron|courge|potiron|butternut|citrouille|courge butternut", 0xFFE8862A),
        e("patate douce", 0xFFE07A3A),
        e("pomme de terre|pommes de terre", 0xFFE7C77A),
        e("oignon|oignons|oignon jaune|oignon blanc|oignons nouveaux", 0xFFE3CFB4),
        e("oignon rouge|oignons rouges|echalote|echalotes", 0xFFA4566E),
        e("ail|gousse d'ail|gousses d'ail", 0xFFEFE7D6),
        e("poireau|poireaux", 0xFFB7CF7E),
        e("courgette|courgettes|concombre", 0xFF6E9E45),
        e("brocoli|brocolis", 0xFF4E8A3A),
        e("epinard|epinards|pousses d'epinards|blettes|chou kale", 0xFF3E7D3A),
        e("petits pois|pois|haricots verts|feves|edamame", 0xFF7DB34A),
        e("poivron|poivron vert", 0xFF4E9A3C),
        e("poivron rouge|piment|piments", 0xFFD63A2F),
        e("poivron jaune", 0xFFF2C230),
        e("aubergine|aubergines", 0xFF4B2E5A),
        e("champignon|champignons|champignons de paris|cepes|girolles|shiitake", 0xFFB59A7A),
        e("chou|chou blanc|chou vert|chou fleur", 0xFFDDE3C2),
        e("chou rouge", 0xFF7A3A6E),
        e("celeri|celeri rave|celeri branche|fenouil|panais|navet|navets", 0xFFE3DEC2),
        e("betterave|betteraves", 0xFF8E1F45),
        e("mais", 0xFFF2C94C),
        e("avocat|avocats", 0xFF8BA845),
        e("salade|laitue|roquette|mache", 0xFF7FB04A),
        e("basilic|persil|coriandre|ciboulette|menthe|aneth|herbes|herbes de provence|thym|romarin", 0xFF4E8F3A),
        e("gingembre", 0xFFE3C17A),
        // Fruits
        e("pomme|pommes", 0xFFE5D27A),
        e("poire|poires", 0xFFE3D58E),
        e("banane|bananes", 0xFFF2D56B),
        e("citron|citrons|zeste de citron", 0xFFF2DC4A),
        e("citron vert|citrons verts", 0xFF9CC24A),
        e("orange|oranges|clementine|clementines|mandarine", 0xFFF29A2E),
        e("fraise|fraises", 0xFFD9364A),
        e("framboise|framboises|fruits rouges", 0xFFC7304F),
        e("myrtille|myrtilles|cassis|mures", 0xFF4A4F8C),
        e("cerise|cerises", 0xFF9E1F33),
        e("mangue|mangues|abricot|abricots|peche|peches", 0xFFF2A93B),
        e("ananas", 0xFFF2D04A),
        e("raisin|raisins secs", 0xFF7A4A6E),
        e("dattes|pruneaux|figues", 0xFF6E3F2A),
        e("noix de coco|coco rapee|noix de coco rapee", 0xFFF3EEE6),
        // Fruits secs, chocolat
        e("amande|amandes|poudre d'amande|poudre d'amandes|amandes effilees", 0xFFD9B48A),
        e("noisette|noisettes|poudre de noisette", 0xFFA8743F),
        e("noix|cerneaux de noix|noix de cajou|pistache|pistaches|pignons|graines", 0xFFB98E5A),
        e("chocolat|chocolat noir|chocolat patissier|chocolat a cuire|pepites de chocolat", 0xFF5A3A26),
        e("chocolat au lait", 0xFF8A5A3A),
        e("chocolat blanc", 0xFFF3E6CC),
        e("cacao|cacao en poudre|poudre de cacao", 0xFF6B4430),
        e("pate a tartiner|praline|praline", 0xFF7A4A2E),
        e("confiture", 0xFFB8304A),
        e("caramel", 0xFFC27A2A),
        // Fromages
        e("fromage|fromage rape|gruyere|emmental|comte|cheddar|raclette|reblochon", 0xFFF0D27A),
        e("parmesan|grana padano|pecorino", 0xFFEBD9A0),
        e("mozzarella|ricotta|mascarpone|fromage frais|chevre|feta|burrata|cottage", 0xFFF5F1E6),
        e("roquefort|bleu|gorgonzola", 0xFFD9DCC9),
        // Viandes, poissons
        e("poulet|blanc de poulet|blancs de poulet|dinde|escalope|volaille|cuisses de poulet", 0xFFE8C3A0),
        e("boeuf|steak|viande hachee|boeuf hache|agneau|veau", 0xFF9E3B32),
        e("porc|saucisse|saucisses|chorizo", 0xFFD98A7A),
        e("lardons|bacon|jambon|jambon cru", 0xFFE79A9A),
        e("saumon|truite", 0xFFF08A5D),
        e("thon|cabillaud|poisson|colin|merlu|crevettes|moules|fruits de mer", 0xFFE9D6C6),
        e("tofu", 0xFFF1E9D6),
        // Épices et condiments
        e("curry|curcuma|safran|paprika doux", 0xFFE0A527),
        e("paprika|piment d'espelette|piment en poudre|cayenne", 0xFFC8452A),
        e("cannelle|muscade|quatre epices|cumin", 0xFFA8622E),
        e("vanille|gousse de vanille|extrait de vanille", 0xFF3A2A20),
        e("moutarde", 0xFFD9B23A),
        e("mayonnaise", 0xFFF3E3A8),
        e("ketchup", 0xFFB92E22),
        e("pesto", 0xFF5E8A3A),
        e("gelatine|agar agar", 0xFFF3EDE0),
    )

    /** Mots qui ne disent rien de l'ingrédient (on les ignore pour comparer). */
    private val noise = setOf("de", "d", "du", "des", "la", "le", "les", "l", "un", "une", "a", "au", "aux", "en", "et")

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace("œ", "oe").replace("æ", "ae")
        .replace(Regex("[’`´]"), "'")
        .replace(Regex("[^a-z0-9' ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun words(s: String): List<String> = norm(s).replace("'", "' ").split(' ').filter { it.isNotBlank() }
        .map { it.trimEnd('\'') }.filter { it !in noise && it.isNotEmpty() }

    private fun singular(w: String): String = when {
        w.endsWith("eaux") -> w.dropLast(1)
        w.length > 3 && w.endsWith("aux") -> w.dropLast(3) + "al"
        w.length > 3 && (w.endsWith("s") || w.endsWith("x")) -> w.dropLast(1)
        else -> w
    }

    private val compiled: List<Triple<List<String>, Entry, String>> = table.flatMap { en ->
        en.keys.map { k -> Triple(words(k).map { singular(it) }, en, k) }
    }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.size * 100 + it.third.length }

    /**
     * Ingrédient reconnu dans un texte (nom d'ingrédient, libellé de pesée ou phrase d'étape).
     * La plus longue correspondance l'emporte ; à longueur égale, la première dans le texte.
     */
    fun lookup(text: String): IngredientInfo? {
        val w = words(text).map { singular(it) }
        if (w.isEmpty()) return null
        var best: Triple<List<String>, Entry, String>? = null
        var bestPos = Int.MAX_VALUE
        for (c in compiled) {
            val k = c.first
            if (best != null && k.size < best.first.size) break
            val pos = indexOf(w, k)
            if (pos >= 0 && (best == null || pos < bestPos)) {
                best = c
                bestPos = pos
            }
        }
        val b = best ?: return null
        return IngredientInfo(b.third, b.second.color, b.second.density, b.second.liquid)
    }

    private fun indexOf(w: List<String>, k: List<String>): Int {
        if (k.size > w.size) return -1
        for (i in 0..w.size - k.size) {
            var ok = true
            for (j in k.indices) if (w[i + j] != k[j]) { ok = false; break }
            if (ok) return i
        }
        return -1
    }
}

/** Unités de masse et de volume reconnues dans les recettes. */
enum class QtyUnit(val symbol: String, val volume: Boolean, private val factor: Double) {
    G("g", false, 1.0), KG("kg", false, 1000.0), ML("ml", true, 1.0), CL("cl", true, 10.0), DL("dl", true, 100.0), L("l", true, 1000.0);

    /** En grammes (masse) ou en millilitres (volume). */
    fun base(amount: Double): Double = amount * factor

    companion object {
        fun of(s: String): QtyUnit? = when (Ingredients.norm(s)) {
            "g", "gr", "gramme", "grammes" -> G
            "kg", "kilo", "kilos", "kilogramme", "kilogrammes" -> KG
            "ml", "millilitre", "millilitres" -> ML
            "cl", "centilitre", "centilitres" -> CL
            "dl", "decilitre", "decilitres" -> DL
            "l", "litre", "litres" -> L
            else -> null
        }
    }
}

/** Quantité à peser : ce que la recette demande, et sa cible en grammes sur la balance. */
data class WeighTarget(
    /** Nom affiché (« riz arborio », « lait »). */
    val label: String,
    /** Cible en grammes, c'est ce que mesure la balance. */
    val grams: Int,
    /** Quantité et unité telles que la recette les écrit (pour afficher 50 cl plutôt que 515 g). */
    val amount: Double,
    val unit: QtyUnit,
    val info: IngredientInfo?,
) {
    val density: Double get() = if (unit.volume) info?.density ?: 1.0 else 1.0

    /** Valeur à afficher pour un poids mesuré, dans l'unité de la recette. */
    fun display(gramsMeasured: Int): Double = when (unit) {
        QtyUnit.G -> gramsMeasured.toDouble()
        QtyUnit.KG -> gramsMeasured / 1000.0
        else -> gramsMeasured / density / unit.base(1.0)
    }

    companion object {
        fun of(label: String, amount: Double, unit: QtyUnit, info: IngredientInfo?): WeighTarget {
            val base = unit.base(amount)
            val grams = if (unit.volume) base * (info?.density ?: 1.0) else base
            return WeighTarget(label, grams.roundToInt(), amount, unit, info)
        }

        /** Pesée explicite d'une recette (en grammes), avec la couleur de l'ingrédient s'il est reconnu. */
        fun grams(label: String, grams: Int, context: String = ""): WeighTarget =
            WeighTarget(label, grams, grams.toDouble(), QtyUnit.G, Ingredients.lookup(label) ?: Ingredients.lookup(context))
    }
}

object Quantities {
    private val number = "(\\d+(?:[.,]\\d+)?|\\d+\\s?/\\s?\\d+|½|¼|¾)"
    private val unit = "(kilogrammes?|kilos?|kg|grammes?|gr|g|millilitres?|ml|centilitres?|cl|décilitres?|decilitres?|dl|litres?|l)"
    private val stops = "(?:\\s+(?:et|puis|dans|sans|avec|pendant|au|aux|à|a|en|jusqu|pour|par|sur|ou|sauf|que|qui|ainsi|afin)\\b|[,.;:!?()\\n]|$)"
    private val pattern = Regex(
        "(?<![\\w,.])$number\\s*$unit(?![\\w])\\s*(?:de\\s+|d['’]\\s*|du\\s+|des\\s+)?([^,.;:!?()\\n]*?)(?=$stops)",
        RegexOption.IGNORE_CASE,
    )

    private fun parseNumber(s: String): Double? {
        val t = s.replace(" ", "")
        return when {
            t == "½" -> 0.5
            t == "¼" -> 0.25
            t == "¾" -> 0.75
            t.contains('/') -> {
                val (a, b) = t.split('/')
                val n = a.toDoubleOrNull()
                val d = b.toDoubleOrNull()
                if (n == null || d == null || d == 0.0) null else n / d
            }
            else -> t.replace(',', '.').toDoubleOrNull()
        }
    }

    /**
     * Quantités écrites dans un texte (« 30 g d'huile d'olive », « 10 cl de vin blanc »), dans
     * l'ordre. Les ingrédients de [recipe] servent à compléter un nom absent ou trop court.
     */
    fun find(text: String, recipe: List<Ingredient> = emptyList()): List<WeighTarget> {
        val out = ArrayList<WeighTarget>()
        for (m in pattern.findAll(text)) {
            val amount = parseNumber(m.groupValues[1]) ?: continue
            val u = QtyUnit.of(m.groupValues[2]) ?: continue
            if (amount <= 0) continue
            var name = m.groupValues[3].trim().trimEnd('\'', '’').trim()
            name = name.split(Regex("\\s+")).take(5).joinToString(" ")
            val fromRecipe = recipe.firstOrNull { ing ->
                val n = Ingredients.norm(ing.name)
                n.isNotEmpty() && name.isNotEmpty() && (Ingredients.norm(name).startsWith(n) || n.startsWith(Ingredients.norm(name)))
            }
            val label = when {
                fromRecipe != null && fromRecipe.name.length > name.length -> fromRecipe.name
                name.isNotEmpty() -> name
                else -> ""
            }
            val info = Ingredients.lookup(label)
            out += WeighTarget.of(label, amount, u, info)
        }
        return out.filter { it.grams in 1..5000 }
    }

    private val skip = setOf("de", "du", "des", "d", "la", "le", "les", "l", "un", "une", "et", "a", "au", "aux", "en",
        "frais", "fraiche", "fraiches", "fin", "fine", "gros", "grosse", "petit", "petite", "bio", "doux", "douce")

    /** Quantité de la recette en nombre (« 120 », « 1 ½ », « 1 - 2 » → 1). */
    private fun amount(qty: String): Double? {
        val t = qty.trim().split(Regex("\\s*-\\s*")).first()
        val parts = t.split(Regex("\\s+")).mapNotNull { parseNumber(it) }
        return if (parts.isEmpty()) null else parts.sum()
    }

    /** Ingrédients de la recette qu'on peut peser (unité de masse ou de volume), dans l'ordre de la liste. */
    fun weighable(ingredients: List<Ingredient>): List<Pair<Ingredient, WeighTarget>> = ingredients.mapNotNull { ing ->
        val u = QtyUnit.of(ing.unit.trim()) ?: return@mapNotNull null
        val a = amount(ing.qty) ?: return@mapNotNull null
        if (a <= 0) return@mapNotNull null
        val t = WeighTarget.of(ing.name, a, u, Ingredients.lookup(ing.name))
        if (t.grams in 1..5000) ing to t else null
    }

    /** Mots qui désignent l'ingrédient dans une étape (« huile neutre » → huile). */
    private fun keys(name: String): List<String> =
        Ingredients.norm(name).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in skip }.take(2)

    /**
     * Ingrédients pesables cités dans une étape sans leur quantité (recettes Thermomix : « Mettre
     * l'oignon et l'huile dans le bol », les grammes étant dans la liste des ingrédients), dans
     * l'ordre où l'étape les cite.
     */
    fun mentioned(text: String, ingredients: List<Ingredient>): List<WeighTarget> {
        val words = Ingredients.norm(text).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        fun same(a: String, b: String) = a == b || a.trimEnd('s', 'x') == b.trimEnd('s', 'x')
        val out = ArrayList<Pair<Int, WeighTarget>>()
        for ((ing, t) in weighable(ingredients)) {
            val k = keys(ing.name).firstOrNull() ?: continue
            val pos = words.indexOfFirst { same(it, k) }
            if (pos >= 0 && out.none { it.second.label == t.label }) out += pos to t
        }
        return out.sortedBy { it.first }.map { it.second }
    }
}

/** Où en est une pesée. */
enum class WeighState { TODO, NEAR, OK, OVER }

object Weighing {
    /** Écart toléré autour de la cible : 3 % ou 5 g, le plus grand des deux, sans dépasser 40 % de la cible. */
    fun tolerance(target: Int): Int = min(max(5, (target * 0.03).roundToInt()), max(1, (target * 0.4).roundToInt()))

    fun state(target: Int, measured: Int): WeighState {
        if (target <= 0) return WeighState.TODO
        val tol = tolerance(target)
        return when {
            measured > target + tol -> WeighState.OVER
            abs(measured - target) <= tol -> WeighState.OK
            measured >= target * 0.9 - tol -> WeighState.NEAR
            else -> WeighState.TODO
        }
    }

}
