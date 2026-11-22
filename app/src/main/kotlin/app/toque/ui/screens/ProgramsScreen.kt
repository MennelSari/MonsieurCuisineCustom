package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.Programs
import app.toque.core.machine.Snapshot
import app.toque.core.smart.SmartProgram
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.ProgressLine
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap

/** Programmes automatiques de l'appli d'origine : pétrir, vapeur, rissoler (+ Turbo). */
class ProgramsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Programmes")
    override val section = Section.PROGRAMS
    private lateinit var water: ProgressLine
    private lateinit var waterText: TextView

    private class Prog(
        val icon: Icon.Kind, val name: String, val text: String,
        var seconds: Int, val min: Int, val max: Int, val step: Int,
        val make: (Long) -> JobSpec,
    ) {
        lateinit var value: TextView
        lateinit var card: LinearLayout
        lateinit var start: View
    }

    private val progs = listOf(
        Prog(Icon.Kind.KNEAD, JobKind.KNEAD.label, tr("Pâtes à pain, pizza, brioche. Alterne sens inverse et normal à vitesse 4."),
            45, 30, 120, 5) { Programs.knead(it) },
        Prog(Icon.Kind.STEAM, JobKind.STEAM.label,
            if (SmartProgram.available) tr("Mettez au moins 500 g d'eau dans le bol. 120 °C, brassage doux à vitesse 1 comme l'appli du Smart.")
            else tr("Mettez au moins 500 g d'eau dans le bol. 120 °C, la lame reste immobile."),
            1200, 300, 3600, 60) { Programs.steam(it) },
        Prog(Icon.Kind.FLAME, JobKind.TOAST.label, tr("Dorer oignons, lardons, viandes à 130 °C, avec un brassage doux en sens inverse."),
            420, 180, 840, 30) { Programs.toast(it) },
    )
    private var selected = 0
    private lateinit var turbo: HoldButton

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), Ui.u(16))
        val row = Ui.horizontal(c, Gravity.TOP)
        for ((i, p) in progs.withIndex()) {
            val card = Ui.vertical(c)
            card.setPadding(Ui.u(20), Ui.u(18), Ui.u(20), Ui.u(18))
            Ui.lift(card)
            p.card = card
            card.addView(Ui.badge(c, p.icon, Ui.ACCENT, Ui.ACCENT_SOFT, 52))
            card.addView(Ui.title(c, p.name, 26f), Ui.lp(t = 12))
            card.addView(Ui.text(c, p.text, 14.5f, Ui.TEXT2).apply { setLineSpacing(0f, 1.18f) }, Ui.lp(t = 4))
            if (p.icon == Icon.Kind.STEAM) {
                // Eau dans le bol : au moins 500 g pour la vapeur.
                val w = Ui.vertical(c)
                w.background = Ui.round(Ui.COOL_SOFT, 16f)
                w.setPadding(Ui.u(12), Ui.u(10), Ui.u(12), Ui.u(11))
                waterText = Ui.text(c, "", 13.5f, Ui.COOL, Ui.MEDIUM)
                w.addView(waterText)
                water = ProgressLine(c).apply { color = 0xFF5DA9DD.toInt(); track = Ui.SURFACE }
                w.addView(water, Ui.lp(Ui.MATCH, Ui.u(8), t = 7))
                card.addView(w, Ui.lp(t = 10))
            }
            card.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
            val dur = Ui.horizontal(c)
            dur.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 50) { selected = i; bump(p, -1) })
            p.value = Ui.text(c, "", 30f, Ui.TEXT, Ui.LIGHT).apply { gravity = Gravity.CENTER }
            dur.addView(p.value, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            dur.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 50) { selected = i; bump(p, 1) })
            card.addView(dur, Ui.lp(t = 10))
            p.start = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 58, textSize = 19f) {
                selected = i
                a.startJob(p.make(p.seconds * 1000L))
            }
            card.addView(p.start, Ui.lp(t = 12))
            card.isClickable = true
            card.tap { selected = i; refresh() }
            row.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < progs.size - 1) rightMargin = Ui.u(14) })
        }
        root.addView(row, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val bottom = Ui.horizontal(c)
        if (SmartProgram.available) {
            bottom.addView(Ui.button(c, tr("Autres programmes"), Ui.Style.SECONDARY, Icon.Kind.CHEVRON, height = 52, textSize = 17f, iconAfter = true) {
                a.push(SmartProgramsScreen(a))
            }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 18))
        }
        bottom.addView(Icon(c, Icon.Kind.BOLT, Ui.WARN), LinearLayout.LayoutParams(Ui.u(20), Ui.u(20)).apply { rightMargin = Ui.u(10) })
        bottom.addView(Ui.text(c, tr("Turbo : impulsions à vitesse maximale, sans chauffe, 3 s maximum par appui."), 15f, Ui.TEXT2),
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        turbo = HoldButton(c, tr("Maintenir pour Turbo"), onDown = {
            a.machine.turboDown(app.toque.core.machine.Accessory.BLADE) { v -> if (!v.ok) a.refusal(v) }
        }, onUp = { a.machine.turboUp() })
        bottom.addView(turbo, Ui.lp(Ui.WRAP, Ui.u(52)))
        root.addView(bottom, Ui.lp(t = 14))
        refresh()
        return root
    }

    private fun bump(p: Prog, dir: Int) {
        p.seconds = (p.seconds + dir * p.step).coerceIn(p.min, p.max)
        refresh()
    }

    private fun refresh() {
        for ((i, p) in progs.withIndex()) {
            p.value.text = Ui.duration(p.seconds)
            p.card.background = Ui.round(Ui.SURFACE, 24f, if (i == selected) Ui.ACCENT else Ui.SURFACE, 2.5f)
        }
    }

    override fun onSnapshot(s: Snapshot) {
        for (p in progs) Ui.setButtonEnabled(p.start, !s.busy)
        if (::water.isInitialized) {
            water.progress = if (s.hasSample) s.weightG / 500f else 0f
            waterText.text = when {
                !s.hasSample -> tr("Eau : balance non connectée")
                s.weightG >= 500 -> tr("Eau : %d g, c'est assez", s.weightG)
                else -> tr("Eau : %d g sur 500 g minimum", maxOf(0, s.weightG))
            }
        }
        turbo.isEnabled = !s.busy || s.turboActive
        turbo.alpha = if (turbo.isEnabled) 1f else 0.35f
    }

    override fun onKnob(delta: Int): Boolean {
        bump(progs[selected], delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        if (!a.snapshot.busy) a.startJob(progs[selected].make(progs[selected].seconds * 1000L))
        return true
    }
}
