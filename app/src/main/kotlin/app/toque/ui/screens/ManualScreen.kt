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
import app.toque.ui.SpeedBar
import app.toque.ui.TempRing
import app.toque.ui.TempScale
import app.toque.ui.Ui
import app.toque.ui.tap
import app.toque.ui.ValueCard
import app.toque.ui.isSmart

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

    private lateinit var timeCard: ValueCard
    private lateinit var tempCard: ValueCard
    private lateinit var speedCard: ValueCard
    private lateinit var dirRow: LinearLayout
    private lateinit var accRow: LinearLayout
    private lateinit var ring: TempRing
    private lateinit var weight: TextView
    private lateinit var lid: TextView
    private lateinit var speedBar: SpeedBar
    private lateinit var tempScale: TempScale
    private lateinit var startBtn: LinearLayout
    private lateinit var hint: TextView
    private lateinit var turbo: HoldButton
    private var live = false
    private var wasLive = false
    private var pendingEdit = false
    private var timeEdited = false
    private var configuredSeconds = seconds
    private val pushLive = Runnable { sendAdjust() }

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), Ui.u(16))
        val left = Ui.vertical(c)
        val cards = Ui.horizontal(c)
        timeCard = ValueCard(c, tr("Durée"), Icon.Kind.CLOCK)
        tempCard = ValueCard(c, tr("Température"), Icon.Kind.THERMO)
        speedCard = ValueCard(c, tr("Vitesse"), Icon.Kind.SPEED)
        val all = listOf(timeCard, tempCard, speedCard)
        for ((i, card) in all.withIndex()) {
            card.onSelect = { selected = i; refresh() }
            card.onMinus = { change(i, -1) }
            card.onPlus = { change(i, 1) }
            cards.addView(card, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { if (i < 2) rightMargin = Ui.u(12) })
        }
        // Durées toutes prêtes
        val presets = Ui.horizontal(c)
        for ((k, sec) in intArrayOf(60, 300, 600).withIndex()) {
            val t = Ui.text(c, Ui.duration(sec), 13.5f, Ui.TEXT2, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
            t.background = Ui.pressable(Ui.round(0, 16f, Ui.STROKE, 1.2f), 16f)
            t.isClickable = true
            t.tap { selected = 0; setSeconds(sec) }
            presets.addView(t, LinearLayout.LayoutParams(0, Ui.u(32), 1f).apply { if (k > 0) leftMargin = Ui.u(6) })
        }
        timeCard.extra.addView(presets, Ui.flp(Ui.MATCH, Ui.WRAP))
        val table = TemperatureTable.current
        tempScale = TempScale(c, IntArray(table.maxLevel + 1) { table.celsius(it) })
        tempScale.onPick = { lv -> selected = 1; setTempLevel(lv) }
        tempCard.extra.addView(tempScale, Ui.flp(Ui.MATCH, Ui.WRAP))
        speedBar = SpeedBar(c)
        speedBar.onPick = { lv -> selected = 2; setSpeed(lv) }
        speedCard.extra.addView(speedBar, Ui.flp(Ui.MATCH, Ui.u(38)))
        left.addView(cards, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val options = Ui.horizontal(c)
        dirRow = Ui.horizontal(c)
        accRow = Ui.horizontal(c)
        options.addView(dirRow)
        options.addView(accRow, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14))
        options.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))
        turbo = HoldButton(c, "Turbo", onDown = {
            a.machine.turboDown(accessory) { v -> if (!v.ok) a.refusal(v) }
        }, onUp = { a.machine.turboUp() })
        options.addView(turbo, Ui.lp(Ui.WRAP, Ui.u(52)))
        left.addView(options, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(16) })

        val right = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        right.background = Ui.round(Ui.SURFACE, 24f)
        Ui.lift(right)
        right.setPadding(Ui.u(18), Ui.u(14), Ui.u(18), Ui.u(16))
        right.addView(Ui.label(c, tr("Dans le bol"), Ui.TEXT2, 12.5f), Ui.lp(Ui.MATCH, Ui.WRAP))
        ring = TempRing(c)
        right.addView(ring, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(4) })
        fun row(label: String): TextView {
            val r = Ui.horizontal(c)
            r.addView(Ui.text(c, label, 15f, Ui.TEXT2), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            val v = Ui.text(c, "", 15f, Ui.TEXT, Ui.MEDIUM)
            r.addView(v)
            right.addView(r, Ui.lp(t = 8))
            return v
        }
        weight = row(tr("Balance"))
        lid = row(tr("Couvercle"))
        hint = Ui.text(c, "", 14f, Ui.WARN).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) }
        right.addView(hint, Ui.lp(Ui.MATCH, Ui.WRAP, t = 10))
        startBtn = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 68, textSize = 21f) { start() }
        right.addView(startBtn, Ui.lp(Ui.MATCH, Ui.WRAP, t = 10))
        root.addView(right, LinearLayout.LayoutParams(Ui.u(246), Ui.MATCH))
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

    private fun refresh() {
        val maxS = if (speed > 6) tr("max 15 min") else tr("max 99 min")
        timeCard.set(Ui.clock(seconds * 1000L), "", maxS)
        if (tempLevel == 0) tempCard.set("—", "", tr("Sans chauffe"), Ui.TEXT2)
        else {
            val t = TemperatureTable.current.celsius(tempLevel)
            tempCard.set("$t", "°C", if (t >= 100) tr("Ébullition") else if (t >= 60) tr("Cuisson") else tr("Tiédir"), Ui.tempColor(t))
        }
        tempScale.level = tempLevel
        speedCard.set("$speed", "", when (speed) {
            1 -> tr("Mijoter")
            2, 3 -> tr("Mélanger")
            in 4..6 -> tr("Hacher")
            else -> tr("Mixer")
        } + if (reverse) " · " + tr("inverse") else "")
        speedBar.level = speed
        speedBar.max = speedMax().first
        timeCard.select(selected == 0)
        tempCard.select(selected == 1)
        speedCard.select(selected == 2)

        dirRow.removeAllViews()
        dirRow.addView(Ui.segmented(c, listOf(Icon.Kind.FORWARD to tr("Normal"), Icon.Kind.REVERSE to tr("Inverse")), if (reverse) 1 else 0, 42) { i ->
            setReverse(i == 1)
        })
        accRow.removeAllViews()
        accRow.addView(Ui.segmented(c, listOf(Icon.Kind.BLADE to Accessory.BLADE.label, Icon.Kind.WHISK to Accessory.WHISK.label),
            if (accessory == Accessory.WHISK) 1 else 0, 42) { i -> setAccessory(if (i == 1) Accessory.WHISK else Accessory.BLADE) })

        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = v.firstOrNull()?.message ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
        Ui.setButtonEnabled(startBtn, v.isEmpty())
    }

    override fun onSnapshot(s: Snapshot) {
        val target = if (s.jobActive && s.jobTempLevel > 0) TemperatureTable.current.celsius(s.jobTempLevel) else
            if (tempLevel > 0) TemperatureTable.current.celsius(tempLevel) else 0
        val cold = s.hasSample && s.actualTempC < 37 && a.isSmart()
        ring.set(if (s.hasSample) s.actualTempC else 0, target,
            when { !s.hasSample -> "—"; cold -> tr("froid"); else -> "${s.actualTempC}°" },
            when { !s.hasSample -> tr("base non connectée"); cold -> tr("moins de 37 °C"); target > 0 -> tr("consigne %d °C", target); else -> tr("mesuré") })
        weight.text = if (s.hasSample) "${s.weightG} g" else "—"
        lid.text = if (!s.hasSample) "—" else if (s.lidOpen) tr("Ouvert") else tr("Fermé")
        lid.setTextColor(if (s.hasSample && s.lidOpen) Ui.WARN else if (s.hasSample) Ui.OK else Ui.TEXT2)
        val manualJob = s.kind?.name == "MANUAL" && (s.state == JobState.RUNNING || s.state == JobState.STARTING || s.state == JobState.PAUSED)
        live = manualJob && s.state != JobState.PAUSED
        tempCard.repeatEnabled = !live
        speedCard.repeatEnabled = !live
        // Pendant la cuisson, vitesse et température ne changent qu'avec + et − (pas de saut d'un appui sur la barre).
        speedBar.isEnabled = !live
        tempScale.isEnabled = !live
        if (live && !pendingEdit) {
            // Pendant la cuisson, la carte Durée affiche le temps restant.
            val remaining = ((s.remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
            if (remaining != seconds) {
                seconds = remaining
                timeCard.set(Ui.clock(seconds * 1000L), "", tr("restant"))
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
        if (live) hint.text = tr("Réglages appliqués en direct")
        else if (!busy) refreshHintOnly()
    }

    private fun refreshHintOnly() {
        val v = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = v.firstOrNull()?.message ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
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
