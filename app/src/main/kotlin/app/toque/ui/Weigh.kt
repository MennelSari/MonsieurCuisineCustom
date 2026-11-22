package app.toque.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.QtyUnit
import app.toque.core.recipes.WeighState
import app.toque.core.recipes.WeighTarget
import app.toque.core.recipes.Weighing

/**
 * Une pesée en cours : le zéro est pris tout seul au premier poids reçu (sans commande de tare
 * à la base), puis la jauge se remplit. [info] montre l'ingrédient, le poids en grand et l'état ;
 * [gauge] est le verre doseur, à poser dans une carte.
 */
class WeighSession(private val a: MainActivity, target: WeighTarget) {
    var target: WeighTarget = target
        private set
    private var baseline: Int? = null
    private var lastState: WeighState? = null
    private var net = 0
    /** Appelé à chaque changement d'état (encore, presque, c'est bon, trop). */
    var onState: (WeighState) -> Unit = {}

    val gauge = LiquidGauge(a)
    val info = Ui.vertical(a)
    private val swatch = View(a)
    private val nameView = Ui.ellipsize(Ui.text(a, "", 17f, Ui.TEXT2))
    private val reading = Ui.text(a, "", 104f, Ui.TEXT, Ui.LIGHT)
    private val unitView = Ui.text(a, "", 36f, Ui.TEXT2, Ui.LIGHT)
    private val ofView = Ui.text(a, "", 21f, Ui.TEXT3)
    private val note = Ui.text(a, "", 14.5f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }
    private val stateChip = Ui.chip(a, "", Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2, 17f)
    val zeroButton: LinearLayout = Ui.button(a, tr("Remettre à zéro"), Ui.Style.GHOST, null, height = 48, textSize = 16f) { zero() }

    init {
        val head = Ui.horizontal(a)
        head.addView(swatch, LinearLayout.LayoutParams(Ui.u(18), Ui.u(18)).apply { rightMargin = Ui.u(10) })
        head.addView(nameView, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        info.addView(head)
        val row = Ui.horizontal(a, Gravity.BOTTOM)
        row.addView(reading)
        row.addView(unitView, Ui.lp(Ui.WRAP, Ui.WRAP, l = 8, b = 8))
        row.addView(ofView, Ui.lp(Ui.WRAP, Ui.WRAP, l = 12, b = 13))
        info.addView(row, Ui.lp(t = 4))
        info.addView(note, Ui.lp(t = 6))
        val actions = Ui.horizontal(a)
        actions.addView(stateChip, Ui.lp(Ui.WRAP, Ui.WRAP))
        actions.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(zeroButton)
        info.addView(actions, Ui.lp(t = 16))
        stateChip.minimumHeight = Ui.u(42)
        stateChip.setPadding(Ui.u(14), 0, Ui.u(18), 0)
        apply(target)
    }

    /** Change de cible (pesées en série) : nouveau zéro au prochain poids reçu. */
    fun apply(t: WeighTarget) {
        target = t
        baseline = null
        lastState = null
        net = 0
        val col = t.info?.color ?: Ui.COOL
        gauge.fill = col
        gauge.targetGrams = t.grams
        gauge.targetLabel = format(t.amount, t.unit) + " " + unitLabel(t.unit)
        gauge.halfLabel = format(t.amount / 2, t.unit) + " " + unitLabel(t.unit)
        gauge.setWeight(0, instant = true)
        swatch.background = Ui.round(col, 9f, Ui.shade(col, 0.72f), 1.5f)
        nameView.text = t.label.replaceFirstChar { it.uppercaseChar() }.ifEmpty { tr("Ingrédient") }
        unitView.text = unitLabel(t.unit)
        ofView.text = tr("sur %s", format(t.amount, t.unit) + " " + unitLabel(t.unit))
        note.text = if (t.unit.volume) tr("La balance pèse en grammes : %s font environ %d g. L'appli convertit pour vous.",
            format(t.amount, t.unit) + " " + unitLabel(t.unit) + if (t.label.isNotEmpty()) " " + tr("de %s", t.label) else "", t.grams)
        else tr("Le zéro est fait tout seul en arrivant sur cette pesée.")
        reading.text = "0"
        update(a.snapshot)
    }

    fun zero() {
        val s = a.snapshot
        if (!s.hasSample) return
        baseline = s.weightG
        lastState = null
        update(s)
    }

    fun update(s: Snapshot) {
        if (!s.hasSample) {
            reading.text = "—"
            setChip(tr("Balance non connectée"), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
            return
        }
        if (s.busy) {
            // Sur le Smart, la base fige le poids tant que le moteur tourne : ni zéro ni mesure.
            setChip(tr("Balance figée pendant la cuisson"), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
            return
        }
        if (baseline == null) baseline = s.weightG
        net = s.weightG - (baseline ?: 0)
        gauge.setWeight(net)
        val shown = target.display(maxOf(0, net))
        reading.text = format(shown, target.unit)
        val st = Weighing.state(target.grams, net)
        reading.setTextColor(if (st == WeighState.OVER) Ui.DANGER else Ui.TEXT)
        val diff = target.display(kotlin.math.abs(target.grams - net))
        val d = format(diff, target.unit) + " " + unitLabel(target.unit)
        when (st) {
            WeighState.TODO -> setChip(tr("Encore %s", d), Icon.Kind.SCALE, Ui.TEXT2, Ui.SURFACE2)
            WeighState.NEAR -> setChip(tr("Presque : %s", d), Icon.Kind.SCALE, Ui.WARN, Ui.WARN_SOFT)
            WeighState.OK -> setChip(tr("C'est bon"), Icon.Kind.CHECK, Ui.OK, Ui.OK_SOFT)
            WeighState.OVER -> setChip(tr("Trop : %s", d), Icon.Kind.MINUS, Ui.DANGER, Ui.DANGER_SOFT)
        }
        if (st != lastState) {
            val prev = lastState
            lastState = st
            if (prev != null) when (st) {
                WeighState.OK -> a.beep(true)
                WeighState.OVER -> a.beep(false)
                else -> Unit
            }
            onState(st)
        }
    }

    val state: WeighState? get() = lastState

    private fun setChip(text: String, icon: Icon.Kind, color: Int, bg: Int) {
        Ui.chipText(stateChip).text = text
        Ui.chipText(stateChip).setTextColor(color)
        Ui.chipIcon(stateChip)?.apply { set(icon); this.color = color }
        stateChip.background = Ui.round(bg, 30f)
    }

    /** Le verre doseur dans une carte, centré. */
    fun gaugeCard(width: Int = 330): FrameLayout {
        val card = FrameLayout(a)
        card.background = Ui.round(Ui.SURFACE, 24f)
        Ui.lift(card)
        card.setPadding(Ui.u(22), Ui.u(22), Ui.u(14), Ui.u(22))
        card.addView(gauge, Ui.flp(Ui.u(width - 36 - 60), Ui.MATCH, Gravity.CENTER))
        return card
    }

    companion object {
        fun unitLabel(u: QtyUnit): String = u.symbol

        fun format(v: Double, u: QtyUnit): String = when (u) {
            QtyUnit.G, QtyUnit.ML -> String.format(I18n.locale, "%d", Math.round(v))
            QtyUnit.CL -> if (v >= 10 || v == Math.floor(v)) String.format(I18n.locale, "%d", Math.round(v)) else String.format(I18n.locale, "%.1f", v)
            QtyUnit.DL -> String.format(I18n.locale, "%.1f", v)
            QtyUnit.KG, QtyUnit.L -> String.format(I18n.locale, "%.2f", v)
        }
    }
}
