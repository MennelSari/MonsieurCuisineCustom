package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.EventType
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.JobState
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.Ingredients
import app.toque.core.recipes.Quantities
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.WeighState
import app.toque.core.recipes.WeighTarget
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap
import app.toque.ui.WeighSession

/**
 * Cuisine pas à pas. Chaque étape « machine » se lance d'un appui ; à la fin du programme,
 * l'étape suivante s'affiche toute seule. Les réglages qui se suivent sans geste à faire entre
 * eux partent ensemble, en un seul programme : la machine passe de l'un à l'autre sans arrêter
 * la lame (sauf changement de sens). La progression est mémorisée (reprise après coupure).
 */
class CookScreen(a: MainActivity, private val recipe: Recipe, startStep: Int) : Screen(a) {
    override val title = recipe.title
    override val section = Section.RECIPES
    override val subtitle: String
        get() = if (recipe.steps.isEmpty()) "" else tr("Étape %d sur %d", index + 1, recipe.steps.size)
    private var index = startStep.coerceIn(0, maxOf(0, recipe.steps.size - 1))
    private val reviewed = HashSet<Int>()
    private val done = HashSet<Int>()
    private var launchedStep = -1
    /** Étapes couvertes par le programme lancé (plusieurs quand elles sont enchaînées). */
    private var launchedCount = 1
    /** Dernière phase de la série vue à l'écran (on ne suit que les changements de phase). */
    private var seenPhase = -1
    /** « Mixer quand même » à chaud déjà accepté pendant cette recette : pas de nouvelle question. */
    private var hotBlendOk = false
    /** Message fixe de l'étape (vérification, refus), réaffiché hors cuisson. */
    private var panelInfo = ""

    private lateinit var segments: LinearLayout
    private lateinit var text: TextView
    private lateinit var bottom: FrameLayout
    private lateinit var side: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var prevBtn: LinearLayout
    private lateinit var nextBtn: LinearLayout
    /** Pesée de l'étape (cible donnée par la recette), affichée dans la page. */
    private var weigh: WeighSession? = null
    /** Pesée lancée depuis une quantité repérée dans le texte, dans une fenêtre. */
    private var popup: WeighSession? = null
    private var popupQueue: List<WeighTarget> = emptyList()
    private var popupIndex = 0
    private var popupNext: LinearLayout? = null
    private var popupCounter: TextView? = null

    override val showTopBar = false
    override val showNav = false
    private lateinit var headTitle: TextView
    private lateinit var headStep: TextView
    private lateinit var progress: FrameLayout
    private lateinit var progressFill: View
    private lateinit var stepsList: LinearLayout
    private lateinit var bowlView: android.widget.ImageView

    /**
     * Design v2 : en-tête (fermer, recette, « Étape 7 sur 18 », fine barre de progression) ;
     * au centre la consigne en grand dans une carte blanche avec le rendu du bol ; à droite le
     * panneau du réglage machine (ou la jauge de pesée) puis la liste des étapes ; en bas
     * « Précédente » et « Suivante ». La logique de cuisson et de sécurité est celle de Camybrith.
     */
    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(19), Ui.u(16), Ui.u(19), Ui.u(4))
        val head = Ui.horizontal(c)
        val close = FrameLayout(c)
        close.background = Ui.pressable(Ui.round(Ui.SURFACE, 22.5f), 22.5f)
        close.addView(Icon(c, Icon.Kind.CLOSE, Ui.TEXT).apply { strokeWidth = 2.6f }, Ui.flp(Ui.u(16), Ui.u(16), Gravity.CENTER))
        close.isClickable = true
        close.contentDescription = tr("Quitter la cuisson")
        close.tap { a.back() }
        head.addView(close, LinearLayout.LayoutParams(Ui.u(45), Ui.u(45)))
        val ht = Ui.vertical(c)
        val line = Ui.horizontal(c)
        headTitle = Ui.ellipsize(Ui.text(c, recipe.title, 12f, Ui.TEXT, Ui.HEADING))
        line.addView(headTitle, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        headStep = Ui.text(c, "", 12f, Ui.TEXT2, Ui.HEADING)
        line.addView(headStep)
        ht.addView(line)
        progress = FrameLayout(c)
        progress.background = Ui.round(Ui.SURFACE3, 2.5f)
        progress.clipToOutline = true
        progressFill = View(c).apply { background = Ui.round(Ui.INK, 2.5f) }
        progress.addView(progressFill, FrameLayout.LayoutParams(0, Ui.MATCH))
        ht.addView(progress, Ui.lp(Ui.MATCH, Ui.u(5), t = 7))
        head.addView(ht, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(13) })
        root.addView(head)
        segments = Ui.horizontal(c) // remplacé par la barre de progression, gardé pour la logique

        val body = Ui.horizontal(c, Gravity.TOP)
        // Gauche : la liste des étapes.
        val stepsCard = Ui.vertical(c)
        stepsCard.setPadding(Ui.u(13), Ui.u(14), Ui.u(13), Ui.u(10))
        stepsCard.background = Ui.round(Ui.SURFACE, 22f)
        stepsCard.addView(Ui.text(c, tr("Étapes"), 14f, Ui.TEXT, Ui.HEADING), Ui.lp(b = 8, l = 3))
        stepsList = Ui.vertical(c)
        stepsCard.addView(stepsList)
        body.addView(stepsCard, LinearLayout.LayoutParams(Ui.u(222), Ui.MATCH).apply { rightMargin = Ui.u(12) })
        // Centre : la consigne et le bol.
        val center = Ui.vertical(c)
        center.setPadding(Ui.u(22), Ui.u(20), Ui.u(22), Ui.u(16))
        center.background = Ui.round(Ui.SURFACE, 22f)
        text = Ui.text(c, "", 22f, Ui.TEXT, Ui.HEADING).apply { setLineSpacing(0f, 1.18f); letterSpacing = -0.02f }
        center.addView(Ui.scroll(c, text), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        bowlView = Ui.bowl(c, "couvercle")
        center.addView(bowlView, Ui.lp(Ui.MATCH, Ui.u(170), t = 6))
        bottom = FrameLayout(c)
        center.addView(bottom, Ui.lp(t = 10))
        body.addView(center, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(12) })
        // Droite : réglage machine (ou jauge de pesée), toute la hauteur.
        side = FrameLayout(c)
        panel = Ui.vertical(c)
        panel.background = Ui.round(Ui.SURFACE, 22f)
        panel.setPadding(Ui.u(18), Ui.u(16), Ui.u(18), Ui.u(14))
        body.addView(side, LinearLayout.LayoutParams(Ui.u(322), Ui.MATCH))
        root.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(13) })

        val nav = Ui.horizontal(c)
        prevBtn = Ui.button(c, tr("Précédente"), Ui.Style.SECONDARY, Icon.Kind.BACK, height = 58, textSize = 14f) { go(index - 1) }
        prevBtn.background = Ui.pressable(Ui.round(Ui.SURFACE, 29f), 29f)
        nav.addView(prevBtn, LinearLayout.LayoutParams(Ui.u(190), Ui.WRAP))
        nav.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))
        nextBtn = Ui.button(c, tr("Suivante"), Ui.Style.SECONDARY, Icon.Kind.CHEVRON, height = 58, textSize = 14f, iconAfter = true) { next() }
        nav.addView(nextBtn, LinearLayout.LayoutParams(Ui.u(190), Ui.WRAP))
        root.addView(nav, Ui.lp(t = 11))
        render()
        return root
    }

    /** Liste des étapes autour de l'étape en cours : faites en vert, en cours en noir. */
    private fun renderSteps() {
        stepsList.removeAllViews()
        val n = recipe.steps.size
        val from = (index - 2).coerceIn(0, maxOf(0, n - 7))
        for (i in from until minOf(n, from + 7)) {
            val st = recipe.steps[i]
            val cur = i == index
            val row = Ui.horizontal(c, Gravity.TOP)
            row.setPadding(Ui.u(8), Ui.u(6), Ui.u(8), Ui.u(6))
            row.background = Ui.pressable(Ui.round(if (cur) Ui.BG else Ui.SURFACE, 13f), 13f)
            row.alpha = if (i < index) 0.55f else 1f
            val dot = Ui.text(c, "${i + 1}", 9.5f, if (cur || i < index) Ui.ON_INK else Ui.TEXT, Ui.HEADING).apply { gravity = Gravity.CENTER }
            dot.background = Ui.round(if (cur) Ui.INK else if (i < index) Ui.OK else Ui.SURFACE2, 10.5f)
            row.addView(dot, LinearLayout.LayoutParams(Ui.u(21), Ui.u(21)))
            val col = Ui.vertical(c)
            col.addView(Ui.ellipsize(Ui.text(c, st.text.lineSequence().first().ifBlank { st.machine?.summary ?: "" }, 11f, Ui.TEXT, Ui.MEDIUM)))
            st.machine?.let { col.addView(Ui.ellipsize(Ui.text(c, it.summary, 10f, Ui.TEXT2, Ui.HEADING)), Ui.lp(t = 1)) }
            row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(9) })
            row.isClickable = true
            row.tap { go(i) }
            stepsList.addView(row, Ui.lp(t = if (stepsList.childCount > 0) 3 else 0))
        }
    }

    /** Rendu du bol selon le réglage de l'étape. */
    private fun bowlFor(m: app.toque.core.recipes.StepMachine?): String = when (m?.program) {
        null -> "vide"
        app.toque.core.recipes.StepProgram.STEAM -> "vapeur"
        app.toque.core.recipes.StepProgram.KNEAD -> "petrir"
        app.toque.core.recipes.StepProgram.TOAST -> "rissoler"
        app.toque.core.recipes.StepProgram.FERMENT -> "fermentation"
        app.toque.core.recipes.StepProgram.SOUS_VIDE -> "sousvide"
        app.toque.core.recipes.StepProgram.SMOOTHIE -> "smoothie"
        else -> if (m.tempC > 0) "chauffe" else "couvercle"
    }

    private fun next() {
        if (index >= recipe.steps.size - 1) {
            a.prefs.guidedRecipe = ""
            a.sound(app.toque.Sounds.Kind.RECIPE)
            a.toast(tr("Bon appétit !"), Ui.OK)
            a.back()
        } else go(index + 1)
    }

    private fun go(i: Int) {
        if (i < 0 || i >= recipe.steps.size) return
        index = i
        a.prefs.guidedRecipe = recipe.id
        a.prefs.guidedStep = index
        render()
    }

    private fun render() {
        val n = recipe.steps.size
        headStep.text = if (n == 0) "" else tr("Étape %d sur %d", index + 1, n)
        progress.post {
            (progressFill.layoutParams as FrameLayout.LayoutParams).width = if (n == 0) 0 else progress.width * (index + 1) / n
            progressFill.requestLayout()
        }
        renderSteps()
        a.refreshTitle()
        bottom.removeAllViews()
        side.removeAllViews()
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
        weigh = null
        if (n == 0) {
            text.text = tr("Cette recette ne contient pas d'étapes.")
            panel.removeAllViews()
            return
        }
        val step = recipe.steps[index]
        text.text = step.text.ifBlank { step.machine?.summary ?: "" }
        bowlView.setImageBitmap(Ui.bowlBitmap(c, bowlFor(step.machine)))
        Ui.setButtonEnabled(prevBtn, index > 0)
        Ui.buttonText(nextBtn)?.text = if (index == n - 1) tr("Terminer") else tr("Suivante")
        val w = step.weigh
        if (w != null) {
            // Pesée donnée par la recette : la jauge prend la place du panneau.
            val ing = matching(w.grams)
            var target = WeighTarget.grams(w.label, w.grams, step.text)
            if (target.info == null && ing != null) target = target.copy(info = Ingredients.lookup(ing.name))
            // Le libellé d'une pesée Lidl est souvent la phrase de l'étape : on préfère le nom de l'ingrédient.
            target = target.copy(label = ing?.name ?: w.label.takeIf { it.length <= 30 && it != step.text }.orEmpty())
            val session = WeighSession(a, target)
            weigh = session
            session.onState = { st -> highlightNext(st == WeighState.OK) }
            bottom.addView(session.info, Ui.flp(Ui.MATCH, Ui.WRAP))
            side.addView(session.gaugeCard(330), Ui.flp())
            if (step.machine != null) {
                // Rare : pesée et réglage dans la même étape. Le réglage passe sous la jauge, en court.
                side.removeAllViews()
                val stack = Ui.vertical(c)
                stack.addView(session.gaugeCard(330), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
                buildPanel(compact = true)
                stack.addView(panel, Ui.lp(Ui.MATCH, Ui.WRAP, t = 12))
                side.addView(stack, Ui.flp())
            }
        } else {
            buildPanel()
            side.addView(panel, Ui.flp())
            // Quantités écrites dans l'étape ; sinon (recettes Thermomix) ingrédients cités, avec les
            // grammes de la liste des ingrédients.
            val found = Quantities.find(step.text, recipe.allIngredients).ifEmpty { Quantities.mentioned(step.text, recipe.allIngredients) }
            if (found.isNotEmpty()) bottom.addView(quantityChips(found), Ui.flp(Ui.MATCH, Ui.WRAP))
        }
        highlightNext(false)
        onSnapshot(a.snapshot)
    }

    /** Ingrédient de la recette dont la quantité en grammes est celle d'une pesée. */
    private fun matching(grams: Int) = recipe.allIngredients.firstOrNull { i ->
        (i.unit.equals("g", true) || i.unit.isBlank()) && i.qty.replace(',', '.').toDoubleOrNull()?.let { Math.round(it).toInt() == grams } == true
    }

    private fun highlightNext(on: Boolean) {
        nextBtn.background = Ui.pressable(Ui.round(if (on) Ui.INK else Ui.SURFACE, 29f), 29f)
        Ui.buttonText(nextBtn)?.setTextColor(if (on) Ui.ON_INK else Ui.TEXT)
        Ui.buttonIcon(nextBtn)?.color = if (on) Ui.ON_INK else Ui.TEXT
    }

    /** Quantités écrites dans l'étape, à peser d'un appui. */
    private fun quantityChips(found: List<WeighTarget>): View {
        val box = Ui.vertical(c)
        box.addView(Ui.label(c, tr("À peser"), Ui.TEXT3, 12f), Ui.lp(b = 8))
        val row = Ui.horizontal(c)
        for ((i, t) in found.take(4).withIndex()) {
            val col = t.info?.color ?: Ui.COOL
            val chip = Ui.horizontal(c)
            chip.setPadding(Ui.u(14), 0, Ui.u(16), 0)
            chip.minimumHeight = Ui.u(46)
            chip.background = Ui.pressable(Ui.round(Ui.SURFACE, 23f, Ui.STROKE, 1.2f), 23f)
            Ui.lift(chip, 1f)
            chip.addView(View(c).apply { background = Ui.round(col, 8f, Ui.shade(col, 0.72f), 1.2f) },
                LinearLayout.LayoutParams(Ui.u(16), Ui.u(16)).apply { rightMargin = Ui.u(9) })
            chip.addView(Ui.text(c, WeighSession.format(t.amount, t.unit) + " " + t.unit.symbol + " " + t.label, 16f, Ui.TEXT, Ui.MEDIUM))
            chip.isClickable = true
            chip.tap { openWeigh(found, i) }
            row.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        }
        val sc = android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        box.addView(sc)
        return box
    }

    /** Fenêtre de pesée : les quantités de l'étape, l'une après l'autre, avec zéro automatique. */
    private fun openWeigh(items: List<WeighTarget>, start: Int) {
        popupQueue = items
        popupIndex = start
        val session = WeighSession(a, items[start])
        popup = session
        val card = Ui.vertical(c)
        card.background = Ui.round(Ui.BG, 28f)
        card.setPadding(Ui.u(26), Ui.u(20), Ui.u(22), Ui.u(20))
        val head = Ui.horizontal(c)
        head.addView(Ui.badge(c, Icon.Kind.SCALE, Ui.COOL, Ui.COOL_SOFT, 42), LinearLayout.LayoutParams(Ui.u(42), Ui.u(42)).apply { rightMargin = Ui.u(12) })
        head.addView(Ui.title(c, tr("Pesée"), 25f))
        val counter = Ui.label(c, "", Ui.TEXT2, 12.5f)
        popupCounter = counter
        head.addView(counter, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14, t = 4))
        head.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        val close = FrameLayout(c)
        close.background = Ui.pressable(Ui.round(Ui.SURFACE2, 21f), 21f)
        close.addView(Icon(c, Icon.Kind.CLOSE, Ui.TEXT).apply { strokeWidth = 2.2f }, Ui.flp(Ui.u(18), Ui.u(18), Gravity.CENTER))
        close.isClickable = true
        close.contentDescription = tr("Fermer")
        close.tap { closeWeigh() }
        head.addView(close, LinearLayout.LayoutParams(Ui.u(42), Ui.u(42)))
        card.addView(head)
        val body = Ui.horizontal(c, Gravity.BOTTOM)
        val left = Ui.vertical(c)
        left.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        left.addView(session.info)
        val next = Ui.button(c, "…", Ui.Style.SECONDARY, Icon.Kind.CHEVRON, height = 52, textSize = 16f, iconAfter = true) { popupAdvance() }
        popupNext = next
        left.addView(next, Ui.lp(Ui.WRAP, Ui.WRAP, t = 18))
        body.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(18) })
        body.addView(session.gaugeCard(300), LinearLayout.LayoutParams(Ui.u(300), Ui.MATCH))
        card.addView(body, Ui.lp(Ui.MATCH, Ui.u(400), t = 16))
        session.onState = { st ->
            // Cible atteinte : on passe tout seul à la quantité suivante si le poids ne bouge plus.
            if (st == WeighState.OK) {
                val idx = popupIndex
                view.postDelayed({ if (popup === session && popupIndex == idx && session.state == WeighState.OK && popupIndex < popupQueue.size - 1) popupAdvance() }, 1_500)
            }
        }
        refreshPopup()
        a.showOverlay(card, 820, dismissOnScrim = false)
    }

    private fun refreshPopup() {
        val n = popupQueue.size
        popupCounter?.text = if (n > 1) tr("%d sur %d", popupIndex + 1, n).uppercase() else ""
        val next = popupNext ?: return
        if (popupIndex < n - 1) {
            val t = popupQueue[popupIndex + 1]
            Ui.buttonText(next)?.text = tr("Ensuite : %s", WeighSession.format(t.amount, t.unit) + " " + t.unit.symbol + " " + t.label)
        } else Ui.buttonText(next)?.text = tr("Terminer la pesée")
    }

    private fun popupAdvance() {
        val session = popup ?: return
        if (popupIndex >= popupQueue.size - 1) { closeWeigh(); return }
        popupIndex++
        session.apply(popupQueue[popupIndex])
        refreshPopup()
    }

    private fun closeWeigh() {
        popup = null
        a.closeOverlay()
    }

    private lateinit var startBtn: View
    private var turboBtn: HoldButton? = null
    private var reviewBtn: View? = null
    private var panelState: TextView? = null

    private fun buildPanel(compact: Boolean = false) {
        panel.removeAllViews()
        turboBtn = null
        reviewBtn = null
        val m = recipe.steps[index].machine
        if (m == null) {
            val box = Ui.vertical(c, Gravity.CENTER)
            box.addView(Ui.badge(c, Icon.Kind.CHECK, Ui.TEXT2, Ui.SURFACE2, 56))
            box.addView(Ui.text(c, tr("Pas de réglage machine pour cette étape"), 17f, Ui.TEXT2).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f) },
                Ui.lp(t = 14))
            panel.addView(box, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
            startBtn = Ui.button(c, tr("Étape suivante"), Ui.Style.SECONDARY, Icon.Kind.CHEVRON, height = 60, iconAfter = true) { next() }
            panel.addView(startBtn)
            addWeighLink()
            return
        }
        val kindIcon = when (m.program) {
            StepProgram.KNEAD -> Icon.Kind.KNEAD
            StepProgram.STEAM -> Icon.Kind.STEAM
            StepProgram.TOAST -> Icon.Kind.FLAME
            StepProgram.TURBO -> Icon.Kind.BOLT
            StepProgram.MANUAL -> Icon.Kind.POT
            StepProgram.SOUS_VIDE, StepProgram.SLOW_COOK, StepProgram.FERMENT -> Icon.Kind.THERMO
            StepProgram.PUREE, StepProgram.SMOOTHIE -> Icon.Kind.BLADE
        }
        val kindName = when (m.program) {
            StepProgram.MANUAL -> tr("Réglage machine")
            StepProgram.KNEAD -> JobKind.KNEAD.label
            StepProgram.STEAM -> JobKind.STEAM.label
            StepProgram.TOAST -> JobKind.TOAST.label
            StepProgram.TURBO -> JobKind.TURBO.label
            StepProgram.PUREE, StepProgram.SMOOTHIE, StepProgram.SOUS_VIDE, StepProgram.SLOW_COOK, StepProgram.FERMENT -> m.program.smart!!.label
        }
        if (compact) {
            panel.addView(Ui.chip(c, m.summary, kindIcon, Ui.ACCENT, Ui.ACCENT_SOFT, 15f), Ui.lp(Ui.WRAP, Ui.WRAP))
        } else {
            val head = Ui.horizontal(c)
            head.addView(Ui.badge(c, kindIcon, Ui.ACCENT, Ui.ACCENT_SOFT, 44), LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { rightMargin = Ui.u(12) })
            head.addView(Ui.title(c, kindName, 23f))
            panel.addView(head)

            val grid = Ui.vertical(c)
            fun line(icon: Icon.Kind, label: String, value: String, color: Int = Ui.TEXT) {
                val r = Ui.horizontal(c)
                r.setPadding(0, Ui.u(9), 0, Ui.u(9))
                r.addView(Icon(c, icon, Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(20), Ui.u(20)).apply { rightMargin = Ui.u(10) })
                r.addView(Ui.text(c, label, 15.5f, Ui.TEXT2), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                r.addView(Ui.text(c, value, 25f, color, Ui.LIGHT))
                if (grid.childCount > 0) grid.addView(Ui.divider(c))
                grid.addView(r)
            }
            if (m.program == StepProgram.TURBO) {
                line(Icon.Kind.BOLT, tr("Impulsions"), if (m.pulses > 0) "× ${m.pulses}" else tr("au besoin"))
            } else {
                line(Icon.Kind.CLOCK, tr("Durée"), Ui.duration(m.seconds))
                if (m.program == StepProgram.MANUAL) {
                    line(Icon.Kind.THERMO, tr("Température"), if (m.tempC > 0) "${TemperatureTable.current.celsius(m.temperatureLevel)} °C" else "—",
                        if (m.tempC > 0) Ui.tempColor(m.tempC) else Ui.TEXT2)
                    line(Icon.Kind.SPEED, tr("Vitesse"), "${m.speed}")
                    if (m.reverse) line(Icon.Kind.REVERSE, tr("Sens"), tr("Inverse"), Ui.COOL)
                    if (m.accessory == Accessory.WHISK) line(Icon.Kind.WHISK, tr("Accessoire"), Accessory.WHISK.label, Ui.WARN)
                }
            }
            panel.addView(grid, Ui.lp(t = 8))
            val chained = if (a.prefs.chainSteps) chainLength(index) else 0
            if (chained > 0) {
                val next = (index + 1..index + chained).map { recipe.steps[it].machine!!.summary }
                val list = next.take(3).joinToString("\n") { "• $it" } +
                    if (next.size > 3) "\n" + tr("… et %d autres", next.size - 3) else ""
                panel.addView(Ui.text(c, tr("Puis, enchaînés automatiquement :") + "\n" + list, 13.5f, Ui.TEXT2)
                    .apply { setLineSpacing(0f, 1.12f) }, Ui.lp(t = 10))
            }
        }
        panelState = Ui.text(c, "", 15f, Ui.WARN).apply { setLineSpacing(0f, 1.1f) }
        panel.addView(panelState, Ui.lp(t = 10))
        panel.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))

        val violations = m.violations()
        val step = recipe.steps[index]
        panelInfo = ""
        if (violations.isNotEmpty()) {
            panelInfo = tr("Réglage refusé par la sécurité : %s. Utilisez la cuisson manuelle.", violations.first().message)
        }
        if (step.needsReview && index !in reviewed && violations.isEmpty()) {
            val rb = Ui.button(c, tr("J'ai vérifié ce réglage"), Ui.Style.GHOST, Icon.Kind.CHECK, height = 50, textSize = 16f) {
                reviewed += index
                buildPanel(compact)
                onSnapshot(a.snapshot)
            }
            reviewBtn = rb
            panel.addView(rb, Ui.lp(b = 10))
            panelInfo = tr("Réglage importé : vérifiez-le avant de lancer.")
        }
        panelState?.text = panelInfo
        if (m.program == StepProgram.TURBO) {
            val t = HoldButton(c, tr("Maintenir pour Turbo"), onDown = {
                a.machine.turboDown(m.accessory) { v -> if (!v.ok) a.refusal(v) }
            }, onUp = { a.machine.turboUp() })
            turboBtn = t
            startBtn = t
            panel.addView(t, Ui.lp(Ui.MATCH, Ui.u(64)))
        } else {
            startBtn = Ui.button(c, tr("Lancer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = if (compact) 56 else 68, textSize = 21f) { launch() }
            panel.addView(startBtn)
        }
        if (m.program == StepProgram.MANUAL && !compact) {
            val adjust = Ui.text(c, tr("Ajuster à la main"), 15.5f, Ui.TEXT2, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
            adjust.setPadding(0, Ui.u(12), 0, Ui.u(4))
            adjust.isClickable = true
            adjust.tap { a.push(ManualScreen(a, m)) }
            panel.addView(adjust, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        if (!compact) addWeighLink()
        val blocked = violations.isNotEmpty() || (step.needsReview && index !in reviewed)
        Ui.setButtonEnabled(startBtn, !blocked)
    }

    /** « Peser un ingrédient » : n'importe quel ingrédient pesable de la recette, sans quitter l'étape. */
    private fun addWeighLink() {
        if (recipe.steps[index].weigh != null || Quantities.weighable(recipe.allIngredients).isEmpty()) return
        val link = Ui.text(c, tr("Peser un ingrédient"), 15.5f, Ui.TEXT2, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        link.setPadding(0, Ui.u(10), 0, Ui.u(4))
        link.isClickable = true
        link.tap { chooseWeigh() }
        panel.addView(link, Ui.lp(Ui.MATCH, Ui.WRAP))
    }

    private fun chooseWeigh() {
        val step = recipe.steps[index]
        val cited = Quantities.mentioned(step.text, recipe.allIngredients).map { it.label }.toSet()
        // Ceux que cite l'étape d'abord, puis les autres dans l'ordre de la recette.
        val all = Quantities.weighable(recipe.allIngredients).map { it.second }.sortedBy { if (it.label in cited) 0 else 1 }
        val card = Ui.vertical(c)
        card.background = Ui.round(Ui.BG, 28f)
        card.setPadding(Ui.u(26), Ui.u(20), Ui.u(22), Ui.u(20))
        val head = Ui.horizontal(c)
        head.addView(Ui.badge(c, Icon.Kind.SCALE, Ui.COOL, Ui.COOL_SOFT, 42), LinearLayout.LayoutParams(Ui.u(42), Ui.u(42)).apply { rightMargin = Ui.u(12) })
        head.addView(Ui.title(c, tr("Peser un ingrédient"), 25f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val close = FrameLayout(c)
        close.background = Ui.pressable(Ui.round(Ui.SURFACE2, 21f), 21f)
        close.addView(Icon(c, Icon.Kind.CLOSE, Ui.TEXT).apply { strokeWidth = 2.2f }, Ui.flp(Ui.u(18), Ui.u(18), Gravity.CENTER))
        close.isClickable = true
        close.contentDescription = tr("Fermer")
        close.tap { a.closeOverlay() }
        head.addView(close, LinearLayout.LayoutParams(Ui.u(42), Ui.u(42)))
        card.addView(head)
        val list = Ui.vertical(c)
        for ((i, t) in all.withIndex()) {
            val col = t.info?.color ?: Ui.COOL
            val row = Ui.horizontal(c)
            row.setPadding(Ui.u(16), 0, Ui.u(16), 0)
            row.minimumHeight = Ui.u(54)
            row.background = Ui.pressable(Ui.round(if (t.label in cited) Ui.ACCENT_SOFT else Ui.SURFACE, 18f), 18f)
            row.addView(View(c).apply { background = Ui.round(col, 8f, Ui.shade(col, 0.72f), 1.2f) },
                LinearLayout.LayoutParams(Ui.u(16), Ui.u(16)).apply { rightMargin = Ui.u(12) })
            row.addView(Ui.text(c, t.label, 18f, Ui.TEXT, Ui.MEDIUM), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            row.addView(Ui.text(c, WeighSession.format(t.amount, t.unit) + " " + t.unit.symbol, 18f, Ui.TEXT2))
            row.isClickable = true
            row.tap {
                a.closeOverlay()
                openWeigh(all, i)
            }
            list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, b = 8))
        }
        val sc = android.widget.ScrollView(c).apply { addView(list) }
        card.addView(sc, Ui.lp(Ui.MATCH, Ui.u(380), t = 16))
        a.showOverlay(card, 700)
    }

    private fun launch() {
        val first = index
        val m = recipe.steps[first].machine ?: return
        val series = series(first)
        val job = series ?: m.toJob(tr("Étape %d · %s", first + 1, recipe.title), hotBlendConfirmed = hotBlendOk) ?: return
        val count = series?.phases?.size ?: 1
        a.prefs.guidedRecipe = recipe.id
        a.prefs.guidedStep = first
        a.startJob(job, onHotConfirmed = { hotBlendOk = true }) { v ->
            if (v.ok) {
                launchedStep = first
                launchedCount = count
                seenPhase = 0
            }
        }
    }

    /**
     * Réglages enchaînés à partir de [first], réunis en un seul programme (une phase par étape) :
     * la machine passe directement au réglage suivant, sans arrêt ni nouvel appui. Uniquement
     * des réglages manuels avec le même accessoire ; sinon les étapes s'enchaînent une à une.
     */
    private fun series(first: Int): JobSpec? {
        if (!a.prefs.chainSteps) return null
        val n = chainLength(first)
        if (n == 0) return null
        val machines = (first..first + n).map { recipe.steps[it].machine ?: return null }
        if (machines.any { it.program != StepProgram.MANUAL || it.accessory != machines[0].accessory }) return null
        val phases = machines.map { it.toJob("", hotBlendOk)?.phases?.singleOrNull() ?: return null }
        val spec = JobSpec(
            kind = JobKind.MANUAL,
            label = tr("Étapes %d à %d · %s", first + 1, first + n + 1, recipe.title),
            phases = phases,
            totalMs = phases.sumOf { it.durationMs },
            cyclic = false,
            accessory = machines[0].accessory,
            hotBlendConfirmed = hotBlendOk,
        )
        return spec.takeIf { SafetyPolicy.checkJob(it, null).isEmpty() }
    }

    /**
     * L'étape [i] continue directement la précédente : un réglage machine seul (pas de texte
     * à suivre, pas de pesée), après une autre étape machine. Le Turbo reste toujours à l'appui.
     */
    private fun continues(i: Int): Boolean {
        val s = recipe.steps.getOrNull(i) ?: return false
        val m = s.machine ?: return false
        val prev = recipe.steps.getOrNull(i - 1)?.machine ?: return false
        return m.program != StepProgram.TURBO && prev.program != StepProgram.TURBO && s.weigh == null &&
            (s.text.isBlank() || s.text == m.summary) && m.violations().isEmpty()
    }

    /** Nombre d'étapes qui s'enchaîneront après l'étape [i]. */
    private fun chainLength(i: Int): Int {
        var n = 0
        while (continues(i + n + 1)) n++
        return n
    }

    override fun onSnapshot(s: Snapshot) {
        weigh?.update(s)
        popup?.update(s)
        val running = launchedStep >= 0 && s.jobActive
        if (running && launchedCount > 1 && s.phaseIndex != seenPhase &&
            s.state != JobState.FINISHED && s.state != JobState.ABORTED) {
            // Série en cours : à chaque nouveau réglage, l'écran passe à l'étape correspondante.
            seenPhase = s.phaseIndex
            val current = launchedStep + s.phaseIndex.coerceIn(0, launchedCount - 1)
            for (k in launchedStep until current) done += k
            reviewed += current
            if (current != index) {
                go(current)
                return
            }
        }
        val m = recipe.steps.getOrNull(index)?.machine ?: return
        val mine = running && index >= launchedStep && index < launchedStep + launchedCount
        val ps = panelState
        if (m.program != StepProgram.TURBO && ::startBtn.isInitialized) {
            startBtn.visibility = if (mine && s.state != JobState.FINISHED && s.state != JobState.ABORTED) View.GONE else View.VISIBLE
            if (s.busy && !mine) Ui.setButtonEnabled(startBtn, false)
            else if (reviewBtn == null && m.violations().isEmpty()) Ui.setButtonEnabled(startBtn, !s.busy)
        }
        if (!mine && ps != null) {
            if (index in done) {
                ps.setTextColor(Ui.OK)
                ps.text = tr("Étape terminée")
            } else {
                ps.setTextColor(Ui.WARN)
                ps.text = panelInfo
            }
        }
        if (mine && ps != null) {
            ps.setTextColor(Ui.TEXT2)
            ps.text = when (s.state) {
                JobState.RUNNING, JobState.STARTING -> tr("En cours… reste %s", Ui.clock(s.remainingMs))
                JobState.STOPPING -> tr("Arrêt du moteur…")
                JobState.PAUSED -> tr("En pause")
                JobState.FINISHED -> tr("Terminé")
                JobState.ABORTED -> s.endReason
                null -> ""
            }
        }
        turboBtn?.let { t ->
            t.isEnabled = !s.busy || s.turboActive
            t.alpha = if (t.isEnabled) 1f else 0.35f
        }
    }

    override fun onEvent(e: MachineEvent) {
        if (e.type == EventType.JOB_FINISHED && launchedStep >= 0 && index >= launchedStep && index < launchedStep + launchedCount) {
            val finished = launchedStep + launchedCount - 1
            for (k in launchedStep..finished) done += k
            launchedStep = -1
            launchedCount = 1
            if (index != finished) go(finished)
            // Étapes non réunissables en un programme (vapeur puis réglage…) : la suivante part seule.
            val chain = a.prefs.chainSteps && continues(finished + 1)
            view.postDelayed({
                if (index == finished) {
                    a.machine.clearFinished()
                    if (index < recipe.steps.size - 1) {
                        // Étape enchaînée : le réglage a été vu avec la première étape de la série.
                        if (chain) reviewed += finished + 1
                        go(finished + 1)
                        if (chain) launch()
                    }
                }
            }, if (chain) 400L else 1_500L)
        }
    }

    override fun onHide() {
        popup = null
    }

    override fun onKnob(delta: Int): Boolean {
        go(index + delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        val m = recipe.steps.getOrNull(index)?.machine
        if (m == null) next() else if (m.program != StepProgram.TURBO && startBtn.isEnabled && startBtn.visibility == View.VISIBLE) launch()
        return true
    }
}
