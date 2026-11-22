package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.smart.SmartProgram
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.tap

/**
 * Programmes propres au Monsieur Cuisine Smart (mixer, smoothie, chauffer l'eau, mijoter,
 * sous-vide, fermenter, riz, œufs, prélavage), reproduits d'après l'appli d'origine.
 * L'utilisateur choisit la durée et, quand c'est possible, la température.
 */
class SmartProgramsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Autres programmes")
    override val section = app.toque.ui.Section.PROGRAMS

    private class Item(val p: SmartProgram) {
        var seconds = p.defaultS
        var celsius = p.defaultC
        lateinit var duration: TextView
        var temperature: TextView? = null
        lateinit var card: LinearLayout
        lateinit var start: View
    }

    private val items = SmartProgram.values().map { Item(it) }
    private var selected = 0

    override fun build(): View {
        val list = Ui.vertical(c)
        list.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), Ui.u(20))
        var row: LinearLayout? = null
        for ((i, it) in items.withIndex()) {
            if (i % 3 == 0) {
                row = Ui.horizontal(c, Gravity.TOP)
                list.addView(row, Ui.lp(t = if (i == 0) 0 else 14))
            }
            row!!.addView(card(i, it), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (i % 3 < 2) rightMargin = Ui.u(14) })
        }
        // Rangée incomplète : cases vides pour garder la largeur des cartes.
        val rest = (3 - items.size % 3) % 3
        repeat(rest) { k -> row!!.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { if (k < rest - 1) rightMargin = Ui.u(14) }) }
        refresh()
        return Ui.scroll(c, list)
    }

    private fun card(i: Int, item: Item): LinearLayout {
        val p = item.p
        val card = Ui.vertical(c)
        card.setPadding(Ui.u(20), Ui.u(18), Ui.u(20), Ui.u(18))
        item.card = card
        Ui.lift(card)
        val head = Ui.horizontal(c)
        head.addView(Ui.badge(c, if (p.heated) Icon.Kind.THERMO else Icon.Kind.BLADE, Ui.ACCENT, Ui.ACCENT_SOFT, 46),
            LinearLayout.LayoutParams(Ui.u(46), Ui.u(46)).apply { rightMargin = Ui.u(12) })
        head.addView(Ui.ellipsize(Ui.title(c, p.label, 23f)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(head)
        card.addView(Ui.text(c, p.description, 14.5f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))

        item.duration = stepper(card, Icon.Kind.CLOCK, p.adjustableDuration) { d -> selected = i; bumpDuration(item, d) }
        if (p.heated) item.temperature = stepper(card, Icon.Kind.THERMO, p.adjustableTemperature) { d -> selected = i; bumpTemperature(item, d) }

        item.start = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 54, textSize = 18f) { selected = i; start(item) }
        card.addView(item.start, Ui.lp(t = 12))
        card.isClickable = true
        card.tap { selected = i; refresh() }
        return card
    }

    /** Ligne « − valeur + » ; sans boutons quand la valeur est fixe. */
    private fun stepper(card: LinearLayout, icon: Icon.Kind, adjustable: Boolean, bump: (Int) -> Unit): TextView {
        val line = Ui.horizontal(c)
        line.addView(Icon(c, icon, Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)).apply { rightMargin = Ui.u(8) })
        if (adjustable) line.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 44) { bump(-1) })
        val value = Ui.text(c, "", 25f, Ui.TEXT, Ui.LIGHT).apply { gravity = Gravity.CENTER }
        line.addView(value, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (adjustable) line.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 44) { bump(1) })
        card.addView(line, Ui.lp(t = 10))
        return value
    }

    private fun bumpDuration(it: Item, dir: Int) {
        val p = it.p
        if (!p.adjustableDuration) return
        // Pas plus fin au début de la plage (une minute), puis le pas du programme.
        val step = if (p.stepS >= 300 && it.seconds + dir * 60 < 600) 60 else p.stepS.coerceAtLeast(1)
        it.seconds = (it.seconds + dir * step).coerceIn(p.minS, p.maxS)
        refresh()
    }

    private fun bumpTemperature(it: Item, dir: Int) {
        val p = it.p
        if (!p.adjustableTemperature) return
        it.celsius = p.clampTemperature(it.celsius + dir * p.stepC)
        refresh()
    }

    private fun start(it: Item) {
        a.startJob(it.p.job(it.seconds * 1000L, it.celsius))
    }

    private fun refresh() {
        for ((i, it) in items.withIndex()) {
            it.duration.text = formatDuration(it.seconds)
            it.temperature?.text = tr("%d °C", it.celsius)
            it.card.background = Ui.round(Ui.SURFACE, 24f, if (i == selected) Ui.ACCENT else Ui.SURFACE, 2.5f)
        }
    }

    override fun onSnapshot(s: Snapshot) {
        for (it in items) Ui.setButtonEnabled(it.start, !s.busy)
    }

    override fun onKnob(delta: Int): Boolean {
        bumpDuration(items[selected], delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        if (!a.snapshot.busy) start(items[selected])
        return true
    }

    private fun formatDuration(s: Int): String = when {
        s < 60 -> "$s s"
        s < 3600 -> if (s % 60 == 0) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        s % 3600 == 0 -> "${s / 3600} h"
        else -> "${s / 3600} h ${(s % 3600) / 60}"
    }
}
