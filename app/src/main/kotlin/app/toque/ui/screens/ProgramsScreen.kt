package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.R
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.Programs
import app.toque.core.machine.Snapshot
import app.toque.core.smart.SmartProgram
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap

/**
 * Programmes du design v2 : les 16 programmes du Smart en grille 4 × 4 (icône au trait, sans
 * pastille), et à droite le programme choisi avec le rendu du bol, ses réglages et le bouton.
 * Chaque programme reprend une commande moteur existante de Camybrith (vérifiée sur le robot) ;
 * la trancheuse-râpe, non encore décrite, reste désactivée.
 */
class ProgramsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Programmes")
    override val section = Section.PROGRAMS

    private sealed class Action {
        class Open(val section: Section) : Action()
        object Manual : Action()
        class Classic(val make: (Long) -> JobSpec, val minS: Int, val maxS: Int, val stepS: Int) : Action()
        class Smart(val p: SmartProgram) : Action()
        object Turbo : Action()
        object Soon : Action()
    }

    private inner class Prog(val icon: Int, val name: String, val bowl: String, val text: String, val cta: String, val action: Action, var seconds: Int = 0) {
        var celsius = (action as? Action.Smart)?.p?.defaultC ?: 0
        lateinit var tile: LinearLayout
        lateinit var iconView: ImageView
        lateinit var label: TextView
    }

    private val progs: List<Prog> by lazy {
        val smart = SmartProgram.available
        fun sp(p: SmartProgram, icon: Int, bowl: String, cta: String) =
            if (smart) Prog(icon, p.label, bowl, p.description, cta, Action.Smart(p), p.defaultS)
            else Prog(icon, p.label, bowl, tr("Réservé au Monsieur Cuisine Smart."), cta, Action.Soon)
        listOf(
            Prog(R.drawable.prog_balance, tr("Balance"), "vide", tr("Peser directement dans le bol, ingrédient par ingrédient, jusqu'à 5 kg."), tr("Ouvrir la balance"), Action.Open(Section.SCALE)),
            Prog(R.drawable.prog_malaxer, tr("Malaxer"), "petrir", tr("Pâtes à pain, pizza, brioche. Alterne sens inverse et normal à vitesse 4, sans chauffe."), tr("Malaxer"),
                Action.Classic({ Programs.knead(it) }, 30, 120, 5), 45),
            Prog(R.drawable.prog_perso, tr("Cuisson personnalisée"), "couvercle", tr("Réglez vous-même la durée, la température, la vitesse et le sens."), tr("Ouvrir la cuisson manuelle"), Action.Manual),
            Prog(R.drawable.prog_saisir, tr("Saisir"), "rissoler", tr("Dorer oignons, lardons ou viande à 130 °C, avec un brassage doux en sens inverse."), tr("Saisir"),
                Action.Classic({ Programs.toast(it) }, 180, 840, 30), 420),
            Prog(R.drawable.prog_vapeur, tr("Cuisson vapeur"), "vapeur",
                if (smart) tr("Au moins 500 g d'eau dans le bol. 120 °C, brassage doux à vitesse 1 comme l'appli du Smart.")
                else tr("Au moins 500 g d'eau dans le bol. 120 °C, la lame reste immobile."), tr("Lancer la vapeur"),
                Action.Classic({ Programs.steam(it) }, 300, 3600, 60), 1200),
            sp(SmartProgram.EGGS, R.drawable.prog_oeufs, "oeufs", tr("Cuire les œufs")),
            sp(SmartProgram.SOUS_VIDE, R.drawable.prog_sousvide, "sousvide", tr("Lancer le sous-vide")),
            sp(SmartProgram.SLOW_COOK, R.drawable.prog_mijoter, "sans-gobelet", tr("Mijoter")),
            sp(SmartProgram.FERMENT, R.drawable.prog_fermenter, "fermentation", tr("Lancer la fermentation")),
            Prog(R.drawable.prog_turbo, tr("Turbo"), "vide", tr("Impulsions à vitesse maximale pour hacher, sans chauffe, 3 s maximum par appui. Interdit si le contenu est chaud."),
                tr("Maintenir pour Turbo"), Action.Turbo),
            sp(SmartProgram.PUREE, R.drawable.prog_mixer, "chauffe", tr("Mixer")),
            sp(SmartProgram.SMOOTHIE, R.drawable.prog_smoothie, "smoothie", tr("Mixer")),
            sp(SmartProgram.RICE, R.drawable.prog_riz, "eau", tr("Cuire le riz")),
            sp(SmartProgram.KETTLE, R.drawable.prog_eau, "eau", tr("Chauffer")),
            sp(SmartProgram.CLEANING, R.drawable.prog_prelaver, "nettoyage", tr("Lancer le prélavage")),
            Prog(R.drawable.prog_trancheuse, tr("Trancheuse-râpe"), "vide", tr("Trancher ou râper avec l'accessoire trancheuse-râpe. Bientôt disponible : sa commande moteur reste à vérifier."),
                tr("Bientôt disponible"), Action.Soon),
        )
    }

    private var selected = 14 // Prélaver, comme la maquette
    private lateinit var bowl: ImageView
    private lateinit var name: TextView
    private lateinit var text: TextView
    private lateinit var settings: LinearLayout
    private lateinit var ctaBox: LinearLayout
    private var start: View? = null
    private var turbo: HoldButton? = null
    private var durationView: TextView? = null
    private var tempView: TextView? = null
    private var waterView: TextView? = null

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(19), Ui.u(2), Ui.u(19), Ui.u(4))

        // Grille 4 × 4
        val grid = Ui.vertical(c)
        for (r in 0 until 4) {
            val row = Ui.horizontal(c)
            for (k in 0 until 4) {
                val i = r * 4 + k
                row.addView(tile(i), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (k < 3) rightMargin = Ui.u(8) })
            }
            grid.addView(row, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { if (r > 0) topMargin = Ui.u(8) })
        }
        root.addView(grid, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(11) })

        // Détail du programme choisi
        val side = Ui.vertical(c)
        side.setPadding(Ui.u(19), Ui.u(10), Ui.u(19), Ui.u(19))
        side.background = Ui.round(Ui.SURFACE, 22f)
        bowl = Ui.bowl(c, "vide")
        side.addView(bowl, Ui.lp(Ui.MATCH, Ui.u(138)))
        name = Ui.text(c, "", 21f, Ui.TEXT, Ui.HEADING).apply { letterSpacing = -0.02f }
        side.addView(name)
        text = Ui.text(c, "", 11.5f, Ui.TEXT2, Ui.REGULAR).apply { setLineSpacing(0f, 1.3f) }
        side.addView(text, Ui.lp(t = 6))
        settings = Ui.vertical(c)
        side.addView(settings, Ui.lp(t = 10))
        side.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        ctaBox = Ui.vertical(c)
        side.addView(ctaBox)
        root.addView(side, LinearLayout.LayoutParams(Ui.u(304), Ui.MATCH))
        select(selected)
        return root
    }

    private fun tile(i: Int): LinearLayout {
        val p = progs[i]
        val t = Ui.vertical(c)
        t.setPadding(Ui.u(13), Ui.u(11), Ui.u(11), Ui.u(11))
        p.iconView = Ui.vector(c, p.icon, Ui.TEXT)
        t.addView(p.iconView, LinearLayout.LayoutParams(Ui.u(26), Ui.u(26)))
        t.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        p.label = Ui.ellipsize(Ui.text(c, p.name, 13f, Ui.TEXT, Ui.HEADING).apply { setLineSpacing(0f, 1.05f) }, 2)
        t.addView(p.label)
        p.tile = t
        t.isClickable = true
        t.contentDescription = p.name
        t.tap { select(i) }
        return t
    }

    /** Ligne de réglage « libellé  −  valeur  + » sur fond crème, comme les étapes de la maquette. */
    private fun setting(label: String, adjustable: Boolean, bump: (Int) -> Unit): TextView {
        val line = Ui.horizontal(c)
        line.setPadding(Ui.u(12), Ui.u(6), Ui.u(6), Ui.u(6))
        line.background = Ui.round(Ui.BG, 13f)
        line.addView(Ui.text(c, label, 11.5f, Ui.TEXT, Ui.MEDIUM), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (adjustable) line.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 34) { bump(-1) })
        val v = Ui.text(c, "", 13f, Ui.ACCENT, Ui.HEADING).apply { gravity = Gravity.CENTER; minWidth = Ui.u(74) }
        line.addView(v)
        if (adjustable) line.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 34) { bump(1) })
        settings.addView(line, Ui.lp(t = if (settings.childCount > 0) 7 else 0))
        return v
    }

    private fun select(i: Int) {
        selected = i
        for ((k, p) in progs.withIndex()) {
            val on = k == i
            p.tile.background = Ui.pressable(Ui.round(if (on) Ui.INK else Ui.SURFACE, 18f), 18f)
            Ui.tintVector(p.iconView, if (on) Ui.ON_INK else Ui.TEXT)
            p.label.setTextColor(if (on) Ui.ON_INK else Ui.TEXT)
        }
        val p = progs[i]
        bowl.setImageBitmap(Ui.bowlBitmap(c, p.bowl))
        name.text = p.name
        text.text = p.text
        settings.removeAllViews()
        durationView = null; tempView = null; waterView = null
        when (val act = p.action) {
            is Action.Classic -> {
                durationView = setting(tr("Durée"), true) { d -> p.seconds = (p.seconds + d * act.stepS).coerceIn(act.minS, act.maxS); refreshValues() }
                if (p.bowl == "vapeur") waterView = Ui.text(c, "", 11.5f, Ui.COOL, Ui.MEDIUM).also { settings.addView(it, Ui.lp(t = 8)) }
            }
            is Action.Smart -> {
                val sp = act.p
                durationView = setting(tr("Durée"), sp.adjustableDuration) { d -> bumpSmart(p, sp, d) }
                if (sp.heated) tempView = setting(tr("Température"), sp.adjustableTemperature) { d ->
                    p.celsius = sp.clampTemperature(p.celsius + d * sp.stepC); refreshValues()
                }
            }
            else -> {}
        }
        ctaBox.removeAllViews()
        start = null; turbo = null
        when (p.action) {
            is Action.Turbo -> {
                val h = HoldButton(c, p.cta, onDown = { a.machine.turboDown(Accessory.BLADE) { v -> if (!v.ok) a.refusal(v) } }, onUp = { a.machine.turboUp() })
                turbo = h
                ctaBox.addView(h, Ui.lp(Ui.MATCH, Ui.u(54)))
            }
            is Action.Soon -> {
                val b = Ui.button(c, p.cta, Ui.Style.SECONDARY, height = 54, textSize = 15f) {}
                Ui.setButtonEnabled(b, false)
                ctaBox.addView(b, Ui.lp(Ui.MATCH, Ui.WRAP))
            }
            else -> {
                val b = Ui.button(c, p.cta, Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 54, textSize = 15f) { run(p) }
                start = b
                ctaBox.addView(b, Ui.lp(Ui.MATCH, Ui.WRAP))
            }
        }
        refreshValues()
        onSnapshot(a.snapshot)
    }

    private fun bumpSmart(p: Prog, sp: SmartProgram, dir: Int) {
        if (!sp.adjustableDuration) return
        val step = if (sp.stepS >= 300 && p.seconds + dir * 60 < 600) 60 else sp.stepS.coerceAtLeast(1)
        p.seconds = (p.seconds + dir * step).coerceIn(sp.minS, sp.maxS)
        refreshValues()
    }

    private fun refreshValues() {
        val p = progs[selected]
        durationView?.text = formatDuration(p.seconds)
        tempView?.text = tr("%d °C", p.celsius)
    }

    private fun run(p: Prog) {
        when (val act = p.action) {
            is Action.Open -> a.open(act.section)
            is Action.Manual -> a.open(Section.MANUAL)
            is Action.Classic -> a.startJob(act.make(p.seconds * 1000L))
            is Action.Smart -> a.startJob(act.p.job(p.seconds * 1000L, p.celsius))
            else -> {}
        }
    }

    override fun onSnapshot(s: Snapshot) {
        val p = progs.getOrNull(selected) ?: return
        val needsMotor = p.action is Action.Classic || p.action is Action.Smart
        start?.let { Ui.setButtonEnabled(it, !needsMotor || !s.busy) }
        turbo?.let { it.isEnabled = !s.busy || s.turboActive; it.alpha = if (it.isEnabled) 1f else 0.35f }
        waterView?.text = when {
            !s.hasSample -> tr("Eau : balance non connectée")
            s.weightG >= 500 -> tr("Eau : %d g, c'est assez", s.weightG)
            else -> tr("Eau : %d g sur 500 g minimum", maxOf(0, s.weightG))
        }
    }

    override fun onKnob(delta: Int): Boolean {
        val p = progs[selected]
        when (val act = p.action) {
            is Action.Classic -> { p.seconds = (p.seconds + delta * act.stepS).coerceIn(act.minS, act.maxS); refreshValues() }
            is Action.Smart -> bumpSmart(p, act.p, delta)
            else -> select((selected + delta).mod(progs.size))
        }
        return true
    }

    override fun onKnobPress(): Boolean {
        if (!a.snapshot.busy) run(progs[selected])
        return true
    }

    private fun formatDuration(s: Int): String = when {
        s < 60 -> "$s s"
        s < 3600 -> if (s % 60 == 0) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        s % 3600 == 0 -> "${s / 3600} h"
        else -> "${s / 3600} h ${(s % 3600) / 60}"
    }
}
