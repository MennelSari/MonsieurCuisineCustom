package app.toque.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animation d'ouverture Camybrith Cooking : la cocotte apparaît, la vapeur monte, puis le nom.
 * Même dessin que le logo de démarrage et l'animation du robot (tools/smart, marque.py).
 * Même chronologie que l'animation du robot (30 images), en 1,6 s, puis la vapeur ondule 0,5 s et
 * tout s'efface en 0,3 s. Un appui la passe. Police Poppins (assets/fonts, licence OFL).
 */
class SplashView(c: Context, private val onDone: () -> Unit) : View(c) {
    private var t = 0f
    private var phase = 0f
    private var finished = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("Poppins-Bold", Typeface.DEFAULT_BOLD); textAlign = Paint.Align.CENTER }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font("Poppins-Light", Typeface.create("sans-serif-light", Typeface.NORMAL)) }
    private val r = RectF()
    private val path = Path()
    private var anim: ValueAnimator? = null

    init {
        setBackgroundColor(BRAND_BG)
        isClickable = true
        setOnClickListener { finish() }
    }

    private fun font(name: String, fallback: Typeface): Typeface =
        try {
            Typeface.createFromAsset(context.assets, "fonts/$name.ttf")
        } catch (_: RuntimeException) {
            fallback
        }

    fun start() {
        if (finished) return
        anim = ValueAnimator.ofFloat(0f, TOTAL).apply {
            duration = (TOTAL * 1000).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                t = min(v, INTRO)
                phase = if (v > INTRO) 2 * PI.toFloat() * (v - INTRO) / 2f else 0f
                alpha = if (v > TOTAL - FADE) max(0f, (TOTAL - v) / FADE) else 1f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = finish()
            })
            start()
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        anim?.removeAllUpdateListeners()
        anim?.cancel()
        onDone()
    }

    private companion object {
        const val INTRO = 1.6f
        const val TOTAL = 2.4f
        const val FADE = 0.3f
        const val WHITE = 0xFFF3F4F6.toInt()
        // Couleurs de la marque, les mêmes que le logo de démarrage : l'animation ne suit pas le thème.
        const val BRAND_BG = 0xFF0B0C0E.toInt()
        const val BRAND_ORANGE = 0xFFFF8A3D.toInt()
    }

    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

    private fun mix(a: Int, b: Int, k: Float): Int {
        val f = k.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * f)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Image courante de la première partie de l'animation du robot (30 images).
        val f = t / INTRO * 30f
        val pot = ease(f / 9f)
        val words = ease((f - 14f) / 10f)
        val size = 0.34f * h
        val cx = w / 2
        val cy = h * 0.36f
        val s = size / 200f
        fun X(x: Float) = cx + (x - 100f) * s
        fun Y(y: Float) = cy + (y - 100f) * s

        if (pot > 0f) {
            fill.color = mix(BRAND_BG, BRAND_ORANGE, pot)
            r.set(X(20f), Y(104f), X(42f), Y(120f)); canvas.drawRoundRect(r, 7 * s, 7 * s, fill)
            r.set(X(158f), Y(104f), X(180f), Y(120f)); canvas.drawRoundRect(r, 7 * s, 7 * s, fill)
            r.set(X(38f), Y(96f), X(162f), Y(168f)); canvas.drawRoundRect(r, 26 * s, 26 * s, fill)
            canvas.drawRect(X(38f), Y(96f), X(162f), Y(120f), fill)
            r.set(X(28f), Y(82f), X(172f), Y(98f)); canvas.drawRoundRect(r, 8 * s, 8 * s, fill)
            r.set(X(86f), Y(68f), X(114f), Y(84f)); canvas.drawRoundRect(r, 7 * s, 7 * s, fill)
            fill.color = mix(BRAND_BG, BRAND_ORANGE, pot * 0.55f)
            canvas.drawRect(X(38f), Y(98f), X(162f), Y(104f), fill)
        }
        stroke.strokeWidth = 9 * s
        val xs = floatArrayOf(66f, 100f, 134f)
        for (i in 0..2) {
            val g = ease((f - 6f - 3f * i) / 8f)
            if (g <= 0f) continue
            val glow = 0.85f + 0.15f * kotlin.math.cos(phase)
            stroke.color = mix(BRAND_BG, WHITE, (if (i == 1) 0.85f else 0.7f) * (if (phase > 0f) glow else 1f))
            path.reset()
            val n = 40
            for (k in 0..n) {
                val u = k / n.toFloat()
                if (u > g) break
                val y = Y(58f) + (Y(8f) - Y(58f)) * u
                val x = X(xs[i]) + 7 * s * sin(u * 2.2f * PI.toFloat() + phase + i * 1.3f) * (0.35f + 0.65f * u)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, stroke)
        }
        if (words > 0f) {
            // Comme marque.py : texte posé par sa ligne d'ascendante (haut de la police), pas sa ligne de base.
            val ts = h * 0.105f
            val slide = 24f * (h / 800f) * (1 - words)
            bold.textSize = ts
            bold.color = mix(BRAND_BG, WHITE, words)
            canvas.drawText("Camybrith", cx, h * 0.56f + slide - bold.fontMetrics.ascent, bold)
            light.textSize = ts * 0.42f
            light.color = mix(BRAND_BG, BRAND_ORANGE, words)
            val word = "COOKING"
            val sp = ts * 0.2f
            val widths = FloatArray(word.length) { light.measureText(word[it].toString()) }
            var x = cx - (widths.sum() + sp * (word.length - 1)) / 2
            val y = h * 0.56f + ts * 1.18f - slide * 0.5f - light.fontMetrics.ascent
            for (k in word.indices) {
                canvas.drawText(word[k].toString(), x, y, light)
                x += widths[k] + sp
            }
        }
    }
}
