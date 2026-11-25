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
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.SearchIndex
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.StatusPills
import app.toque.ui.Ui
import app.toque.ui.tap
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Accueil du design v2 : l'heure et l'état du robot en haut ; à gauche la recette en cours (ou la
 * recherche) et les favoris ; à droite six raccourcis à icône au trait, sans pastille de couleur.
 * Mesures de la maquette (1280 × 800 px) divisées par 1,25 (unités de [Ui]).
 */
class HomeScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Accueil")
    override val section = Section.HOME
    override val showTopBar = false

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var status: StatusPills
    private lateinit var hero: FrameLayout
    private lateinit var favTitle: TextView
    private lateinit var favRow: LinearLayout
    private var unsub: (() -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            refreshClock()
            view.postDelayed(this, 10_000)
        }
    }

    override fun build(): View {
        val col = Ui.vertical(c)
        col.setPadding(Ui.u(26), Ui.u(19), Ui.u(26), 0)

        // En-tête : heure, date, état du robot
        val head = Ui.horizontal(c)
        clock = Ui.text(c, "", 24f, Ui.TEXT, Ui.HEADING).apply { letterSpacing = -0.03f }
        head.addView(clock)
        date = Ui.text(c, "", 13f, Ui.TEXT2, Ui.MEDIUM)
        head.addView(date, Ui.lp(Ui.WRAP, Ui.WRAP, l = 11))
        head.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        status = StatusPills(a, showClock = false)
        head.addView(status)
        col.addView(head, Ui.lp(Ui.MATCH, Ui.u(42)))

        val main = Ui.horizontal(c, Gravity.TOP)
        // Colonne de gauche : recette en cours (ou recherche), puis favoris
        val left = Ui.vertical(c)
        hero = FrameLayout(c)
        left.addView(hero, Ui.lp(Ui.MATCH, 0, 1f))
        val favHead = Ui.horizontal(c, Gravity.BOTTOM)
        favTitle = Ui.text(c, "", 16f, Ui.TEXT, Ui.HEADING)
        favHead.addView(favTitle, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        favHead.addView(Ui.text(c, tr("Tout voir"), 12f, Ui.TEXT2, Ui.MEDIUM).apply {
            setPadding(Ui.u(8), Ui.u(4), 0, Ui.u(2))
            isClickable = true
            tap { openRecipes(focus = false) }
        })
        left.addView(favHead, Ui.lp(t = 14))
        favRow = Ui.horizontal(c, Gravity.TOP)
        left.addView(favRow, Ui.lp(Ui.MATCH, Ui.u(106), t = 8))
        main.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1.45f).apply { rightMargin = Ui.u(14) })

        // Colonne de droite : 2 × 3 raccourcis
        val grid = Ui.vertical(c)
        val tiles = listOf(
            Triple(Icon.Kind.POT, tr("Cuisson manuelle"), tr("Temps, °C, vitesse")) to { a.open(Section.MANUAL) },
            Triple(Icon.Kind.GRID, tr("Programmes"), tr("Malaxer, mijoter…")) to { a.open(Section.PROGRAMS) },
            Triple(Icon.Kind.CHAIN, tr("Importer"), tr("Depuis Cookomix")) to { a.open(Section.RECIPES); a.push(ImportScreen(a)) },
            Triple(Icon.Kind.PEN, tr("Créer"), tr("Ma recette perso")) to { a.open(Section.RECIPES); a.push(EditorScreen(a)) },
            Triple(Icon.Kind.DROP, tr("Prélaver"), tr("Nettoyage du bol")) to { a.open(Section.PROGRAMS) },
            Triple(Icon.Kind.SCALE, tr("Balance"), tr("Peser, tarer")) to { a.open(Section.SCALE) },
        )
        for (r in 0 until 3) {
            val row = Ui.horizontal(c)
            for (k in 0 until 2) {
                val (t, go) = tiles[r * 2 + k]
                row.addView(quick(t.first, t.second, t.third, go), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (k == 0) rightMargin = Ui.u(11) })
            }
            grid.addView(row, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { if (r > 0) topMargin = Ui.u(11) })
        }
        main.addView(grid, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        col.addView(main, Ui.lp(Ui.MATCH, 0, 1f, t = 14))
        return col
    }

    private fun openRecipes(focus: Boolean) {
        a.open(Section.RECIPES)
        if (focus) (a.root(Section.RECIPES) as? RecipesScreen)?.focusSearch()
    }

    /** Raccourci : carte blanche, icône au trait fin (1,4) sans fond, titre et sous-titre. */
    private fun quick(icon: Icon.Kind, title: String, sub: String, onClick: () -> Unit): LinearLayout {
        val t = Ui.vertical(c)
        t.setPadding(Ui.u(16), Ui.u(16), Ui.u(14), Ui.u(14))
        t.background = Ui.pressable(Ui.round(Ui.SURFACE, 21f), 21f)
        t.addView(Icon(c, icon, Ui.TEXT).apply { strokeWidth = 1.4f }, LinearLayout.LayoutParams(Ui.u(32), Ui.u(32)))
        t.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        t.addView(Ui.ellipsize(Ui.text(c, title, 14.5f, Ui.TEXT, Ui.HEADING)))
        t.addView(Ui.ellipsize(Ui.text(c, sub, 11.5f, Ui.TEXT2, Ui.MEDIUM)), Ui.lp(t = 2))
        t.isClickable = true
        t.contentDescription = title
        t.tap { onClick() }
        return t
    }

    /** Fond « photo » : l'image de la recette, sinon un aplat doux tiré de son titre. */
    private fun photo(title: String, image: String, radius: Float): FrameLayout {
        val f = FrameLayout(c)
        f.background = Ui.gradient(Ui.placeholderColors(title), radius)
        f.clipToOutline = true
        if (image.isNotEmpty()) {
            val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            f.addView(img, Ui.flp())
            ImageLoader.load(img, a.app.recipes.imageFile(image), Ui.u(420))
        }
        return f
    }

    /** Grande carte « recette en cours » : photo plein cadre, carte blanche en bas avec la reprise. */
    private fun resumeCard(id: String, title: String, image: String, step: Int, steps: Int): View {
        val card = photo(title, image, 26f)
        val body = Ui.horizontal(c)
        body.setPadding(Ui.u(19), Ui.u(14), Ui.u(14), Ui.u(14))
        body.background = Ui.round(Ui.SURFACE, 19f)
        val txt = Ui.vertical(c)
        txt.addView(Ui.text(c, tr("En cours · étape %d sur %d", step + 1, steps).uppercase(), 10.5f, Ui.ACCENT, Ui.HEADING).apply { letterSpacing = 0.06f })
        txt.addView(Ui.ellipsize(Ui.text(c, title, 17.5f, Ui.TEXT, Ui.HEADING), 1), Ui.lp(t = 5))
        val bar = FrameLayout(c)
        bar.background = Ui.round(Ui.SURFACE2, 2.5f)
        bar.clipToOutline = true
        bar.addView(View(c).apply { background = Ui.round(Ui.HEAT, 2.5f) },
            FrameLayout.LayoutParams(0, Ui.MATCH).also { lp -> bar.post { lp.width = bar.width * (step + 1) / maxOf(1, steps); bar.requestLayout() } })
        txt.addView(bar, Ui.lp(Ui.MATCH, Ui.u(5), t = 8))
        body.addView(txt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        body.addView(Ui.button(c, tr("Reprendre"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 48, textSize = 14f) { resume(id) }, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14))
        card.addView(body, Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM).apply { setMargins(Ui.u(11), 0, Ui.u(11), Ui.u(11)) })
        card.isClickable = true
        card.tap { resume(id) }
        return card
    }

    /** Sans recette en cours : la recherche, en grand. */
    private fun searchCard(count: Int): View {
        val card = Ui.vertical(c, Gravity.BOTTOM)
        card.setPadding(Ui.u(22), Ui.u(20), Ui.u(22), Ui.u(20))
        card.background = Ui.round(Ui.SURFACE, 26f)
        card.addView(Ui.text(c, tr("Qu'est-ce qu'on cuisine ?"), 26f, Ui.TEXT, Ui.HEADING).apply { letterSpacing = -0.02f })
        card.addView(Ui.text(c, if (count > 0) tr("%s recettes à portée de main", String.format(I18n.locale, "%,d", count))
            else tr("Les recettes Lidl arrivent après la première synchronisation (Réglages)."), 13f, Ui.TEXT2, Ui.MEDIUM), Ui.lp(t = 6))
        val search = Ui.horizontal(c)
        search.setPadding(Ui.u(18), 0, Ui.u(18), 0)
        search.background = Ui.pressable(Ui.round(Ui.BG, 24f), 24f)
        search.addView(Icon(c, Icon.Kind.SEARCH, Ui.TEXT2).apply { strokeWidth = 2.2f }, LinearLayout.LayoutParams(Ui.u(18), Ui.u(18)).apply { rightMargin = Ui.u(10) })
        search.addView(Ui.ellipsize(Ui.text(c, tr("Un plat, un ingrédient : poulet, courgette…"), 14.5f, Ui.TEXT3, Ui.REGULAR)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        search.isClickable = true
        search.tap { openRecipes(focus = true) }
        card.addView(search, Ui.lp(Ui.MATCH, Ui.u(48), t = 16))
        return card
    }

    /** Favori : vignette à gauche, titre et durée à droite. */
    private fun favCard(e: SearchIndex.Entry): View {
        val card = Ui.horizontal(c)
        card.background = Ui.pressable(Ui.round(Ui.SURFACE, 18f), 18f)
        card.clipToOutline = true
        card.addView(photo(e.title, e.image, 0f), LinearLayout.LayoutParams(Ui.u(86), Ui.MATCH))
        val txt = Ui.vertical(c)
        txt.setPadding(Ui.u(13), Ui.u(11), Ui.u(12), Ui.u(11))
        txt.addView(Ui.ellipsize(Ui.text(c, e.title, 13f, Ui.TEXT, Ui.HEADING).apply { setLineSpacing(0f, 1.1f) }, 3), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val src = Ui.origin(e.id, e.source).first
        val time = if (e.totalMinutes > 0) Ui.minutes(e.totalMinutes) + " · " else ""
        txt.addView(Ui.ellipsize(Ui.text(c, time + src, 11f, Ui.TEXT2, Ui.MEDIUM)))
        card.addView(txt, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        card.isClickable = true
        card.tap {
            val r = a.app.recipes.get(e.id) ?: return@tap
            a.open(Section.RECIPES)
            a.push(RecipeScreen(a, r))
        }
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
    }

    private fun refresh() {
        val repo = a.app.recipes
        hero.removeAllViews()
        val guided = a.prefs.guidedRecipe
        val entry = if (guided.isEmpty()) null else repo.index.entries.firstOrNull { it.id == guided }
        if (entry != null) {
            val steps = repo.get(entry.id)?.steps?.size ?: 0
            val step = a.prefs.guidedStep.coerceIn(0, maxOf(0, steps - 1))
            hero.addView(resumeCard(entry.id, entry.title, entry.image, step, maxOf(steps, 1)), Ui.flp())
        } else hero.addView(searchCard(if (repo.loaded) repo.count else 0), Ui.flp())

        // Favoris, sinon les mieux notées
        val favs = repo.index.entries.filter { repo.isFavorite(it.id) }
        val list = if (favs.isNotEmpty()) favs.take(3)
        else repo.index.entries.sortedByDescending { it.rating * Math.log(1.0 + it.ratingCount) }.take(3)
        favTitle.text = if (favs.isNotEmpty()) tr("Vos favoris") else tr("Les mieux notées")
        favRow.removeAllViews()
        for (i in 0 until 3) {
            val v = list.getOrNull(i)?.let { favCard(it) } ?: View(c)
            favRow.addView(v, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i > 0) leftMargin = Ui.u(11) })
        }
        val show = list.isNotEmpty()
        favRow.visibility = if (show) View.VISIBLE else View.GONE
        (favTitle.parent as View).visibility = if (show) View.VISIBLE else View.GONE
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
