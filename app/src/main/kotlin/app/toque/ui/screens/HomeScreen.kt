package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.SearchIndex
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.RecipeCard
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.StatusPills
import app.toque.ui.Ui
import app.toque.ui.tap
import java.text.SimpleDateFormat
import java.util.Date

/** Accueil : l'heure en grand, la recherche, la recette en cours, les raccourcis et les favoris. */
class HomeScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Accueil")
    override val section = Section.HOME
    override val showTopBar = false

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var greeting: TextView
    private lateinit var status: StatusPills
    private lateinit var count: TextView
    private lateinit var shortcuts: LinearLayout
    private lateinit var favTitle: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var empty: LinearLayout
    private var unsub: (() -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            refreshClock()
            view.postDelayed(this, 10_000)
        }
    }

    override fun build(): View {
        val col = Ui.vertical(c)
        col.setPadding(Ui.u(26), Ui.u(20), Ui.u(24), Ui.u(18))

        // En-tête : heure, salutation, date, état du robot
        val head = Ui.horizontal(c, Gravity.TOP)
        val left = Ui.horizontal(c, Gravity.BOTTOM)
        clock = Ui.title(c, "", 84f).apply { includeFontPadding = false; letterSpacing = -0.01f }
        left.addView(clock)
        val greet = Ui.vertical(c)
        greeting = Ui.title(c, "", 30f, Ui.ACCENT).apply { includeFontPadding = false }
        greet.addView(greeting)
        date = Ui.text(c, "", 16.5f, Ui.TEXT2)
        greet.addView(date, Ui.lp(t = 6))
        left.addView(greet, Ui.lp(Ui.WRAP, Ui.WRAP, l = 18, b = 6))
        head.addView(left, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        status = StatusPills(a, showClock = false)
        head.addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
        col.addView(head)

        // Recherche
        val search = Ui.horizontal(c)
        search.setPadding(Ui.u(22), 0, Ui.u(9), 0)
        search.background = Ui.pressable(Ui.round(Ui.SURFACE, 31f), 31f)
        Ui.lift(search, 2f)
        search.addView(Icon(c, Icon.Kind.SEARCH, Ui.TEXT2).apply { strokeWidth = 2.1f }, LinearLayout.LayoutParams(Ui.u(24), Ui.u(24)).apply { rightMargin = Ui.u(13) })
        search.addView(Ui.text(c, tr("Rechercher une recette ou un ingrédient"), 18f, Ui.TEXT3), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        count = Ui.text(c, "", 14.5f, Ui.TEXT2).apply { gravity = Gravity.CENTER_VERTICAL }
        count.setPadding(Ui.u(17), 0, Ui.u(17), 0)
        count.background = Ui.round(Ui.SURFACE2, 22f)
        search.addView(count, LinearLayout.LayoutParams(Ui.WRAP, Ui.u(44)))
        search.isClickable = true
        search.tap { openRecipes(focus = true) }
        col.addView(search, Ui.lp(Ui.MATCH, Ui.u(62), t = 20))

        // Raccourcis (et recette en cours)
        shortcuts = Ui.horizontal(c, Gravity.TOP)
        col.addView(shortcuts, Ui.lp(Ui.MATCH, Ui.u(158), t = 16))

        // Favoris
        val favHead = Ui.horizontal(c, Gravity.BOTTOM)
        favTitle = Ui.title(c, "", 23f)
        favHead.addView(favTitle, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val all = Ui.horizontal(c)
        all.addView(Ui.text(c, tr("Toutes les recettes"), 15.5f, Ui.ACCENT, Ui.MEDIUM))
        all.addView(Icon(c, Icon.Kind.CHEVRON, Ui.ACCENT).apply { strokeWidth = 2.3f }, LinearLayout.LayoutParams(Ui.u(16), Ui.u(16)).apply { leftMargin = Ui.u(3) })
        all.setPadding(Ui.u(8), Ui.u(6), 0, Ui.u(4))
        all.isClickable = true
        all.tap { openRecipes(focus = false) }
        favHead.addView(all)
        col.addView(favHead, Ui.lp(t = 18))
        favRow = Ui.horizontal(c, Gravity.TOP)
        col.addView(favRow, Ui.lp(t = 10))
        empty = Ui.horizontal(c)
        empty.background = Ui.round(Ui.SURFACE, 20f)
        empty.setPadding(Ui.u(20), Ui.u(16), Ui.u(16), Ui.u(16))
        empty.addView(Ui.badge(c, Icon.Kind.SYNC), LinearLayout.LayoutParams(Ui.u(46), Ui.u(46)).apply { rightMargin = Ui.u(14) })
        empty.addView(Ui.text(c, tr("Les recettes Lidl apparaîtront après la première synchronisation (Réglages › Recettes Lidl)."), 16f, Ui.TEXT2)
            .apply { setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        empty.addView(Ui.button(c, tr("Réglages"), Ui.Style.SECONDARY, Icon.Kind.SLIDERS, height = 48, textSize = 16f) { a.open(Section.SETTINGS) },
            Ui.lp(Ui.WRAP, Ui.WRAP, l = 12))
        col.addView(empty, Ui.lp(t = 10))
        return Ui.scroll(c, col)
    }

    private fun openRecipes(focus: Boolean) {
        a.open(Section.RECIPES)
        if (focus) (a.root(Section.RECIPES) as? RecipesScreen)?.focusSearch()
    }

    private fun quick(icon: Icon.Kind, title: String, sub: String, color: Int, soft: Int, onClick: () -> Unit): LinearLayout {
        val t = Ui.vertical(c)
        t.setPadding(Ui.u(17), Ui.u(16), Ui.u(14), Ui.u(14))
        t.background = Ui.pressable(Ui.round(Ui.SURFACE, 22f), 22f)
        Ui.lift(t)
        t.addView(Ui.badge(c, icon, color, soft, 48))
        t.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        t.addView(Ui.ellipsize(Ui.title(c, title, 22f), 2).apply { includeFontPadding = false; setLineSpacing(0f, 0.95f) })
        t.addView(Ui.ellipsize(Ui.text(c, sub, 14f, Ui.TEXT2)), Ui.lp(t = 5))
        t.isClickable = true
        t.tap { onClick() }
        return t
    }

    private fun resumeCard(id: String, title: String, image: String, step: Int, steps: Int, stepText: String): LinearLayout {
        val card = Ui.horizontal(c, Gravity.TOP)
        card.background = Ui.pressable(Ui.round(Ui.SURFACE, 22f), 22f)
        card.clipToOutline = true
        Ui.lift(card)
        val photo = FrameLayout(c)
        photo.background = Ui.gradient(Ui.placeholderColors(title), 0f)
        photo.addView(Icon(c, Icon.Kind.PLATE, Ui.placeholderInk).apply { strokeWidth = 1.3f }, Ui.flp(Ui.u(52), Ui.u(52), Gravity.CENTER))
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        photo.addView(img, Ui.flp())
        ImageLoader.load(img, a.app.recipes.imageFile(image), Ui.u(240))
        card.addView(photo, LinearLayout.LayoutParams(Ui.u(112), Ui.MATCH))
        val body = Ui.vertical(c)
        body.setPadding(Ui.u(18), Ui.u(15), Ui.u(16), Ui.u(14))
        body.addView(Ui.label(c, tr("En cours"), Ui.ACCENT, 12f))
        body.addView(Ui.ellipsize(Ui.title(c, title, 21f), 1), Ui.lp(t = 4))
        body.addView(Ui.ellipsize(Ui.text(c, tr("Étape %d sur %d", step + 1, steps) + if (stepText.isNotEmpty()) " · $stepText" else "", 14.5f, Ui.TEXT2)), Ui.lp(t = 4))
        body.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        val segs = Ui.horizontal(c)
        for (i in 0 until steps.coerceAtMost(24)) {
            val v = View(c)
            v.background = Ui.round(if (i < step) Ui.ACCENT else if (i == step) Ui.TEXT else Ui.SURFACE3, 3f)
            segs.addView(v, LinearLayout.LayoutParams(0, Ui.u(5), 1f).apply { if (i > 0) leftMargin = Ui.u(4) })
        }
        body.addView(segs, Ui.lp(b = 12))
        val row = Ui.horizontal(c)
        row.addView(Ui.button(c, tr("Reprendre"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 44, textSize = 16f) { resume(id) })
        body.addView(row)
        card.addView(body, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        card.isClickable = true
        card.tap { resume(id) }
        return card
    }

    private fun resume(id: String) {
        val r = a.app.recipes.get(id)
        if (r == null) {
            a.prefs.guidedRecipe = ""
            refresh()
            return
        }
        a.open(Section.RECIPES)
        a.push(CookScreen(a, r, a.prefs.guidedStep))
    }

    private fun refreshClock() {
        clock.text = MainActivity.now()
        date.text = SimpleDateFormat(tr("EEEE d MMMM"), I18n.locale).format(Date()).replaceFirstChar { it.uppercaseChar() }
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting.text = when (h) {
            in 5..11 -> tr("Bonjour")
            in 12..17 -> tr("Bon après-midi")
            else -> tr("Bonsoir")
        }
    }

    private fun refresh() {
        val repo = a.app.recipes
        val n = repo.count
        count.text = when {
            !repo.loaded -> tr("Chargement…")
            n == 1 -> tr("1 recette")
            else -> tr("%s recettes", String.format(I18n.locale, "%,d", n))
        }
        count.visibility = if (repo.loaded && n == 0) View.GONE else View.VISIBLE

        shortcuts.removeAllViews()
        val guided = a.prefs.guidedRecipe
        val entry = if (guided.isEmpty()) null else repo.index.entries.firstOrNull { it.id == guided }
        val gap = Ui.u(14)
        if (entry != null) {
            val r = repo.get(entry.id)
            val steps = r?.steps?.size ?: 0
            val step = a.prefs.guidedStep.coerceIn(0, maxOf(0, steps - 1))
            val stepText = r?.steps?.getOrNull(step)?.let { s -> (s.text.ifBlank { s.machine?.summary ?: "" }).substringBefore('.').take(60) } ?: ""
            shortcuts.addView(resumeCard(entry.id, entry.title, entry.image, step, maxOf(steps, 1), stepText),
                LinearLayout.LayoutParams(0, Ui.MATCH, 1.7f).apply { rightMargin = gap })
        } else {
            shortcuts.addView(quick(Icon.Kind.BOOK, tr("Recettes"), if (n > 0) tr("%s recettes Lidl", String.format(I18n.locale, "%,d", n))
                else tr("Recettes Lidl"), Ui.ACCENT, Ui.ACCENT_SOFT) { openRecipes(false) },
                LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = gap })
        }
        shortcuts.addView(quick(Icon.Kind.POT, tr("Cuisson manuelle"), tr("Temps, chaleur, vitesse"), Ui.ACCENT, Ui.ACCENT_SOFT) { a.open(Section.MANUAL) },
            LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = gap })
        if (entry == null) shortcuts.addView(quick(Icon.Kind.GRID, tr("Programmes"),
            if (app.toque.core.smart.SmartProgram.available) tr("Pétrir, vapeur, rissoler, mijoter, sous-vide…") else tr("Pétrir, vapeur, rissoler"),
            Ui.ACCENT, Ui.ACCENT_SOFT) { a.open(Section.PROGRAMS) },
            LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = gap })
        shortcuts.addView(quick(Icon.Kind.SCALE, tr("Balance"), tr("Peser au gramme"), Ui.COOL, Ui.COOL_SOFT) { a.open(Section.SCALE) },
            LinearLayout.LayoutParams(0, Ui.MATCH, 1f))

        // Favoris, sinon les recettes les mieux notées
        val favs = repo.index.entries.filter { repo.isFavorite(it.id) }
        val list: List<SearchIndex.Entry> = if (favs.isNotEmpty()) favs.take(4)
        else repo.index.entries.sortedByDescending { it.rating * Math.log(1.0 + it.ratingCount) }.take(4)
        favTitle.text = if (favs.isNotEmpty()) tr("Vos favoris") else tr("Les mieux notées")
        favRow.removeAllViews()
        for ((i, e) in list.withIndex()) {
            val card = RecipeCard(c, 92)
            card.bind(e, repo.isFavorite(e.id), repo.imageFile(e.image))
            card.isClickable = true
            card.tap {
                val r = repo.get(e.id) ?: return@tap
                a.open(Section.RECIPES)
                a.push(RecipeScreen(a, r))
            }
            favRow.addView(card, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (i > 0) leftMargin = gap })
        }
        for (i in list.size until 4) favRow.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = gap })
        val none = repo.loaded && n == 0
        empty.visibility = if (none) View.VISIBLE else View.GONE
        favRow.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        (favTitle.parent as View).visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onShow() {
        refreshClock()
        refresh()
        unsub = a.app.recipes.onChange { refresh() }
        view.removeCallbacks(tick)
        view.postDelayed(tick, 10_000)
    }

    override fun onHide() {
        unsub?.invoke()
        view.removeCallbacks(tick)
    }

    override fun onSnapshot(s: Snapshot) = status.update(s)
}
