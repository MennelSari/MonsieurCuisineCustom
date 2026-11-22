package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.IngredientInfo
import app.toque.core.recipes.Ingredients
import app.toque.core.recipes.WeighState
import app.toque.core.recipes.Weighing
import app.toque.ui.Icon
import app.toque.ui.LiquidGauge
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap

/**
 * Balance : le poids en grand et le verre doseur. On peut viser un poids (la jauge passe au rouge
 * au delà) et choisir l'ingrédient, qui donne sa couleur au liquide et l'équivalent en ml.
 */
class ScaleScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Balance")
    override val section = Section.SCALE
    private lateinit var value: TextView
    private lateinit var state: LinearLayout
    private lateinit var volume: TextView
    private lateinit var gauge: LiquidGauge
    private lateinit var targets: LinearLayout
    private lateinit var kinds: LinearLayout
    private lateinit var targetValue: TextView
    private var simulated = 0
    private var target = 0
    private var kind = 0
    private var lastState: WeighState? = null

    private val choices: List<Pair<String, IngredientInfo?>> by lazy {
        listOf(
            tr("Autre") to null,
            tr("Eau") to Ingredients.lookup("eau"),
            tr("Lait") to Ingredients.lookup("lait"),
            tr("Farine") to Ingredients.lookup("farine"),
            tr("Sucre") to Ingredients.lookup("sucre"),
            tr("Huile") to Ingredients.lookup("huile"),
        )
    }

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), Ui.u(16))
        val left = Ui.vertical(c)
        val row = Ui.horizontal(c, Gravity.BOTTOM)
        value = Ui.text(c, "—", 150f, Ui.TEXT, Ui.THIN).apply { includeFontPadding = false }
        row.addView(value)
        row.addView(Ui.text(c, "g", 50f, Ui.TEXT2, Ui.THIN), Ui.lp(Ui.WRAP, Ui.WRAP, l = 10, b = 16))
        left.addView(row)
        val info = Ui.horizontal(c)
        state = Ui.chip(c, "", Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2, 16f)
        info.addView(state, Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        volume = Ui.text(c, "", 16f, Ui.TEXT3)
        info.addView(volume)
        left.addView(info, Ui.lp(t = 8))
        left.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))

        val tHead = Ui.horizontal(c)
        tHead.addView(Ui.label(c, tr("Poids à atteindre"), Ui.TEXT2, 12f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        left.addView(tHead)
        val tRow = Ui.horizontal(c)
        targets = Ui.horizontal(c)
        tRow.addView(targets, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        tRow.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 44) { bumpTarget(-1) })
        targetValue = Ui.text(c, "", 18f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        tRow.addView(targetValue, LinearLayout.LayoutParams(Ui.u(84), Ui.WRAP))
        tRow.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 44) { bumpTarget(1) })
        left.addView(tRow, Ui.lp(t = 8))
        left.addView(Ui.label(c, tr("Ingrédient"), Ui.TEXT2, 12f), Ui.lp(t = 16))
        kinds = Ui.horizontal(c)
        left.addView(android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(kinds) }, Ui.lp(t = 8))

        val buttons = Ui.horizontal(c)
        buttons.addView(Ui.button(c, tr("Tare (remise à zéro)"), Ui.Style.PRIMARY, Icon.Kind.SCALE, height = 60, textSize = 19f) {
            a.machine.tare()
            lastState = null
        })
        if (a.prefs.mode == Prefs.Mode.DEMO) {
            buttons.addView(Ui.spacer(c, w = 18))
            for (g in intArrayOf(25, 100)) {
                buttons.addView(Ui.button(c, "+$g g", Ui.Style.GHOST, height = 50, textSize = 16f) {
                    simulated += g
                    a.machine.simulate(weight = simulated)
                }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
            }
            buttons.addView(Ui.button(c, tr("Vider"), Ui.Style.GHOST, height = 50, textSize = 16f) {
                simulated = 0
                a.machine.simulate(weight = 0)
            })
        }
        left.addView(buttons, Ui.lp(t = 18))
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(18) })

        val card = FrameLayout(c)
        card.background = Ui.round(Ui.SURFACE, 24f)
        Ui.lift(card)
        card.setPadding(Ui.u(22), Ui.u(20), Ui.u(12), Ui.u(20))
        gauge = LiquidGauge(c)
        card.addView(gauge, Ui.flp(Ui.MATCH, Ui.MATCH))
        root.addView(card, LinearLayout.LayoutParams(Ui.u(300), Ui.MATCH))
        renderChoices()
        return root
    }

    /** Boutons : 10 g par appui jusqu'à 1 kg, 50 g au-delà. */
    private fun bumpTarget(dir: Int) {
        val step = if (target < 1000 || (target == 1000 && dir < 0)) 10 else 50
        setTarget(target + dir * step)
    }

    private fun setTarget(g: Int) {
        target = g.coerceIn(0, 5000)
        lastState = null
        renderChoices()
    }

    /** Molette : 1 g par cran jusqu'à 100 g, 5 g jusqu'à 1 kg, 10 g au-delà (120 g en deux tours). */
    override fun onKnob(delta: Int): Boolean {
        val step = when {
            target < 100 || (target == 100 && delta < 0) -> 1
            target < 1000 || (target == 1000 && delta < 0) -> 5
            else -> 10
        }
        setTarget(target + delta * step)
        return true
    }

    private fun renderChoices() {
        targets.removeAllViews()
        for (g in intArrayOf(0, 100, 250, 500, 1000)) {
            val label = when (g) { 0 -> tr("Sans"); 1000 -> "1 kg"; else -> "$g g" }
            targets.addView(Ui.toggleChip(c, label, target == g) { target = g; lastState = null; renderChoices() }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 7))
        }
        targetValue.text = if (target == 0) "—" else "$target g"
        kinds.removeAllViews()
        for ((i, ch) in choices.withIndex()) {
            val on = i == kind
            val chip = Ui.horizontal(c)
            chip.setPadding(Ui.u(12), 0, Ui.u(15), 0)
            chip.minimumHeight = Ui.u(40)
            chip.background = Ui.pressable(if (on) Ui.round(Ui.TEXT, 20f) else Ui.round(Ui.SURFACE, 20f, Ui.STROKE, 1.2f), 20f)
            val col = ch.second?.color ?: Ui.COOL
            chip.addView(View(c).apply { background = Ui.round(col, 7f, Ui.shade(col, 0.72f), 1.2f) },
                LinearLayout.LayoutParams(Ui.u(14), Ui.u(14)).apply { rightMargin = Ui.u(7) })
            chip.addView(Ui.text(c, ch.first, 15f, if (on) Ui.BG else Ui.TEXT, Ui.MEDIUM))
            chip.isClickable = true
            chip.tap { kind = i; renderChoices() }
            kinds.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP, r = 7))
        }
        gauge.fill = choices[kind].second?.color ?: Ui.COOL
        gauge.targetGrams = target
        gauge.targetLabel = "$target g"
        gauge.halfLabel = "${target / 2} g"
        onSnapshot(a.snapshot)
    }

    override fun onSnapshot(s: Snapshot) {
        if (!::value.isInitialized) return
        if (!s.hasSample) {
            value.text = "—"
            setState(tr("Base non connectée"), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
            volume.text = ""
            return
        }
        value.text = s.weightG.toString()
        gauge.setWeight(s.weightG)
        val info = choices[kind].second
        volume.text = if (info != null && info.liquid && s.weightG > 0) tr("≈ %d ml", Math.round(s.weightG / info.density).toInt()) else ""
        if (s.scaleState == "overload") {
            setState(tr("Surcharge"), Icon.Kind.WARNING, Ui.DANGER, Ui.DANGER_SOFT)
            return
        }
        if (target > 0) {
            val st = Weighing.state(target, s.weightG)
            val d = kotlin.math.abs(target - s.weightG)
            when (st) {
                WeighState.TODO -> setState(tr("Encore %d g", d), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
                WeighState.NEAR -> setState(tr("Presque : %d g", d), Icon.Kind.SCALE, Ui.WARN, Ui.WARN_SOFT)
                WeighState.OK -> setState(tr("C'est bon"), Icon.Kind.CHECK, Ui.OK, Ui.OK_SOFT)
                WeighState.OVER -> setState(tr("Trop : %d g", d), Icon.Kind.MINUS, Ui.DANGER, Ui.DANGER_SOFT)
            }
            value.setTextColor(if (st == WeighState.OVER) Ui.DANGER else Ui.TEXT)
            if (st != lastState) {
                if (lastState != null && st == WeighState.OK) a.beep(true)
                if (lastState != null && st == WeighState.OVER) a.beep(false)
                lastState = st
            }
            return
        }
        value.setTextColor(Ui.TEXT)
        if (s.scaleStable) setState(tr("Stable"), Icon.Kind.CHECK, Ui.OK, Ui.OK_SOFT)
        else setState(tr("Mesure…"), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
    }

    private fun setState(text: String, icon: Icon.Kind, color: Int, bg: Int) {
        Ui.chipText(state).text = text
        Ui.chipText(state).setTextColor(color)
        Ui.chipIcon(state)?.apply { set(icon); this.color = color }
        state.background = Ui.round(bg, 30f)
    }

    override fun onKnobPress(): Boolean {
        a.machine.tare()
        lastState = null
        return true
    }
}
