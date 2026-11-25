package app.toque.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * Thèmes (clair et chaleureux, sombre et raffiné) et utilitaires de mise en page, à l'échelle d'un
 * écran de référence 1024 × 600 (1 unité = 1,25 px sur l'écran 1280 × 800 du Monsieur Cuisine Smart).
 *
 * Les couleurs sont lues au moment où une vue est construite : changer de thème reconstruit l'activité.
 */
object Ui {
    enum class Theme(val key: String) {
        LIGHT("light"), DARK("dark"), AUTO("auto");

        companion object {
            fun of(k: String?) = values().firstOrNull { it.key == k } ?: AUTO
        }
    }

    /** Thème affiché (jamais AUTO). */
    var theme = Theme.DARK
        private set
    val dark: Boolean get() = theme == Theme.DARK

    var BG = 0; private set
    var RAIL = 0; private set
    var SURFACE = 0; private set
    var SURFACE2 = 0; private set
    var SURFACE3 = 0; private set
    var STROKE = 0; private set
    var TEXT = 0; private set
    var TEXT2 = 0; private set
    var TEXT3 = 0; private set
    var ACCENT = 0; private set
    var ACCENT_SOFT = 0; private set
    var ON_ACCENT = 0; private set
    var COOL = 0; private set
    var COOL_SOFT = 0; private set
    var ON_COOL = 0; private set
    var OK = 0; private set
    var OK_SOFT = 0; private set
    var WARN = 0; private set
    var WARN_SOFT = 0; private set
    var DANGER = 0; private set
    var DANGER_SOFT = 0; private set
    var ON_DANGER = 0; private set
    var SCRIM = 0; private set
    /** Barre de cuisson et messages : toujours foncée, lisible sur les deux thèmes. */
    var DOCK = 0; private set
    var DOCK_TEXT = 0; private set
    var DOCK_TEXT2 = 0; private set
    var DOCK_SURFACE = 0; private set
    var RIPPLE = 0; private set
    /** Boutons principaux (noir en clair, orange en sombre) et leur texte. */
    var INK = 0; private set
    var ON_INK = 0; private set
    /** Orange vif de la chaleur (barres de progression, vagues de chauffe). */
    var HEAT = 0; private set

    /** Ancien nom de [ACCENT_SOFT]. */
    val ACCENT_DIM: Int get() = ACCENT_SOFT

    init {
        apply(Theme.DARK)
    }

    private fun c(v: Long) = v.toInt()

    fun apply(t: Theme) {
        theme = if (t == Theme.AUTO) Theme.DARK else t
        if (theme == Theme.LIGHT) {
            // Design v2 : clair, sobre, encre presque noire ; orange réservé à la chaleur, bleu à la vitesse.
            BG = c(0xFFF6F5F2); RAIL = c(0xFFFFFFFF); SURFACE = c(0xFFFFFFFF); SURFACE2 = c(0xFFEFEEEA); SURFACE3 = c(0xFFE4E2DC)
            STROKE = c(0xFFE4E2DC); TEXT = c(0xFF121212); TEXT2 = c(0xFF5E5B55); TEXT3 = c(0xFF8C8880)
            ACCENT = c(0xFFB8410F); ACCENT_SOFT = c(0xFFFDECE3); ON_ACCENT = Color.WHITE
            COOL = c(0xFF2453C9); COOL_SOFT = c(0xFFE7EDFB); ON_COOL = Color.WHITE
            OK = c(0xFF2E7D4F); OK_SOFT = c(0xFFE4EFE8); WARN = c(0xFF94600F); WARN_SOFT = c(0xFFF6E7C8)
            DANGER = c(0xFFB83A2E); DANGER_SOFT = c(0xFFF7DCD8); ON_DANGER = Color.WHITE
            SCRIM = c(0x99121212); DOCK = c(0xFF121212); DOCK_TEXT = c(0xFFFFFFFF); DOCK_TEXT2 = c(0xFFB9B6AE); DOCK_SURFACE = c(0xFF2C2B29)
            RIPPLE = c(0x22121212)
            INK = c(0xFF121212); ON_INK = Color.WHITE; HEAT = c(0xFFE0551F)
        } else {
            BG = c(0xFF13110F); RAIL = c(0xFF0D0C0A); SURFACE = c(0xFF1D1A17); SURFACE2 = c(0xFF28241F); SURFACE3 = c(0xFF342E28)
            STROKE = c(0xFF3A332C); TEXT = c(0xFFF3ECE3); TEXT2 = c(0xFFB6AB9E); TEXT3 = c(0xFF8C8175)
            ACCENT = c(0xFFE0915F); ACCENT_SOFT = c(0xFF3A2A1F); ON_ACCENT = c(0xFF22140A)
            COOL = c(0xFF7CC4B4); COOL_SOFT = c(0xFF1E302C); ON_COOL = c(0xFF0E1F1B)
            OK = c(0xFF6FCF97); OK_SOFT = c(0xFF1C2F23); WARN = c(0xFFE9B860); WARN_SOFT = c(0xFF3A2E17)
            DANGER = c(0xFFF06A5E); DANGER_SOFT = c(0xFF3D1D1A); ON_DANGER = c(0xFF2A0F0C)
            SCRIM = c(0xCC000000); DOCK = c(0xFF2A2520); DOCK_TEXT = c(0xFFF3ECE3); DOCK_TEXT2 = c(0xFFB6AB9E); DOCK_SURFACE = c(0xFF3B342D)
            RIPPLE = c(0x33FFFFFF)
            INK = ACCENT; ON_INK = ON_ACCENT; HEAT = ACCENT
        }
    }

    /** Thème à afficher maintenant : en automatique, clair de 7 h à 20 h. */
    fun resolve(pref: Theme, cal: Calendar = Calendar.getInstance()): Theme {
        if (pref != Theme.AUTO) return pref
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return if (h in 7..19) Theme.LIGHT else Theme.DARK
    }

    var LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); private set
    var THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); private set
    var REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL); private set
    var MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set
    var BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD); private set
    /** Titres : DM Serif Display (licence SIL OFL, voir assets/fonts/OFL-DMSerifDisplay.txt). */
    var SERIF: Typeface = Typeface.SERIF; private set
    /** Marque Camybrith Cooking : Poppins (licence SIL OFL, voir assets/fonts/OFL.txt). */
    var BRAND_BOLD: Typeface = Typeface.DEFAULT_BOLD; private set
    var BRAND_LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); private set
    /** Titres du design v2 : Manrope ExtraBold, interlettrage resserré. */
    var HEADING: Typeface = Typeface.DEFAULT_BOLD; private set

    /** Facteur d'échelle : 1 unité = 1 px sur un écran 1024×600. */
    var scale = 1f

    fun init(widthPx: Int, heightPx: Int) {
        val w = maxOf(widthPx, heightPx)
        val h = minOf(widthPx, heightPx)
        scale = minOf(w / 1024f, h / 600f)
    }

    fun loadFonts(c: Context) {
        fun font(name: String, fallback: Typeface) = try {
            Typeface.createFromAsset(c.assets, "fonts/$name.ttf")
        } catch (_: Exception) {
            fallback
        }
        // Manrope (licence SIL OFL, voir assets/fonts/OFL-Manrope.txt) : instances fixes tirées de la
        // police variable officielle, car Android 8 espace mal les lettres d'une police variable.
        SERIF = font("DMSerifDisplay-Regular", Typeface.SERIF)
        BRAND_BOLD = font("Poppins-Bold", Typeface.DEFAULT_BOLD)
        BRAND_LIGHT = font("Poppins-Light", Typeface.create("sans-serif-light", Typeface.NORMAL))
        LIGHT = font("Manrope-500", LIGHT)
        REGULAR = font("Manrope-500", REGULAR)
        MEDIUM = font("Manrope-700", MEDIUM)
        BOLD = font("Manrope-800", BOLD)
        HEADING = font("Manrope-800", BOLD)
    }

    /** Son d'appui, branché par MainActivity selon les réglages. */
    @JvmStatic
    var tapSound: () -> Unit = {}

    fun u(v: Float): Int = Math.round(v * scale)
    fun u(v: Int): Int = Math.round(v * scale)
    fun uf(v: Float): Float = v * scale

    fun round(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            setCornerRadius(uf(radius))
            if (strokeWidth > 0) setStroke(u(strokeWidth), strokeColor)
        }

    fun pressable(base: Drawable, radius: Float, ripple: Int = RIPPLE): Drawable =
        RippleDrawable(ColorStateList.valueOf(ripple), base, round(Color.WHITE, radius))

    /** Ombre douce sous une carte, en thème clair seulement (invisible et inutile sur fond sombre). */
    fun lift(v: View, level: Float = 2f) {
        if (!dark) v.elevation = uf(level)
    }

    fun text(c: Context, s: CharSequence = "", size: Float = 18f, color: Int = TEXT, face: Typeface = REGULAR): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_PX, uf(size))
            setTextColor(color)
            typeface = face
            includeFontPadding = false
        }

    /** Titre : Manrope ExtraBold (design v2). */
    fun title(c: Context, s: CharSequence = "", size: Float = 28f, color: Int = TEXT): TextView =
        text(c, s, size, color, HEADING).apply { includeFontPadding = true; letterSpacing = -0.02f }

    /** Petit intitulé en capitales espacées. */
    fun label(c: Context, s: String, color: Int = TEXT2, size: Float = 13f): TextView =
        text(c, s.uppercase(), size, color, MEDIUM).apply { letterSpacing = 0.1f }

    fun ellipsize(t: TextView, lines: Int = 1): TextView {
        t.maxLines = lines
        t.ellipsize = TextUtils.TruncateAt.END
        return t
    }

    fun vertical(c: Context, gravity: Int = Gravity.START): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setGravity(gravity)
    }

    fun horizontal(c: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setGravity(gravity)
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f,
           l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(u(l), u(t), u(r), u(b)) }

    fun flp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.MATCH_PARENT,
            gravity: Int = Gravity.NO_GRAVITY): FrameLayout.LayoutParams = FrameLayout.LayoutParams(w, h, gravity)

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun pad(v: View, all: Int): View {
        v.setPadding(u(all), u(all), u(all), u(all))
        return v
    }

    fun pad(v: View, h: Int, vert: Int): View {
        v.setPadding(u(h), u(vert), u(h), u(vert))
        return v
    }

    fun scroll(c: Context, child: View): ScrollView = ScrollView(c).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(child)
    }

    fun spacer(c: Context, w: Int = 0, h: Int = 0, weight: Float = 0f): View =
        View(c).apply { layoutParams = LinearLayout.LayoutParams(if (w == 0 && weight > 0) 0 else u(w), u(h), weight) }

    fun divider(c: Context): View = View(c).apply {
        background = ColorDrawable(STROKE)
        layoutParams = LinearLayout.LayoutParams(MATCH, maxOf(1, u(1)))
    }

    enum class Style { PRIMARY, SECONDARY, DANGER, GHOST, COOL, DOCK }

    /** Bouton en pilule avec icône facultative. */
    fun button(c: Context, label: String, style: Style = Style.SECONDARY, icon: Icon.Kind? = null,
               height: Int = 56, textSize: Float = 18f, iconAfter: Boolean = false, onClick: (View) -> Unit): LinearLayout {
        val (bg, fg) = when (style) {
            Style.PRIMARY -> INK to ON_INK
            Style.SECONDARY -> SURFACE2 to TEXT
            Style.DANGER -> DANGER to ON_DANGER
            Style.GHOST -> Color.TRANSPARENT to TEXT2
            Style.COOL -> COOL to ON_COOL
            Style.DOCK -> DOCK_SURFACE to DOCK_TEXT
        }
        val row = horizontal(c, Gravity.CENTER)
        row.minimumHeight = u(height)
        row.minimumWidth = u(height)
        row.setPadding(if (label.isEmpty()) 0 else u(20), 0, if (label.isEmpty()) 0 else u(20), 0)
        val base = if (style == Style.GHOST) round(Color.TRANSPARENT, height / 2f, STROKE, 1.5f) else round(bg, height / 2f)
        row.background = pressable(base, height / 2f)
        val iconView = icon?.let { Icon(c, it, fg) }
        val lpIcon = LinearLayout.LayoutParams(u(textSize * 1.25f), u(textSize * 1.25f))
        if (iconView != null && !iconAfter) {
            row.addView(iconView, lpIcon.apply { if (label.isNotEmpty()) rightMargin = u(8) })
        }
        if (label.isNotEmpty()) row.addView(text(c, label, textSize, fg, MEDIUM))
        if (iconView != null && iconAfter) {
            row.addView(iconView, lpIcon.apply { if (label.isNotEmpty()) leftMargin = u(8) })
        }
        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = label.ifEmpty { icon?.name }
        row.tap { onClick(it) }
        return row
    }

    /** Texte d'un bouton créé par [button]. */
    fun buttonText(b: LinearLayout): TextView? = (0 until b.childCount).map { b.getChildAt(it) }.firstOrNull { it is TextView } as? TextView

    /** Icône d'un bouton créé par [button]. */
    fun buttonIcon(b: LinearLayout): Icon? = (0 until b.childCount).map { b.getChildAt(it) }.firstOrNull { it is Icon } as? Icon

    fun setButtonEnabled(b: View, enabled: Boolean) {
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.38f
    }

    /** Pastille d'information (point ou icône + texte). */
    fun chip(c: Context, label: String, icon: Icon.Kind? = null, color: Int = TEXT2, bg: Int = SURFACE2, size: Float = 15f): LinearLayout {
        val row = horizontal(c, Gravity.CENTER_VERTICAL)
        row.setPadding(u(11), 0, u(13), 0)
        row.minimumHeight = u(size * 2.15f)
        row.background = round(bg, 40f)
        if (icon != null) row.addView(Icon(c, icon, color), LinearLayout.LayoutParams(u(size * 1.2f), u(size * 1.2f)).apply { rightMargin = u(6) })
        row.addView(text(c, label, size, color, MEDIUM))
        return row
    }

    fun chipText(chip: LinearLayout): TextView = chip.getChildAt(chip.childCount - 1) as TextView

    fun chipIcon(chip: LinearLayout): Icon? = chip.getChildAt(0) as? Icon

    /** Puce sélectionnable (filtres, choix) : pleine quand elle est choisie. */
    fun toggleChip(c: Context, label: String, selected: Boolean, icon: Icon.Kind? = null, idle: Int = SURFACE, onClick: () -> Unit): LinearLayout {
        val fg = if (selected) ON_INK else TEXT
        val row = horizontal(c, Gravity.CENTER)
        row.setPadding(u(16), 0, u(16), 0)
        row.minimumHeight = u(37)
        row.background = pressable(round(if (selected) INK else idle, 40f), 40f)
        if (icon != null) row.addView(Icon(c, icon, fg), LinearLayout.LayoutParams(u(15), u(15)).apply { rightMargin = u(7) })
        row.addView(text(c, label, 12f, fg, MEDIUM))
        row.isClickable = true
        row.tap { onClick() }
        return row
    }

    /** Choix exclusif dans une gouttière (Sens : Normal | Inverse). */
    fun segmented(c: Context, options: List<Pair<Icon.Kind?, String>>, selected: Int, height: Int = 46, onPick: (Int) -> Unit): LinearLayout {
        val box = horizontal(c)
        box.setPadding(u(4), u(4), u(4), u(4))
        box.background = round(SURFACE2, (height + 8) / 2f)
        for ((i, o) in options.withIndex()) {
            val on = i == selected
            val fg = if (on) TEXT else TEXT2
            val b = horizontal(c, Gravity.CENTER)
            b.setPadding(u(16), 0, u(16), 0)
            b.minimumHeight = u(height)
            b.background = if (on) round(SURFACE, height / 2f).also { } else pressable(round(0, height / 2f), height / 2f)
            if (on) lift(b, 1.5f)
            o.first?.let { b.addView(Icon(c, it, fg), LinearLayout.LayoutParams(u(19), u(19)).apply { if (o.second.isNotEmpty()) rightMargin = u(7) }) }
            if (o.second.isNotEmpty()) b.addView(text(c, o.second, 16f, fg, MEDIUM))
            b.isClickable = true
            b.tap { onPick(i) }
            box.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) leftMargin = u(3) })
        }
        return box
    }

    fun card(c: Context, radius: Float = 22f, color: Int = SURFACE): LinearLayout = vertical(c).apply {
        background = round(color, radius)
        setPadding(u(18), u(16), u(18), u(16))
        lift(this)
    }

    /** Pastille ronde colorée portant une icône (en tête de carte). */
    /**
     * Icône d'en-tête. Design v2 : l'icône seule, au trait fin, sans pastille de couleur ; la couleur
     * n'est gardée que pour les alertes (danger, avertissement).
     */
    fun badge(c: Context, icon: Icon.Kind, color: Int = ACCENT, soft: Int = ACCENT_SOFT, size: Int = 46): FrameLayout {
        val f = FrameLayout(c)
        val ink = if (color == DANGER || color == WARN) color else TEXT
        f.addView(Icon(c, icon, ink).apply { strokeWidth = 1.5f }, flp(u(size * 0.7f), u(size * 0.7f), Gravity.CENTER))
        f.layoutParams = LinearLayout.LayoutParams(u(size), u(size))
        return f
    }

    fun clock(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        else String.format("%02d:%02d", s / 60, s % 60)
    }

    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }

    /** Durée d'une recette en minutes : « 45 min », « 2 h 10 ». */
    fun minutes(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> String.format("%d h %02d", m / 60, m % 60)
    }

    /** Couleur liée à une température : tiède -> vert d'eau, chaud -> cuivre, très chaud -> rouge. */
    fun tempColor(c: Int): Int = when {
        c >= 100 -> DANGER
        c >= 60 -> ACCENT
        c >= 40 -> WARN
        else -> COOL
    }

    /** Couleur de repli stable pour une vignette de recette sans photo. */
    /** Origine d'une recette et sa couleur : Lidl (encre), Cookomix (bleu), Thermomix, Mes recettes (vert). */
    fun origin(id: String, source: app.toque.core.recipes.RecipeSource): Pair<String, Int> = when {
        id.startsWith(app.toque.core.recipes.Cookomix.ID_PREFIX) -> "Cookomix" to COOL
        id.startsWith(app.toque.core.recipes.Cookidoo.ID_PREFIX) -> "Thermomix" to TEXT2
        source == app.toque.core.recipes.RecipeSource.USER -> app.toque.core.i18n.tr("Mes recettes") to OK
        else -> "Lidl" to TEXT
    }

    /** Icône vectorielle au trait du design v2 (res/drawable), recolorée. */
    fun vector(c: Context, res: Int, color: Int = TEXT): android.widget.ImageView = android.widget.ImageView(c).apply {
        val d = c.resources.getDrawable(res, null).mutate()
        d.setTint(color)
        setImageDrawable(d)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    }

    fun tintVector(v: android.widget.ImageView, color: Int) { v.drawable?.setTint(color) }

    private val bowlCache = HashMap<String, android.graphics.Bitmap>()

    /** Rendu 3D du bol (assets/bol/<état>.png) : vide, chauffe, couvercle, sans-gobelet, vapeur, petrir… */
    fun bowlBitmap(c: Context, state: String): android.graphics.Bitmap? = bowlCache[state] ?: try {
        c.assets.open("bol/$state.png").use { android.graphics.BitmapFactory.decodeStream(it) }?.also { bowlCache[state] = it }
    } catch (_: Exception) { null }

    fun bowl(c: Context, state: String): android.widget.ImageView = android.widget.ImageView(c).apply {
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        setImageBitmap(bowlBitmap(c, state))
    }

    fun placeholderColors(seed: String): IntArray {
        val palette = if (dark) intArrayOf(
            0xFF4A3424.toInt(), 0xFF3F3A26.toInt(), 0xFF2E3A2C.toInt(), 0xFF47302B.toInt(),
            0xFF3E3324.toInt(), 0xFF2C3532.toInt(), 0xFF4A3B22.toInt(), 0xFF41302A.toInt(),
        ) else intArrayOf(
            0xFFE9C9A8.toInt(), 0xFFD7B98F.toInt(), 0xFFCFD9B8.toInt(), 0xFFE8BFAE.toInt(),
            0xFFD9C6A5.toInt(), 0xFFC9D5C4.toInt(), 0xFFE6CFA0.toInt(), 0xFFDDB9A4.toInt(),
        )
        val i = Math.abs(seed.hashCode()) % palette.size
        return intArrayOf(palette[i], palette[(i + 3) % palette.size])
    }

    /** Encre posée sur une vignette sans photo. */
    val placeholderInk: Int get() = if (dark) 0x38FFEBD2 else 0x59462D19

    fun initials(title: String): String {
        val words = title.split(' ', '-', '\'').filter { it.length > 2 }
        return words.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifEmpty { title.take(1).uppercase() }
    }

    fun gradient(colors: IntArray, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { setCornerRadius(uf(radius)) }

    /** Couleur plus sombre (f < 1) ou plus claire (f > 1). */
    fun shade(color: Int, f: Float): Int {
        val a = Color.alpha(color)
        fun ch(v: Int) = if (f <= 1f) (v * f).toInt().coerceIn(0, 255) else (v + (255 - v) * (f - 1f)).toInt().coerceIn(0, 255)
        return Color.argb(a, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    /** Luminance relative approximative, 0..1. */
    fun luminance(color: Int): Float = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f

    fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
}

/** Comme setOnClickListener, avec le son d'appui. */
fun View.tap(action: (View) -> Unit) = setOnClickListener { Ui.tapSound(); action(it) }
