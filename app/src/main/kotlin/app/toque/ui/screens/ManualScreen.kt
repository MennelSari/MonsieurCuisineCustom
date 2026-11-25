package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.Accessory
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobState
import app.toque.core.machine.Limits
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Snapshot
import app.toque.core.protocol.Direction
import app.toque.core.machine.TemperatureTable
import app.toque.ui.HoldButton
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.Wheel
import app.toque.ui.tap
import app.toque.ui.isSmart
import android.widget.ImageView

/**
 * Cuisson manuelle. Les réglages incompatibles sont corrigés à la saisie (vitesse limitée en
 * chauffe, en sens inverse ou avec le fouet) plutôt que refusés au démarrage.
 */
class ManualScreen(a: MainActivity, preset: app.toque.core.recipes.StepMachine? = null) : Screen(a) {
    override val title = JobKind.MANUAL.label
    override val section = Section.MANUAL

    private var seconds = preset?.seconds?.takeIf { it > 0 } ?: 60
    private var tempLevel = preset?.temperatureLevel ?: 0
    private var speed = preset?.speed?.coerceIn(1, 10) ?: 2
    private var reverse = preset?.reverse ?: false
    private var accessory = preset?.accessory ?: Accessory.BLADE
    private var selected = 0

    private lateinit var wMin: Wheel
    private lateinit var wSec: Wheel
    private lateinit var wTemp: Wheel
    private lateinit var wSpeed: Wheel
    private lateinit var accRow: LinearLayout
    private lateinit var revBtn: LinearLayout
    private lateinit var bowl: ImageView
    private lateinit var caption: TextView
    private lateinit var weight: TextView
    private lateinit var startBtn: LinearLayout
    private lateinit var hint: TextView
    private lateinit var turbo: HoldButton
    private var live = false
    private var wasLive = false
    private var pendingEdit = false
    private var timeEdited = false
    private var configuredSeconds = seconds
    private val pushLive = Runnable { sendAdjust() }

    /**
     * Design v2 : quatre molettes (minutes, secondes, température, vitesse) sur une bande centrale,
     * le rendu du bol à droite (ouvert, fermé, en chauffe) avec la balance, et en bas le sens
     * inverse, le Turbo et le grand bouton Démarrer.
     */
    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(19), Ui.u(2), Ui.u(19), Ui.u(4))
        val main = Ui.horizontal(c, Gravity.TOP)

        val card = Ui.vertical(c)
        card.setPadding(Ui.u(19), Ui.u(15), Ui.u(19), Ui.u(15))
        card.background = Ui.round(Ui.SURFACE, 22f)
        val titles = Ui.horizontal(c)
        for ((t, col) in listOf(tr("Minutes") to Ui.TEXT2, tr("Secondes") to Ui.TEXT2, tr("Température") to Ui.ACCENT, tr("Vitesse") to Ui.COOL)) {
            titles.addView(Ui.text(c, t.uppercase(), 10.5f, col, Ui.HEADING).apply { gravity = Gravity.CENTER; letterSpacing = 0.06f }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        card.addView(titles)
        val wheels = android.widget.FrameLayout(c)
        val band = View(c)
        band.background = Ui.round(Ui.BG, 22f)
        wheels.addView(band, Ui.flp(Ui.MATCH, Ui.u(70), Gravity.CENTER_VERTICAL))
        val row = Ui.horizontal(c)
        wMin = Wheel(c, Ui.TEXT); wSec = Wheel(c, Ui.TEXT); wTemp = Wheel(c, Ui.ACCENT); wSpeed = Wheel(c, Ui.COOL)
        wSec.labels = (0 until 12).map { String.format("%02d", it * 5) }
        for (w in listOf(wMin, wSec, wTemp, wSpeed)) row.addView(w, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        wheels.addView(row, Ui.flp())
        card.addView(wheels, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(4) })
        wMin.onChange = { i -> selected = 0; setSeconds(maxOf(5, i * 60 + (seconds % 60))) }
        wSec.onChange = { i -> selected = 0; setSeconds(maxOf(5, (seconds / 60) * 60 + i * 5)) }
        wTemp.onChange = { i -> selected = 1; setTempLevel(tempSteps[i.coerceIn(0, tempSteps.size - 1)]) }
        wSpeed.onChange = { i -> selected = 2; setSpeed(i + 1) }
        hint = Ui.text(c, "", 11.5f, Ui.ACCENT, Ui.HEADING).apply { gravity = Gravity.CENTER }
        hint.setPadding(Ui.u(12), Ui.u(8), Ui.u(12), Ui.u(8))
        card.addView(hint, Ui.lp(Ui.MATCH, Ui.WRAP, t = 6))
        main.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(11) })

        val right = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        right.setPadding(Ui.u(15), Ui.u(12), Ui.u(15), Ui.u(14))
        right.background = Ui.round(Ui.SURFACE, 22f)
        bowl = Ui.bowl(c, "couvercle")
        right.addView(bowl, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        caption = Ui.text(c, "", 12f, Ui.TEXT2, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        right.addView(caption, Ui.lp(Ui.MATCH, Ui.WRAP, t = 2))
        // Balance bien lisible : libellé, poids en grand, bouton Tarer.
        val scale = Ui.horizontal(c)
        scale.setPadding(Ui.u(16), Ui.u(9), Ui.u(9), Ui.u(9))
        scale.background = Ui.round(Ui.BG, 18f)
        val sc = Ui.vertical(c)
        sc.addView(Ui.text(c, tr("Balance").uppercase(), 10f, Ui.TEXT2, Ui.HEADING).apply { letterSpacing = 0.06f })
        weight = Ui.text(c, "", 25f, Ui.TEXT, Ui.HEADING).apply { letterSpacing = -0.02f }
        sc.addView(weight, Ui.lp(t = 1))
        scale.addView(sc, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        scale.addView(Ui.button(c, tr("Tarer"), Ui.Style.PRIMARY, height = 42, textSize = 13f) { a.machine.tare() })
        right.addView(scale, Ui.lp(Ui.MATCH, Ui.WRAP, t = 10))
        accRow = Ui.horizontal(c, Gravity.CENTER)
        right.addView(accRow, Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
        main.addView(right, LinearLayout.LayoutParams(Ui.u(304), Ui.MATCH))
        root.addView(main, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val foot = Ui.horizontal(c)
        revBtn = Ui.button(c, tr("Sens inverse"), Ui.Style.SECONDARY, Icon.Kind.REVERSE, height = 58, textSize = 13f) { setReverse(!reverse) }
        foot.addView(revBtn)
        turbo = HoldButton(c, tr("Maintenir pour Turbo"), onDown = {
            a.machine.turboDown(accessory) { v -> if (!v.ok) a.refusal(v) }
        }, onUp = { a.machine.turboUp() })
        foot.addView(turbo, Ui.lp(Ui.WRAP, Ui.u(58), l = 10))
        startBtn = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 58, textSize = 16f) { start() }
        foot.addView(startBtn, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(10) })
        root.addView(foot, Ui.lp(Ui.MATCH, Ui.WRAP, t = 11))
        refresh()
        return root
    }

    private fun setSeconds(sec: Int) {
        val max = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        seconds = sec.coerceIn(5, max)
        afterChange(0)
    }

    private fun setTempLevel(lv: Int) {
        tempLevel = lv
        if (tempLevel > 0 && speed > Limits.MAX_SPEED_HEATED) {
            speed = Limits.MAX_SPEED_HEATED
            a.toast(tr("En chauffe, vitesse %d maximum", Limits.MAX_SPEED_HEATED))
        }
        afterChange(1)
    }

    private fun speedMax(): Pair<Int, String> {
        var max = 10
        var why = trKey("Vitesse %d maximum")
        if (tempLevel > 0) { max = Limits.MAX_SPEED_HEATED; why = trKey("En chauffe, vitesse %d maximum") }
        if (reverse && max > Limits.MAX_SPEED_REVERSE) { max = Limits.MAX_SPEED_REVERSE; why = trKey("En sens inverse, vitesse %d maximum") }
        if (accessory.maxSpeed < max) { max = accessory.maxSpeed; why = trKey("Avec le fouet, vitesse %d maximum") }
        return max to why
    }

    private fun setSpeed(lv: Int) {
        val (max, why) = speedMax()
        if (lv > max) {
            a.toast(tr(why, max))
            speed = max
        } else speed = lv
        afterChange(2)
    }

    /** Après un réglage : bornes, affichage, et envoi en direct pendant une cuisson manuelle. */
    private fun afterChange(card: Int) {
        val maxS = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        if (seconds > maxS) seconds = maxS
        refresh()
        val s = a.snapshot
        if (live && s.kind?.name == "MANUAL") {
            if (card == 0) timeEdited = true
            pendingEdit = true
            view.removeCallbacks(pushLive)
            view.postDelayed(pushLive, 350)
        }
    }

    private val tempSteps: IntArray get() = intArrayOf(0) + TemperatureTable.current.heatedLevels

    private fun nextSeconds(s: Int, dir: Int): Int {
        val step = when {
            dir > 0 && s < 60 || dir < 0 && s <= 60 -> 5
            dir > 0 && s < 600 || dir < 0 && s <= 600 -> 30
            else -> 60
        }
        val max = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        return (s + dir * step).coerceIn(5, max)
    }

    private fun change(card: Int, dir: Int) {
        when (card) {
            0 -> { seconds = nextSeconds(seconds, dir); afterChange(0) }
            1 -> {
                val i = tempSteps.indexOf(tempLevel).coerceAtLeast(0)
                setTempLevel(tempSteps[(i + dir).coerceIn(0, tempSteps.size - 1)])
            }
            2 -> {
                val (max, why) = speedMax()
                val next = (speed + dir).coerceIn(1, 10)
                if (next > max) a.toast(tr(why, max)) else setSpeed(next)
            }
        }
    }

    private fun setReverse(r: Boolean) {
        if (r && speed > Limits.MAX_SPEED_REVERSE) {
            speed = Limits.MAX_SPEED_REVERSE
            a.toast(tr("En sens inverse, vitesse %d maximum", Limits.MAX_SPEED_REVERSE))
        }
        reverse = r
        refresh()
        if (live) { pendingEdit = true; view.removeCallbacks(pushLive); view.postDelayed(pushLive, 200) }
    }

    private fun setAccessory(acc: Accessory) {
        if (live) return
        accessory = acc
        if (speed > acc.maxSpeed) {
            speed = acc.maxSpeed
            a.toast(tr("%s : vitesse %d maximum", acc.label, acc.maxSpeed))
        }
        refresh()
    }

    private fun spec(hot: Boolean = false) = Programs.manual(
        speed, tempLevel, if (reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE,
        seconds * 1000L, accessory, hot,
    )

    private fun start() {
        if (a.dockJustMoved()) return
        val s = a.snapshot
        if (s.state == JobState.PAUSED && s.kind?.name == "MANUAL") {
            a.machine.resume(s.hotBlendConfirmed) { a.refusal(it) }
            return
        }
        configuredSeconds = seconds
        a.startJob(spec())
    }

    private fun sendAdjust() {
        pendingEdit = false
        val s = a.snapshot
        if (!s.jobActive || s.kind?.name != "MANUAL") return
        // La durée n'est renvoyée que si elle a été modifiée : changer la vitesse ne relance pas le minuteur.
        val total: Long? = if (timeEdited) s.elapsedMs + seconds * 1000L else null
        timeEdited = false
        a.machine.adjust(speed, tempLevel, reverse, total, s.hotBlendConfirmed) { v ->
            if (!v.ok && v.has(SafetyPolicy.HOT_BLEND)) {
                a.confirm(tr("Contenu chaud"), tr("Le bol est à %d °C. Passer à la vitesse %d ?", a.snapshot.actualTempC, speed), tr("Confirmer"), icon = Icon.Kind.THERMO) {
                    a.machine.adjust(speed, tempLevel, reverse, total, true) { a.refusal(it) }
                }
            } else a.refusal(v)
        }
    }

    private fun maxSeconds() = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()

    private fun refreshWheels() {
        wMin.labels = (0..maxSeconds() / 60).map { String.format("%02d", it) }
        wMin.set(seconds / 60)
        wSec.set((seconds % 60) / 5)
        val table = TemperatureTable.current
        wTemp.labels = tempSteps.map { if (it == 0) "—" else "${table.celsius(it)}°" }
        wTemp.set(tempSteps.indexOf(tempLevel).coerceAtLeast(0))
        wSpeed.labels = (1..10).map { it.toString() }
        wSpeed.set(speed - 1)
    }

    private fun refresh() {
        refreshWheels()
        revBtn.background = Ui.pressable(Ui.round(if (reverse) Ui.INK else Ui.SURFACE, 29f), 29f)
        Ui.buttonText(revBtn)?.setTextColor(if (reverse) Ui.ON_INK else Ui.TEXT)
        Ui.buttonIcon(revBtn)?.color = if (reverse) Ui.ON_INK else Ui.TEXT
        accRow.removeAllViews()
        accRow.addView(Ui.segmented(c, listOf(Icon.Kind.BLADE to Accessory.BLADE.label, Icon.Kind.WHISK to Accessory.WHISK.label),
            if (accessory == Accessory.WHISK) 1 else 0, 34) { i -> setAccessory(if (i == 1) Accessory.WHISK else Accessory.BLADE) })
        refreshHintOnly()
        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        Ui.setButtonEnabled(startBtn, v.isEmpty())
    }

    override fun onSnapshot(s: Snapshot) {
        val heating = s.hasSample && (s.actualTempC >= 40 || (s.jobActive && s.jobTempLevel > 0))
        val state = when {
            !s.hasSample -> "couvercle"
            s.lidOpen -> "vide"
            heating -> "chauffe"
            else -> "couvercle"
        }
        if (bowl.tag != state) { bowl.tag = state; bowl.setImageBitmap(Ui.bowlBitmap(c, state)) }
        val cold = s.hasSample && s.actualTempC < 37 && a.isSmart()
        caption.text = when {
            !s.hasSample -> tr("Base non connectée")
            else -> (if (cold) tr("Bol à moins de 37 °C") else tr("Bol à %d °C", s.actualTempC)) + " · " +
                (if (s.lidOpen) tr("couvercle ouvert") else tr("couvercle fermé"))
        }
        weight.text = if (s.hasSample) "${s.weightG} g" else "—"
        val manualJob = s.kind?.name == "MANUAL" && (s.state == JobState.RUNNING || s.state == JobState.STARTING || s.state == JobState.PAUSED)
        live = manualJob && s.state != JobState.PAUSED
        // Pendant la cuisson, la température et la vitesse se changent cran par cran (molette du robot ou glisser).
        if (live && !pendingEdit) {
            val remaining = ((s.remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
            if (remaining != seconds) {
                seconds = remaining
                wMin.set(seconds / 60)
                wSec.set((seconds % 60) / 5)
            }
        }
        if (wasLive && !live && !manualJob) {
            seconds = configuredSeconds
            refresh()
        }
        wasLive = live
        val busy = s.busy
        startBtn.visibility = if (busy && s.state != JobState.PAUSED) View.GONE else View.VISIBLE
        Ui.buttonText(startBtn)?.text = if (s.state == JobState.PAUSED && manualJob) tr("Reprendre") else tr("Démarrer")
        turbo.isEnabled = !busy || s.turboActive
        turbo.alpha = if (turbo.isEnabled) 1f else 0.35f
        if (live) { hint.text = tr("Réglages appliqués en direct"); hint.background = Ui.round(Ui.OK_SOFT, 13f); hint.setTextColor(Ui.OK) }
        else if (!busy) refreshHintOnly()
    }

    private fun refreshHintOnly() {
        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        val limited = (tempLevel > 0 || reverse) && speed >= Limits.MAX_SPEED_HEATED
        hint.text = v.firstOrNull()?.message ?: when {
            a.snapshot.hot && speed >= 4 -> tr("Contenu chaud : confirmation demandée")
            limited -> tr("En chauffe ou en sens inverse, la vitesse est limitée à %d.", Limits.MAX_SPEED_HEATED)
            else -> ""
        }
        hint.background = if (hint.text.isEmpty()) null else Ui.round(Ui.ACCENT_SOFT, 13f)
        hint.setTextColor(Ui.ACCENT)
        hint.visibility = if (hint.text.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    override fun onKnob(delta: Int): Boolean {
        change(selected, delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        val s = a.snapshot
        when {
            s.state == JobState.RUNNING -> a.machine.pause { a.refusal(it) }
            else -> start()
        }
        return true
    }
}
