package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.Ingredients
import app.toque.core.recipes.McOnline
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.Step
import app.toque.recipes.Reviews
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap

/** Fiche recette : photo et actions à gauche ; ingrédients, étapes et avis à droite, en onglets. */
class RecipeScreen(a: MainActivity, private val recipe: Recipe) : Screen(a) {
    override val title = recipe.title
    override val section = Section.RECIPES
    override val subtitle: String
        get() = listOf(recipe.category, if (recipe.totalMinutes > 0) Ui.minutes(recipe.totalMinutes) else "").filter { it.isNotBlank() }.joinToString(" · ")
    private lateinit var favButton: LinearLayout
    private lateinit var tabs: FrameLayout
    private lateinit var pane: FrameLayout
    private var tab = 0
    private val panes = HashMap<Int, View>()

    override val showTopBar = false

    /**
     * Fiche du design v2 : la photo pleine hauteur à gauche (retour, favori, modifier, origine),
     * à droite le titre, quatre repères (préparation, total, difficulté, portions), les ingrédients
     * en colonnes (ou les étapes, ou les avis) et le grand bouton « Cuisiner pas à pas ».
     */
    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(16), Ui.u(16), Ui.u(16), Ui.u(4))

        // ---- photo
        val photo = FrameLayout(c)
        photo.background = Ui.gradient(Ui.placeholderColors(recipe.title), 26f)
        photo.clipToOutline = true
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        photo.addView(img, Ui.flp())
        ImageLoader.load(img, a.app.recipes.imageFile(recipe.image), Ui.u(520))
        val top = Ui.horizontal(c)
        top.addView(circle(Icon.Kind.BACK, tr("Retour")) { a.back() })
        top.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        favButton = circle(Icon.Kind.STAR, tr("Favori")) { toggleFavorite() }
        top.addView(favButton)
        if (recipe.source == app.toque.core.recipes.RecipeSource.USER)
            top.addView(circle(Icon.Kind.PEN, tr("Modifier la recette")) { a.push(EditorScreen(a, recipe)) }, LinearLayout.LayoutParams(Ui.u(45), Ui.u(45)).apply { leftMargin = Ui.u(8) })
        photo.addView(top, Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.TOP).apply { setMargins(Ui.u(16), Ui.u(16), Ui.u(16), 0) })
        val (origin, originColor) = Ui.origin(recipe.id, recipe.source)
        val originText = when (origin) {
            "Cookomix" -> tr("Importée de Cookomix")
            "Lidl" -> tr("Recette Lidl")
            "Thermomix" -> tr("Recette Thermomix convertie")
            else -> tr("Ma recette")
        }
        val chip = Ui.text(c, originText, 11.5f, originColor, Ui.HEADING)
        chip.setPadding(Ui.u(12), Ui.u(7), Ui.u(12), Ui.u(7))
        chip.background = Ui.round(0xE6FFFFFF.toInt(), 11f)
        photo.addView(chip, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(Ui.u(16), 0, 0, Ui.u(16)) })
        root.addView(photo, LinearLayout.LayoutParams(Ui.u(416), Ui.MATCH).apply { rightMargin = Ui.u(16) })

        // ---- texte
        val right = Ui.vertical(c)
        right.setPadding(Ui.u(3), Ui.u(8), Ui.u(6), 0)
        right.addView(Ui.ellipsize(Ui.text(c, recipe.title, 28f, Ui.TEXT, Ui.HEADING).apply { letterSpacing = -0.03f; setLineSpacing(0f, 1.05f) }, 2))
        val facts = Ui.horizontal(c)
        fun fact(label: String, value: String) {
            val f = Ui.vertical(c)
            f.setPadding(Ui.u(13), Ui.u(10), Ui.u(13), Ui.u(10))
            f.background = Ui.round(Ui.SURFACE, 16f)
            f.addView(Ui.text(c, label, 10.5f, Ui.TEXT2, Ui.MEDIUM))
            f.addView(Ui.ellipsize(Ui.text(c, value, 17.5f, Ui.TEXT, Ui.HEADING)), Ui.lp(t = 2))
            facts.addView(f, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (facts.childCount > 0) leftMargin = Ui.u(8) })
        }
        fact(tr("Préparation"), if (recipe.prepMinutes > 0) Ui.minutes(recipe.prepMinutes) else "—")
        fact(tr("Total"), if (recipe.totalMinutes > 0) Ui.minutes(recipe.totalMinutes) else "—")
        fact(tr("Difficulté"), when (recipe.difficulty) { 1 -> tr("Facile"); 2 -> tr("Moyen"); else -> tr("Difficile") })
        fact(tr("Portions"), if (recipe.servings > 0) recipe.servings.toString() else "—")
        right.addView(facts, Ui.lp(t = 14))

        pane = FrameLayout(c)
        pane.background = Ui.round(Ui.SURFACE, 21f)
        val card = Ui.vertical(c)
        val head = Ui.horizontal(c)
        head.setPadding(Ui.u(19), Ui.u(15), Ui.u(15), 0)
        tabs = FrameLayout(c)
        head.addView(tabs, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val machines = recipe.machineSteps
        head.addView(Ui.text(c, tr("%d étapes · %d réglages machine", recipe.steps.size, machines), 11.5f, Ui.TEXT2, Ui.MEDIUM))
        card.addView(head)
        card.addView(pane, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        card.background = Ui.round(Ui.SURFACE, 21f)
        pane.background = null
        right.addView(card, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(14) })
        right.addView(Ui.button(c, tr("Cuisiner pas à pas"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 61, textSize = 16f) {
            a.push(CookScreen(a, recipe, 0))
        }, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
        root.addView(right, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        showTab(if (recipe.ingredients.isEmpty()) 1 else 0)
        refreshFavorite()
        return root
    }

    /** Bouton rond blanc posé sur la photo. */
    private fun circle(icon: Icon.Kind, label: String, onClick: () -> Unit): LinearLayout {
        val b = Ui.horizontal(c, Gravity.CENTER)
        b.background = Ui.pressable(Ui.round(Ui.SURFACE, 22.5f), 22.5f)
        b.addView(Icon(c, icon, Ui.TEXT).apply { strokeWidth = 2.2f }, LinearLayout.LayoutParams(Ui.u(18), Ui.u(18)))
        b.layoutParams = LinearLayout.LayoutParams(Ui.u(45), Ui.u(45))
        b.isClickable = true
        b.contentDescription = label
        b.tap { onClick() }
        return b
    }

    private fun showTab(i: Int) {
        tab = i
        tabs.removeAllViews()
        val opts = mutableListOf<Pair<Icon.Kind?, String>>(
            null to tr("Ingrédients"),
            null to tr("Étapes"),
        )
        val withReviews = McOnline.apiIdOf(recipe.id) != null
        if (withReviews) opts += null to tr("Avis")
        tabs.addView(Ui.segmented(c, opts, i, height = 36) { showTab(it) }, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.START))
        pane.removeAllViews()
        val v = panes.getOrPut(i) {
            when (i) {
                0 -> ingredientsPane()
                1 -> stepsPane()
                else -> reviewsPane()
            }
        }
        (v.parent as? FrameLayout)?.removeView(v)
        pane.addView(v, Ui.flp())
    }

    /** Ingrédients en trois colonnes : un groupe par colonne, ou la liste répartie également. */
    private fun ingredientsPane(): View {
        val cols = Ui.horizontal(c, Gravity.TOP)
        cols.setPadding(Ui.u(19), Ui.u(12), Ui.u(19), Ui.u(16))
        val groups = recipe.ingredients.filter { it.items.isNotEmpty() }
        val columns: List<Pair<String, List<app.toque.core.recipes.Ingredient>>> =
            if (groups.size in 2..3) groups.map { it.title to it.items }
            else {
                val all = groups.flatMap { it.items }
                val per = maxOf(1, (all.size + 2) / 3)
                all.chunked(per).map { "" to it }
            }
        for ((k, col) in columns.withIndex()) {
            val v = Ui.vertical(c)
            if (col.first.isNotBlank()) v.addView(Ui.text(c, col.first.uppercase(), 10f, Ui.TEXT2, Ui.HEADING).apply { letterSpacing = 0.06f }, Ui.lp(b = 4))
            for (i in col.second) {
                val row = Ui.horizontal(c, Gravity.TOP)
                row.setPadding(0, Ui.u(7), 0, Ui.u(7))
                val qty = listOf(i.qty, i.unit).filter { it.isNotBlank() }.joinToString(" ")
                row.addView(Ui.text(c, qty, 12f, Ui.TEXT, Ui.HEADING), LinearLayout.LayoutParams(Ui.u(58), Ui.WRAP))
                row.addView(Ui.text(c, i.name + if (i.note.isNotBlank()) ", " + i.note else "", 12f, Ui.TEXT, Ui.REGULAR).apply { setLineSpacing(0f, 1.15f) },
                    LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                v.addView(row)
                v.addView(Ui.divider(c), Ui.lp(Ui.MATCH, Math.max(1, Ui.u(1))))
            }
            cols.addView(v, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (k > 0) leftMargin = Ui.u(16) })
        }
        if (columns.isEmpty()) cols.addView(Ui.text(c, tr("Pas de liste d'ingrédients."), 13f, Ui.TEXT3))
        return Ui.scroll(c, cols)
    }

    private fun stepsPane(): View {
        val steps = Ui.vertical(c)
        steps.setPadding(Ui.u(22), Ui.u(8), Ui.u(22), Ui.u(18))
        for ((i, s) in recipe.steps.withIndex()) steps.addView(stepRow(i, s), Ui.lp(t = 14))
        return Ui.scroll(c, steps)
    }

    private fun reviewsPane(): View {
        val box = Ui.vertical(c)
        box.setPadding(Ui.u(22), Ui.u(14), Ui.u(22), Ui.u(18))
        McOnline.apiIdOf(recipe.id)?.let { apiId ->
            box.addView(reviewsBox)
            reviewsBox.addView(section(if (recipe.ratingCount > 0)
                tr("Avis · %s/5 (%d notes)", rating(recipe.rating), recipe.ratingCount) else tr("Avis")))
            reviewsBox.addView(reviewsState, Ui.lp(t = 10))
            loadReviews(apiId, 1)
        }
        return Ui.scroll(c, box)
    }

    // ------------------------------------------------------------------ avis

    private val reviewsBox by lazy { Ui.vertical(c) }
    private val reviewsState by lazy { Ui.text(c, "", 16f, Ui.TEXT3) }
    private var moreButton: View? = null
    private var loading = false

    private fun loadReviews(apiId: Long, page: Int) {
        if (loading) return
        loading = true
        reviewsState.visibility = View.VISIBLE
        reviewsState.text = tr("Chargement des avis…")
        Thread {
            val result = try {
                Reviews.load(apiId, page)
            } catch (e: Exception) {
                null
            }
            reviewsBox.post {
                loading = false
                if (result == null) {
                    reviewsState.text = tr("Avis indisponibles (pas de connexion ?)")
                    return@post
                }
                showReviews(apiId, result)
            }
        }.start()
    }

    private fun showReviews(apiId: Long, p: McOnline.ReviewPage) {
        moreButton?.let { reviewsBox.removeView(it) }
        moreButton = null
        if (p.page == 1 && p.reviews.isEmpty()) {
            reviewsState.text = tr("Pas encore de commentaire sur cette recette.")
            return
        }
        reviewsState.visibility = View.GONE
        for (r in p.reviews) reviewsBox.addView(reviewRow(r), Ui.lp(t = 14))
        if (p.page < p.totalPages) {
            val shown = reviewsBox.childCount - 2
            val more = Ui.button(c, tr("Voir plus d'avis (%d sur %d)", shown, p.total), Ui.Style.GHOST, height = 50, textSize = 16f) {
                loadReviews(apiId, p.page + 1)
            }
            moreButton = more
            reviewsBox.addView(more, Ui.lp(Ui.WRAP, Ui.WRAP, t = 12))
        }
    }

    private fun reviewRow(r: McOnline.Review): View {
        val box = Ui.vertical(c)
        box.background = Ui.round(Ui.SURFACE2, 16f)
        box.setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
        val head = Ui.horizontal(c)
        head.addView(Ui.text(c, "★".repeat(r.rating) + "☆".repeat(5 - r.rating), 17f, Ui.ACCENT), Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        val who = listOf(r.author.ifBlank { tr("Anonyme") }, r.date).filter { it.isNotBlank() }.joinToString(" · ")
        head.addView(Ui.ellipsize(Ui.text(c, who, 15f, Ui.TEXT3)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(head)
        if (r.message.isNotBlank()) {
            box.addView(Ui.text(c, r.message, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.12f) }, Ui.lp(t = 6))
        }
        if (r.reply.isNotBlank()) {
            box.addView(Ui.text(c, tr("Réponse de Monsieur Cuisine : %s", r.reply), 15f, Ui.TEXT2)
                .apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 8))
        }
        return box
    }

    private fun rating(v: Double): String = String.format(I18n.locale, "%.1f", v)

    private fun authorName(name: String): String =
        if (name.equals("Monsieur Cuisine Official", ignoreCase = true)) tr("Monsieur Cuisine (recette officielle)") else name

    private fun section(t: String): TextView = Ui.label(c, t, Ui.TEXT2, 12.5f).apply { setPadding(0, Ui.u(6), 0, 0) }

    private fun stepRow(i: Int, s: Step): View {
        val row = Ui.horizontal(c, Gravity.TOP)
        val num = Ui.text(c, "${i + 1}", 15f, Ui.ACCENT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        num.background = Ui.round(Ui.ACCENT_SOFT, 16f)
        row.addView(num, LinearLayout.LayoutParams(Ui.u(32), Ui.u(32)).apply { rightMargin = Ui.u(14) })
        val col = Ui.vertical(c)
        if (s.text.isNotBlank()) col.addView(Ui.text(c, s.text, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.18f) })
        val chips = Ui.horizontal(c)
        s.weigh?.let { chips.addView(Ui.chip(c, tr("Peser %d g", it.grams), Icon.Kind.SCALE, Ui.COOL, Ui.COOL_SOFT, size = 14f), Ui.lp(Ui.WRAP, Ui.WRAP, r = 8)) }
        s.machine?.let { m ->
            val ok = m.valid
            chips.addView(Ui.chip(c, m.summary, if (ok) Icon.Kind.POT else Icon.Kind.WARNING,
                if (ok) Ui.ACCENT else Ui.WARN, if (ok) Ui.ACCENT_SOFT else Ui.WARN_SOFT, 14f))
        }
        if (chips.childCount > 0) col.addView(chips, Ui.lp(t = 8))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        return row
    }

    private fun toggleFavorite() {
        val now = !a.app.recipes.isFavorite(recipe.id)
        a.app.recipes.setFavorite(recipe.id, now)
        refreshFavorite()
        a.toast(if (now) tr("Ajoutée aux favoris") else tr("Retirée des favoris"))
    }

    private fun refreshFavorite() {
        val f = a.app.recipes.isFavorite(recipe.id)
        (favButton.getChildAt(0) as? Icon)?.apply {
            kind = if (f) Icon.Kind.STAR_FILLED else Icon.Kind.STAR
            color = Ui.TEXT
            invalidate()
        }
    }

    override fun onKnobPress(): Boolean {
        a.push(CookScreen(a, recipe, 0))
        return true
    }
}
