package app.toque.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Molette verticale du design v2 (comme le TM7) : la valeur choisie en grand au centre, les
 * voisines plus petites et plus pâles au-dessus et en dessous. On la fait défiler du doigt (elle se
 * cale sur une valeur), ou on touche une voisine pour y aller directement.
 */
class Wheel(c: Context, private val accent: Int) : View(c) {
    var labels: List<String> = emptyList()
        set(v) { field = v; index = index.coerceIn(0, maxOf(0, v.size - 1)); invalidate() }
    var index = 0
        private set
    /** Appelé quand l'utilisateur choisit une valeur (pas lors d'un [set] par le code). */
    var onChange: (Int) -> Unit = {}

    private val row get() = Ui.uf(56f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var offset = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var tracker: VelocityTracker? = null

    fun set(i: Int) {
        index = i.coerceIn(0, maxOf(0, labels.size - 1))
        offset = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val cx = width / 2f
        for (k in -3..3) {
            val i = index + k
            if (i < 0 || i >= labels.size) continue
            val y = cy + k * row + offset
            val d = (abs(y - cy) / row).coerceAtMost(3f)
            // 0 → grand et net ; 1 → moyen ; 2 → petit et pâle.
            val size = when {
                d < 1f -> lerp(50f, 24f, d)
                d < 2f -> lerp(24f, 16f, d - 1f)
                else -> 16f
            }
            val alpha = when {
                d < 1f -> lerp(1f, 0.5f, d)
                d < 2f -> lerp(0.5f, 0.22f, d - 1f)
                else -> lerp(0.22f, 0f, (d - 2f).coerceAtMost(1f))
            }
            paint.textSize = Ui.uf(size)
            paint.typeface = if (d < 0.5f) Ui.HEADING else Ui.MEDIUM
            paint.color = if (d < 0.5f) accent else Ui.TEXT
            paint.alpha = (alpha * (if (isEnabled) 255 else 120)).roundToInt()
            paint.letterSpacing = if (d < 0.5f) -0.04f else 0f
            val fm = paint.fontMetrics
            canvas.drawText(labels[i], cx, y - (fm.ascent + fm.descent) / 2f, paint)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled || labels.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downY = e.y; lastY = e.y; dragging = false
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                if (!dragging && abs(e.y - downY) > slop) dragging = true
                if (dragging) {
                    offset += e.y - lastY
                    lastY = e.y
                    // Passe d'une valeur à l'autre en glissant, sans dépasser les bornes.
                    while (offset > row / 2 && index > 0) { index--; offset -= row; Ui.tapSound() }
                    while (offset < -row / 2 && index < labels.size - 1) { index++; offset += row; Ui.tapSound() }
                    offset = offset.coerceIn(-row / 2, row / 2)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(e)
                if (dragging) {
                    tracker?.computeCurrentVelocity(1000)
                    val v = tracker?.yVelocity ?: 0f
                    // Lancer : quelques crans de plus selon la vitesse du geste.
                    val extra = (-v / (row * 6f)).roundToInt().coerceIn(-15, 15)
                    choose(index + extra)
                } else {
                    val k = ((e.y - height / 2f) / row).roundToInt()
                    if (k != 0) choose(index + k)
                }
                offset = 0f
                invalidate()
                tracker?.recycle(); tracker = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                offset = 0f; invalidate()
                tracker?.recycle(); tracker = null
                if (dragging) onChange(index)
            }
        }
        return true
    }

    private fun choose(i: Int) {
        index = i.coerceIn(0, labels.size - 1)
        Ui.tapSound()
        onChange(index)
    }
}
