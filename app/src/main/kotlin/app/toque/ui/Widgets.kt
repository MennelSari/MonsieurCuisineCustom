package app.toque.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.SearchIndex
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Carte de réglage (durée, température, vitesse) : intitulé, grande valeur, zone libre (barre de
 * vitesse, échelle de température, durées toutes prêtes), boutons − et +. La carte choisie est celle
 * que règle le bouton rotatif.
 */
class ValueCard(c: Context, label: String, icon: Icon.Kind) : LinearLayout(c) {
    private val valueView = Ui.text(c, "", 74f, Ui.TEXT, Ui.LIGHT).apply { maxLines = 1 }
    private val unitView = Ui.text(c, "", 24f, Ui.TEXT2, Ui.LIGHT)
    private val subView = Ui.ellipsize(Ui.text(c, "", 15f, Ui.TEXT2))
    private val iconView = Icon(c, icon, Ui.TEXT2)
    private val knobTag = Ui.label(c, tr("Molette"), Ui.ACCENT, 11f)
    /** Zone libre sous la valeur. */
    val extra = FrameLayout(c)
    private val minus: View
    private val plus: View
    var onMinus: () -> Unit = {}
    var onPlus: () -> Unit = {}
    var onSelect: () -> Unit = {}
    /** Répétition à l'appui long (désactivée pendant une cuisson pour vitesse et température). */
    var repeatEnabled = true
    private var isSel = false

    init {
        orientation = VERTICAL
        setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(14))
        val head = Ui.horizontal(c)
        head.addView(iconView, LayoutParams(Ui.u(20), Ui.u(20)).apply { rightMargin = Ui.u(8) })
        head.addView(Ui.ellipsize(Ui.label(c, label, Ui.TEXT2, 12.5f).apply { letterSpacing = 0.07f }), LayoutParams(0, Ui.WRAP, 1f))
        knobTag.setPadding(Ui.u(8), Ui.u(3), Ui.u(8), Ui.u(3))
        knobTag.background = Ui.round(Ui.ACCENT_SOFT, 10f)
        head.addView(knobTag)
        addView(head)
        val row = Ui.horizontal(c, Gravity.BOTTOM)
        row.addView(valueView)
        row.addView(unitView, Ui.lp(Ui.WRAP, Ui.WRAP, l = 5, b = 9))
        addView(row, Ui.lp(t = 12))
        addView(subView, Ui.lp(t = 5))
        addView(View(c), LayoutParams(1, 0, 1f))
        addView(extra, Ui.lp(Ui.MATCH, Ui.WRAP, b = 12))
        val buttons = Ui.horizontal(c)
        minus = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 52, textSize = 20f) { onSelect(); onMinus() }
        plus = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 52, textSize = 20f) { onSelect(); onPlus() }
        repeatOnHold(minus, { repeatEnabled }) { onMinus() }
        repeatOnHold(plus, { repeatEnabled }) { onPlus() }
        buttons.addView(minus, LayoutParams(0, Ui.u(52), 1f).apply { rightMargin = Ui.u(10) })
        buttons.addView(plus, LayoutParams(0, Ui.u(52), 1f))
        addView(buttons)
        isClickable = true
        tap { onSelect() }
        Ui.lift(this)
        refreshBg()
    }

    fun set(value: String, unit: String = "", sub: String = "", color: Int = Ui.TEXT) {
        // Une durée (« 03:12 ») tient sur une ligne avec un corps un peu plus petit.
        valueView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(if (value.length >= 5) 58f else 74f))
        valueView.text = value
        valueView.setTextColor(color)
        unitView.text = unit
        subView.text = sub
    }

    fun select(sel: Boolean) {
        if (sel == isSel) return
        isSel = sel
        refreshBg()
    }

    fun setEditable(on: Boolean) {
        Ui.setButtonEnabled(minus, on)
        Ui.setButtonEnabled(plus, on)
    }

    private fun refreshBg() {
        background = Ui.round(Ui.SURFACE, 24f, if (isSel) Ui.ACCENT else Ui.SURFACE, 2.5f)
        iconView.color = if (isSel) Ui.ACCENT else Ui.TEXT2
        knobTag.visibility = if (isSel) VISIBLE else GONE
    }

    companion object {
        /**
         * Répète l'action tant que le bouton reste appuyé (après 450 ms). S'arrête de lui-même si
         * le bouton n'est plus enfoncé ou après 40 répétitions, même si le relâchement est perdu.
         */
        fun repeatOnHold(v: View, enabled: () -> Boolean = { true }, action: () -> Unit) {
            var count = 0
            val r = object : Runnable {
                override fun run() {
                    if (!v.isPressed || !enabled() || count >= 40) return
                    count++
                    action()
                    v.postDelayed(this, 110)
                }
            }
            v.setOnTouchListener { view, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        count = 0
                        view.removeCallbacks(r)
                        view.postDelayed(r, 450)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.removeCallbacks(r)
                }
                false
            }
        }
    }
}

/** Dix barres de vitesse, de plus en plus hautes ; un appui choisit la vitesse. */
class SpeedBar(c: Context) : View(c) {
    var level = 0
        set(v) { if (field != v) { field = v; invalidate() } }
    var max = 10
        set(v) { if (field != v) { field = v; invalidate() } }
    var color = Ui.COOL
    var onPick: ((Int) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        val gap = Ui.uf(4f)
        val bw = (width - 9 * gap) / 10f
        for (i in 1..10) {
            val h = height * (0.38f + 0.062f * i)
            val x = (i - 1) * (bw + gap)
            r.set(x, height - h, x + bw, height.toFloat())
            p.color = when {
                i <= level -> color
                i > max -> Ui.withAlpha(Ui.SURFACE3, 0.45f)
                else -> Ui.SURFACE3
            }
            canvas.drawRoundRect(r, bw / 3, bw / 3, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pick = onPick ?: return false
        if (!isEnabled) return false
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            val lv = ((e.x / width) * 10 + 1).toInt().coerceIn(1, 10)
            if (lv != level) pick(lv)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }
}

/** Échelle de température, de 37 à 130 °C ; un appui choisit le palier le plus proche. */
class TempScale(c: Context, private val levels: IntArray) : View(c) {
    var level = 0
        set(v) { if (field != v) { field = v; invalidate() } }
    var onPick: ((Int) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val lo = 37f
    private val hi = 130f

    private fun frac(c: Int) = ((c - lo) / (hi - lo)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val th = Ui.uf(11f)
        r.set(0f, 0f, width.toFloat(), th)
        p.color = Ui.SURFACE3
        canvas.drawRoundRect(r, th / 2, th / 2, p)
        val cel = levels[level.coerceIn(0, levels.size - 1)]
        if (cel > 0) {
            r.set(0f, 0f, max(th, width * frac(cel)), th)
            p.color = Ui.tempColor(cel)
            canvas.drawRoundRect(r, th / 2, th / 2, p)
        }
        t.textSize = Ui.uf(11.5f)
        t.color = Ui.TEXT3
        t.typeface = Ui.REGULAR
        for (m in intArrayOf(37, 60, 90, 120)) {
            val x = (width * frac(m)).coerceIn(Ui.uf(10f), width - Ui.uf(10f))
            canvas.drawText("$m", x, th + Ui.uf(17f), t)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Ui.u(32))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pick = onPick ?: return false
        if (!isEnabled) return false
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            val cel = lo + (e.x / width).coerceIn(0f, 1f) * (hi - lo)
            var best = 1
            for (i in 1 until levels.size) if (abs(levels[i] - cel) < abs(levels[best] - cel)) best = i
            if (best != level) pick(best)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }
}

/** Anneau de température du bol : valeur mesurée au centre, arc proportionnel, repère de consigne. */
class TempRing(c: Context) : View(c) {
    private var actual = -1
    private var target = 0
    private var center = ""
    private var sub = ""
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()

    /** [actual] en °C (0 si inconnue ou sous le seuil de mesure), [center] et [sub] : textes affichés. */
    fun set(actual: Int, target: Int, center: String, sub: String) {
        if (actual == this.actual && target == this.target && center == this.center && sub == this.sub) return
        this.actual = actual
        this.target = target
        this.center = center
        this.sub = sub
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val sw = size * 0.07f
        val cx = width / 2f
        val cy = height / 2f
        val rad = size / 2f - sw
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        arc.strokeWidth = sw
        arc.color = Ui.SURFACE3
        canvas.drawArc(r, 135f, 270f, false, arc)
        val frac = (actual / 130f).coerceIn(0f, 1f)
        arc.color = Ui.tempColor(actual)
        if (frac > 0f) canvas.drawArc(r, 135f, 270f * frac, false, arc)
        if (target > 0) {
            val a = Math.toRadians((135 + 270 * (target / 130f).coerceIn(0f, 1f)).toDouble())
            arc.strokeWidth = sw * 0.32f
            arc.color = Ui.TEXT
            val r1 = rad - sw * 0.95f
            val r2 = rad + sw * 0.95f
            canvas.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
                cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), arc)
        }
        text.typeface = Ui.LIGHT
        text.color = Ui.TEXT
        text.textSize = size * (if (center.length > 4) 0.2f else 0.27f)
        canvas.drawText(center, cx, cy + size * 0.07f, text)
        text.typeface = Ui.REGULAR
        text.color = Ui.TEXT2
        text.textSize = size * 0.068f
        canvas.drawText(sub, cx, cy + size * 0.21f, text)
    }
}

/** Bouton « maintenir pour Turbo ». */
class HoldButton(c: Context, label: String, private val onDown: () -> Unit, private val onUp: () -> Unit) : LinearLayout(c) {
    private val text: TextView
    private val icon: Icon

    init {
        orientation = HORIZONTAL
        setGravity(Gravity.CENTER)
        setPadding(Ui.u(20), 0, Ui.u(20), 0)
        minimumHeight = Ui.u(52)
        background = Ui.round(0, 26f, Ui.STROKE, 1.5f)
        icon = Icon(c, Icon.Kind.BOLT, Ui.WARN)
        addView(icon, LayoutParams(Ui.u(22), Ui.u(22)).apply { rightMargin = Ui.u(8) })
        text = Ui.text(c, label, 17f, Ui.TEXT, Ui.MEDIUM)
        addView(text)
        isClickable = true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                background = Ui.round(Ui.WARN, 26f)
                text.setTextColor(Ui.BG)
                icon.color = Ui.BG
                onDown()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                background = Ui.round(0, 26f, Ui.STROKE, 1.5f)
                text.setTextColor(Ui.TEXT)
                icon.color = Ui.WARN
                onUp()
            }
        }
        return true
    }
}

/**
 * Jauge de pesée façon verre doseur : elle se remplit comme un liquide de la couleur de
 * l'ingrédient, avec une surface qui ondule et un filet qui coule tant que le poids monte. Au delà
 * de la cible, le trop apparaît en rouge rayé au dessus de la ligne pointillée.
 */
class LiquidGauge(c: Context) : View(c) {
    /** Cible en grammes ; 0 : simple balance, l'échelle s'adapte au poids. */
    var targetGrams = 0
        set(v) { field = v; invalidate() }
    /** La cible est un minimum (vapeur) : rien de rouge au delà. */
    var minimum = false
    var fill = Ui.COOL
        set(v) { field = v; invalidate() }
    var targetLabel = ""
    var halfLabel = ""
    var showLabels = true

    private var measured = 0f
    private var shown = 0f
    private var agitation = 0f
    private var stream = 0f
    private var phase = 0f
    private var lastFrame = 0L
    private var lastRise = 0L
    private var lastChange = 0L
    private var started = false

    private val jar = Path()
    private val liquid = Path()
    private val surface = Path()
    private val box = RectF()
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pOver = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radii = FloatArray(8)

    /** Nouveau poids (grammes, déjà remis à zéro). */
    fun setWeight(g: Int, instant: Boolean = false) {
        val v = max(0, g).toFloat()
        val now = SystemClock.uptimeMillis()
        if (!started || instant) {
            started = true
            measured = v
            shown = v
            lastFrame = now
            invalidate()
            return
        }
        val d = v - measured
        if (abs(d) < 0.5f) return
        val scaleG = max(40f, full() * 0.12f)
        agitation = min(1f, agitation + abs(d) / scaleG)
        if (d > 0) {
            stream = min(1f, max(stream, 0.35f + d / scaleG))
            lastRise = now
        }
        measured = v
        lastChange = now
        kick()
    }

    private fun kick() {
        if (lastFrame == 0L) lastFrame = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** Poids correspondant au haut du verre. */
    private fun full(): Float = if (targetGrams > 0) targetGrams / TARGET_AT else {
        val need = max(measured, shown) * 1.25f
        val steps = floatArrayOf(250f, 500f, 1000f, 1500f, 2000f, 3000f, 5000f)
        steps.firstOrNull { it >= need } ?: 5000f
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        // Pas de temps réel, borné : l'animation garde son rythme même si des images sont sautées.
        val dt = ((now - (if (lastFrame == 0L) now else lastFrame)).coerceIn(0, 200)) / 1000f
        lastFrame = now
        shown += (measured - shown) * (1f - exp(-dt / 0.16f))
        if (abs(measured - shown) < 0.3f) shown = measured
        phase += dt * (2.2f + agitation * 6f)
        agitation *= exp(-dt / 0.9f)
        if (now - lastRise > 250) stream *= exp(-dt / 0.3f)

        // Place des étiquettes à droite ; sans cible, le verre garde sa largeur et se centre.
        val labelArea = if (showLabels) Ui.uf(92f) else 0f
        val offset = if (showLabels && targetGrams <= 0) labelArea / 2 else 0f
        val sw = Ui.uf(2.5f)
        val left = offset + sw / 2
        val right = offset + width - labelArea - sw / 2
        val top = sw / 2
        val bottom = height - sw / 2
        val innerH = bottom - top
        box.set(left, top, right, bottom)
        val rt = min(Ui.uf(24f), (right - left) / 3f)
        val rb = min(Ui.uf(32f), (right - left) / 2.6f)
        radii[0] = rt; radii[1] = rt; radii[2] = rt; radii[3] = rt
        radii[4] = rb; radii[5] = rb; radii[6] = rb; radii[7] = rb
        jar.reset()
        jar.addRoundRect(box, radii, Path.Direction.CW)

        // Verre vide : blanc cassé en thème clair (les ingrédients clairs y restent visibles), brun en sombre.
        pTrack.color = if (Ui.dark) Ui.SURFACE2 else Ui.shade(Ui.SURFACE, 1.4f)
        pTrack.style = Paint.Style.FILL
        canvas.drawPath(jar, pTrack)
        val lum = Ui.luminance(fill)
        // Corps du liquide : un ingrédient très clair est un peu assombri sur le thème clair.
        val body = if (!Ui.dark && lum > 0.8f) Ui.shade(fill, 0.86f) else fill
        // Ménisque : plus foncé que le liquide, ou plus clair pour un liquide sombre sur le thème sombre.
        val edge = if (Ui.dark && lum < 0.35f) Ui.shade(fill, 1.6f) else Ui.shade(body, if (lum > 0.7f) 0.7f else 0.78f)

        val fullG = full()
        val targetY = if (targetGrams > 0) bottom - TARGET_AT * innerH else -1f
        canvas.save()
        canvas.clipPath(jar)

        // Liquide
        val level = (shown / fullG).coerceIn(0f, 1f) * innerH
        val surfY = bottom - level
        if (level > 0.5f) {
            val amp = min(level / 3f, Ui.uf(1.6f) + agitation * Ui.uf(9f))
            val w = right - left
            val k1 = (2 * Math.PI / (w * 0.9f)).toFloat()
            val k2 = (2 * Math.PI / (w * 0.43f)).toFloat()
            fun wave(x: Float) = amp * (0.7f * sin(k1 * x + phase) + 0.3f * sin(k2 * x - phase * 1.7f))
            liquid.reset()
            surface.reset()
            liquid.moveTo(left, bottom)
            var x = left
            val step = Ui.uf(6f)
            var first = true
            while (x <= right + step) {
                val xx = min(x, right)
                val y = surfY + wave(xx)
                liquid.lineTo(xx, y)
                if (first) { surface.moveTo(xx, y); first = false } else surface.lineTo(xx, y)
                x += step
            }
            liquid.lineTo(right, bottom)
            liquid.close()
            pFill.color = body
            pFill.shader = LinearGradient(0f, surfY, 0f, bottom, body, Ui.shade(body, 0.9f), Shader.TileMode.CLAMP)
            val over = targetGrams > 0 && !minimum && shown > targetGrams + 0.5f
            if (over) {
                canvas.save()
                canvas.clipRect(left, targetY, right, bottom)
                canvas.drawPath(liquid, pFill)
                canvas.restore()
                canvas.save()
                canvas.clipRect(left, top, right, targetY)
                val s = Ui.uf(16f)
                pOver.shader = LinearGradient(0f, 0f, s, s, intArrayOf(Ui.DANGER, Ui.DANGER, Ui.DANGER_SOFT, Ui.DANGER_SOFT),
                    floatArrayOf(0f, 0.62f, 0.62f, 1f), Shader.TileMode.REPEAT)
                canvas.drawPath(liquid, pOver)
                canvas.restore()
            } else canvas.drawPath(liquid, pFill)
            pFill.shader = null
            // Reflet du verre
            pFill.color = Ui.withAlpha(android.graphics.Color.WHITE, if (lum > 0.75f) 0.3f else 0.13f)
            canvas.drawRect(left + w * 0.72f, surfY + amp + Ui.uf(4f), left + w * 0.79f, bottom - Ui.uf(10f), pFill)
            // Ménisque, plus foncé : les ingrédients blancs restent visibles sur le thème clair
            pLine.color = if (over) Ui.DANGER else edge
            pLine.strokeWidth = Ui.uf(3f)
            canvas.drawPath(surface, pLine)
        }

        // Graduations, par dessus le liquide comme sur un verre doseur
        pLine.color = Ui.TEXT3
        pLine.pathEffect = null
        for (k in 1..10) {
            val g = if (targetGrams > 0) targetGrams * k / 10f else fullG * k / 10f
            val y = bottom - g / fullG * innerH
            val big = k == 5 || k == 10
            pLine.strokeWidth = Ui.uf(if (big) 2f else 1.5f)
            pLine.alpha = if (big) 190 else 100
            canvas.drawLine(left, y, left + Ui.uf(if (big) 20f else 11f), y, pLine)
        }
        pLine.alpha = 255

        // Filet qui coule tant que le poids monte
        if (stream > 0.03f && surfY > top + Ui.uf(8f)) {
            val cx = (left + right) / 2 + sin(phase * 0.8f) * Ui.uf(2.5f)
            val sw2 = Ui.uf(3f + 8f * stream)
            pFill.color = Ui.withAlpha(body, min(1f, stream * 1.6f))
            box.set(cx - sw2 / 2, top, cx + sw2 / 2, surfY + Ui.uf(3f))
            canvas.drawRoundRect(box, sw2 / 2, sw2 / 2, pFill)
            for (i in 0..2) {
                val dx = (if (i % 2 == 0) -1 else 1) * Ui.uf(10f + 7f * i)
                val hop = abs(sin(phase * 2.2f + i * 1.9f)) * Ui.uf(9f) * stream
                canvas.drawCircle(cx + dx, surfY - hop - Ui.uf(3f), Ui.uf(2.6f) * stream + Ui.uf(0.8f), pFill)
            }
        }
        canvas.restore()

        // Contour du verre
        pLine.color = Ui.STROKE
        pLine.strokeWidth = sw
        canvas.drawPath(jar, pLine)

        // Ligne de la cible
        if (targetGrams > 0) {
            pLine.color = Ui.withAlpha(Ui.TEXT, 0.8f)
            pLine.strokeWidth = Ui.uf(2.2f)
            pLine.pathEffect = DashPathEffect(floatArrayOf(Ui.uf(9f), Ui.uf(7f)), 0f)
            canvas.drawLine(left + sw, targetY, right - sw, targetY, pLine)
            pLine.pathEffect = null
        }
        if (showLabels && targetGrams > 0) {
            pText.typeface = Ui.MEDIUM
            pText.textSize = Ui.uf(15f)
            val tw = pText.measureText(targetLabel)
            val ph = Ui.uf(28f)
            box.set(right + Ui.uf(12f), targetY - ph / 2, right + Ui.uf(12f) + tw + Ui.uf(22f), targetY + ph / 2)
            pText.color = Ui.TEXT
            canvas.drawRoundRect(box, ph / 2, ph / 2, pText)
            pText.color = Ui.BG
            canvas.drawText(targetLabel, box.left + Ui.uf(11f), targetY + Ui.uf(5.3f), pText)
            if (halfLabel.isNotEmpty()) {
                pText.typeface = Ui.REGULAR
                pText.textSize = Ui.uf(13f)
                pText.color = Ui.TEXT3
                val hy = bottom - TARGET_AT * innerH / 2
                canvas.drawText(halfLabel, right + Ui.uf(14f), hy + Ui.uf(4.5f), pText)
            }
        }

        val moving = abs(measured - shown) > 0.3f || agitation > 0.02f || stream > 0.03f
        if (moving || now - lastChange < 2_500) postInvalidateOnAnimation()
    }

    companion object {
        /** Hauteur de la ligne cible dans le verre : la place au dessus montre le trop. */
        const val TARGET_AT = 0.78f
    }
}

/** Vignette de recette (accueil, catalogue) : photo, titre, durée et catégorie. */
class RecipeCard(c: Context, private val photoHeight: Int) : LinearLayout(c) {
    private val photoBox = FrameLayout(c)
    private val plate = Icon(c, Icon.Kind.PLATE, Ui.placeholderInk).apply { strokeWidth = 1.3f }
    private val image = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val star = FrameLayout(c)
    private val titleView = Ui.ellipsize(Ui.text(c, "", 16.5f, Ui.TEXT, Ui.MEDIUM).apply { setLineSpacing(0f, 1.08f) }, 2)
    private val meta = Ui.ellipsize(Ui.text(c, "", 13.5f, Ui.TEXT2))
    var id = ""
        private set

    init {
        orientation = VERTICAL
        background = Ui.round(Ui.SURFACE, 18f)
        clipToOutline = true
        Ui.lift(this, 2f)
        photoBox.addView(plate, Ui.flp(Ui.u(photoHeight * 0.34f), Ui.u(photoHeight * 0.34f), Gravity.CENTER))
        photoBox.addView(image, Ui.flp())
        star.background = Ui.round(Ui.SURFACE, 15f)
        star.addView(Icon(c, Icon.Kind.STAR_FILLED, Ui.ACCENT), Ui.flp(Ui.u(16), Ui.u(16), Gravity.CENTER))
        photoBox.addView(star, Ui.flp(Ui.u(30), Ui.u(30), Gravity.TOP or Gravity.END).apply { setMargins(0, Ui.u(9), Ui.u(9), 0) })
        addView(photoBox, LayoutParams(Ui.MATCH, Ui.u(photoHeight)))
        val body = Ui.vertical(c)
        body.setPadding(Ui.u(13), Ui.u(10), Ui.u(12), Ui.u(12))
        body.addView(titleView, Ui.lp(Ui.MATCH, Ui.u(38)))
        val m = Ui.horizontal(c)
        m.addView(Icon(c, Icon.Kind.CLOCK, Ui.TEXT2), LayoutParams(Ui.u(14), Ui.u(14)).apply { rightMargin = Ui.u(5) })
        m.addView(meta, LayoutParams(0, Ui.WRAP, 1f))
        body.addView(m, Ui.lp(t = 6))
        addView(body, Ui.lp())
    }

    fun bind(e: SearchIndex.Entry, favorite: Boolean, imageFile: java.io.File?) {
        id = e.id
        titleView.text = e.title
        val parts = ArrayList<String>()
        if (e.totalMinutes > 0) parts += Ui.minutes(e.totalMinutes)
        if (e.category.isNotBlank()) parts += e.category
        if (e.ratingCount > 0) parts += "★ " + String.format(I18n.locale, "%.1f", e.rating)
        meta.text = parts.joinToString(" · ")
        (meta.parent as View).visibility = if (parts.isEmpty()) GONE else VISIBLE
        star.visibility = if (favorite) VISIBLE else GONE
        photoBox.background = Ui.gradient(Ui.placeholderColors(e.title), 0f)
        ImageLoader.load(image, imageFile, Ui.u(260))
    }
}
