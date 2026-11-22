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

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(4), Ui.u(22), Ui.u(18))

        // ---- colonne gauche
        val left = Ui.vertical(c)
        val header = FrameLayout(c)
        header.background = Ui.gradient(Ui.placeholderColors(recipe.title), 22f)
        header.addView(Icon(c, Icon.Kind.PLATE, Ui.placeholderInk).apply { strokeWidth = 1.2f }, Ui.flp(Ui.u(70), Ui.u(70), Gravity.CENTER))
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        header.addView(img, Ui.flp())
        header.clipToOutline = true
        header.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, Ui.uf(22f))
        }
        ImageLoader.load(img, a.app.recipes.imageFile(recipe.image), Ui.u(420))
        left.addView(header, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val meta = Ui.horizontal(c)
        if (recipe.totalMinutes > 0) meta.addView(Ui.chip(c, Ui.minutes(recipe.totalMinutes), Icon.Kind.CLOCK), Ui.lp(Ui.WRAP, Ui.WRAP, r = 7))
        if (recipe.servings > 0) meta.addView(Ui.chip(c, tr("%d pers.", recipe.servings), Icon.Kind.PLATE), Ui.lp(Ui.WRAP, Ui.WRAP, r = 7))
        meta.addView(Ui.chip(c, when (recipe.difficulty) { 1 -> tr("Facile"); 2 -> tr("Moyen"); else -> tr("Difficile") }, Icon.Kind.STAR))
        left.addView(meta, Ui.lp(t = 12))

        // Auteur et note du site.
        val byline = ArrayList<String>()
        if (recipe.author.isNotBlank()) byline += tr("Par %s", authorName(recipe.author))
        if (recipe.ratingCount > 0) byline += "★ " + tr("%s/5 · %d notes", rating(recipe.rating), recipe.ratingCount)
        if (byline.isNotEmpty()) {
            left.addView(Ui.ellipsize(Ui.text(c, byline.joinToString("   "), 14.5f, Ui.TEXT2)), Ui.lp(t = 10))
        }

        val actions = Ui.horizontal(c)
        actions.addView(Ui.button(c, tr("Cuisiner"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 60, textSize = 20f) {
            a.push(CookScreen(a, recipe, 0))
        }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(10) })
        favButton = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.STAR, height = 60, textSize = 22f) { toggleFavorite() }
        favButton.contentDescription = tr("Favori")
        actions.addView(favButton)
        left.addView(actions, Ui.lp(t = 14))
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 0.4f).apply { rightMargin = Ui.u(20) })

        // ---- colonne droite : onglets
        val right = Ui.vertical(c)
        tabs = FrameLayout(c)
        right.addView(tabs, Ui.lp(Ui.WRAP, Ui.WRAP))
        pane = FrameLayout(c)
        pane.background = Ui.round(Ui.SURFACE, 22f)
        Ui.lift(pane)
        right.addView(pane, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(12) })
        root.addView(right, LinearLayout.LayoutParams(0, Ui.MATCH, 0.6f))
        showTab(if (recipe.ingredients.isEmpty()) 1 else 0)
        refreshFavorite()
        return root
    }

    private fun showTab(i: Int) {
        tab = i
        tabs.removeAllViews()
        val opts = mutableListOf<Pair<Icon.Kind?, String>>(
            Icon.Kind.BOOK to tr("Ingrédients"),
            Icon.Kind.POT to tr("Étapes (%d)", recipe.steps.size),
        )
        val withReviews = McOnline.apiIdOf(recipe.id) != null
        if (withReviews) opts += Icon.Kind.STAR to tr("Avis")
        tabs.addView(Ui.segmented(c, opts, i) { showTab(it) })
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

    private fun ingredientsPane(): View {
        val ing = Ui.vertical(c)
        ing.setPadding(Ui.u(22), Ui.u(12), Ui.u(22), Ui.u(18))
        for (g in recipe.ingredients) {
            if (g.title.isNotBlank()) ing.addView(Ui.label(c, g.title, Ui.ACCENT, 12.5f), Ui.lp(t = 14, b = 2))
            for (i in g.items) {
                val row = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
                val col = Ingredients.lookup(i.name)?.color
                val dot = View(c)
                dot.background = if (col != null) Ui.round(col, 7f, Ui.shade(col, 0.72f), 1.2f) else Ui.round(0, 7f, Ui.TEXT3, 1.4f)
                row.addView(dot, LinearLayout.LayoutParams(Ui.u(14), Ui.u(14)).apply { rightMargin = Ui.u(12) })
                val qty = listOf(i.qty, i.unit).filter { it.isNotBlank() }.joinToString(" ")
                if (qty.isNotEmpty()) row.addView(Ui.text(c, qty, 17f, Ui.TEXT, Ui.MEDIUM), Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
                row.addView(Ui.text(c, i.name + if (i.note.isNotBlank()) ", " + i.note else "", 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.1f) },
                    LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                ing.addView(row, Ui.lp(t = 10))
                ing.addView(Ui.divider(c), Ui.lp(Ui.MATCH, Math.max(1, Ui.u(1)), t = 10))
            }
        }
        if (recipe.ingredients.isEmpty()) ing.addView(Ui.text(c, tr("Pas de liste d'ingrédients."), 16f, Ui.TEXT3), Ui.lp(t = 8))
        return Ui.scroll(c, ing)
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
        Ui.buttonIcon(favButton)?.apply {
            set(if (f) Icon.Kind.STAR_FILLED else Icon.Kind.STAR)
            color = if (f) Ui.ACCENT else Ui.TEXT
        }
    }

    override fun onKnobPress(): Boolean {
        a.push(CookScreen(a, recipe, 0))
        return true
    }
}
