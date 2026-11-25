package app.toque.ui.screens

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.recipes.PhotoReceiver
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.QrView
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap
import java.io.File

/**
 * Éditeur de recette perso (design v2) : titre, photo envoyée depuis le téléphone, ingrédients
 * (une ligne par ingrédient), étapes avec leur consigne et leur réglage machine. Les réglages sont
 * vérifiés par les règles de sécurité du robot ([StepMachine.violations]) avant l'enregistrement.
 */
class EditorScreen(a: MainActivity, private val original: Recipe? = null) : Screen(a) {
    override val title = original?.title ?: tr("Nouvelle recette")
    override val section = Section.RECIPES
    override val showTopBar = false

    private val id = original?.id ?: ("perso-" + System.currentTimeMillis().toString(36))
    private val steps: MutableList<Step> = original?.steps?.toMutableList() ?: mutableListOf(Step(""))
    private var selected = 0
    private var showIngredients = false
    private var imageName = original?.image?.takeIf { it.isNotEmpty() } ?: ""

    private lateinit var titleInput: EditText
    private lateinit var stepList: LinearLayout
    private lateinit var panel: FrameLayout
    private lateinit var thumb: ImageView
    private lateinit var ingredientsInput: EditText
    private lateinit var ingButton: LinearLayout
    private var receiver: PhotoReceiver? = null

    private enum class Mode(val label: String, val program: StepProgram?) {
        MANUAL(tr("Manuel"), StepProgram.MANUAL), KNEAD(tr("Malaxer"), StepProgram.KNEAD), STEAM(tr("Vapeur"), StepProgram.STEAM),
        TOAST(tr("Saisir"), StepProgram.TOAST), FERMENT(tr("Fermenter"), StepProgram.FERMENT), TURBO("Turbo", StepProgram.TURBO), NONE(tr("Sans machine"), null)
    }

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(19), Ui.u(16), Ui.u(19), Ui.u(4))

        // En-tête : fermer, titre modifiable, ingrédients, enregistrer
        val head = Ui.horizontal(c)
        head.addView(round(Icon.Kind.CLOSE, tr("Fermer")) { a.back() })
        val t = Ui.vertical(c)
        t.addView(Ui.text(c, tr("Mes recettes").uppercase(), 10.5f, Ui.OK, Ui.HEADING).apply { letterSpacing = 0.06f })
        titleInput = EditText(c).apply {
            background = null
            setPadding(0, 0, 0, 0)
            setText(original?.title.orEmpty())
            hint = tr("Nom de la recette")
            setHintTextColor(Ui.TEXT3)
            setTextColor(Ui.TEXT)
            typeface = Ui.HEADING
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(21f))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        t.addView(titleInput, Ui.lp(t = 2))
        head.addView(t, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(13) })
        ingButton = Ui.button(c, tr("Ingrédients"), Ui.Style.SECONDARY, height = 45, textSize = 13f) { showIngredients = !showIngredients; refreshPanel() }
        head.addView(ingButton, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        head.addView(Ui.button(c, tr("Enregistrer"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 45, textSize = 13f) { save() })
        root.addView(head)

        val main = Ui.horizontal(c, Gravity.TOP)
        // Colonne gauche : photo, étapes
        val left = Ui.vertical(c)
        val photo = Ui.horizontal(c)
        photo.setPadding(Ui.u(8), Ui.u(8), Ui.u(12), Ui.u(8))
        photo.background = Ui.pressable(Ui.round(Ui.SURFACE, 18f), 18f)
        val tb = FrameLayout(c)
        tb.background = Ui.round(Ui.SURFACE2, 13f)
        tb.clipToOutline = true
        tb.addView(Icon(c, Icon.Kind.PLUS, Ui.TEXT2).apply { strokeWidth = 1.8f }, Ui.flp(Ui.u(22), Ui.u(22), Gravity.CENTER))
        thumb = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        tb.addView(thumb, Ui.flp())
        photo.addView(tb, LinearLayout.LayoutParams(Ui.u(70), Ui.u(70)))
        val pt = Ui.vertical(c)
        pt.addView(Ui.text(c, tr("Ajouter une photo"), 13f, Ui.TEXT, Ui.HEADING))
        pt.addView(Ui.text(c, tr("Scannez le QR code avec le téléphone, la photo arrive sur le robot."), 10.5f, Ui.TEXT2, Ui.MEDIUM)
            .apply { setLineSpacing(0f, 1.25f) }, Ui.lp(t = 3))
        photo.addView(pt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(11) })
        photo.isClickable = true
        photo.tap { photoDialog() }
        left.addView(photo)
        stepList = Ui.vertical(c)
        left.addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; addView(stepList) }, Ui.lp(Ui.MATCH, 0, 1f, t = 8))
        val add = Ui.text(c, "+ " + tr("Ajouter une étape"), 12f, Ui.TEXT2, Ui.HEADING).apply { gravity = Gravity.CENTER }
        add.background = Ui.pressable(Ui.round(0, 18f, Ui.SURFACE3, 1.6f), 18f)
        add.isClickable = true
        add.tap { commitText(); steps.add(selected + 1, Step("")); selected++; showIngredients = false; refresh() }
        left.addView(add, Ui.lp(Ui.MATCH, Ui.u(46), t = 8))
        main.addView(left, LinearLayout.LayoutParams(Ui.u(272), Ui.MATCH).apply { rightMargin = Ui.u(13) })

        panel = FrameLayout(c)
        panel.background = Ui.round(Ui.SURFACE, 22f)
        main.addView(panel, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        root.addView(main, Ui.lp(Ui.MATCH, 0, 1f, t = 13))

        ingredientsInput = EditText(c).apply {
            background = Ui.round(Ui.BG, 14f)
            setPadding(Ui.u(14), Ui.u(12), Ui.u(14), Ui.u(12))
            gravity = Gravity.TOP
            setTextColor(Ui.TEXT)
            typeface = Ui.REGULAR
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(13f))
            setLineSpacing(0f, 1.3f)
            hint = tr("Une ligne par ingrédient, par exemple :\n500 g farine\n1 c. à café sel")
            setHintTextColor(Ui.TEXT3)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(original?.ingredients?.flatMap { it.items }?.joinToString("\n") { it.display }.orEmpty())
        }
        refresh()
        loadThumb()
        return root
    }

    private fun round(icon: Icon.Kind, label: String, onClick: () -> Unit): FrameLayout {
        val b = FrameLayout(c)
        b.background = Ui.pressable(Ui.round(Ui.SURFACE, 22.5f), 22.5f)
        b.addView(Icon(c, icon, Ui.TEXT).apply { strokeWidth = 2.4f }, Ui.flp(Ui.u(16), Ui.u(16), Gravity.CENTER))
        b.layoutParams = LinearLayout.LayoutParams(Ui.u(45), Ui.u(45))
        b.isClickable = true
        b.contentDescription = label
        b.tap { onClick() }
        return b
    }

    // ------------------------------------------------------------------ étapes

    private fun refresh() {
        stepList.removeAllViews()
        for ((i, s) in steps.withIndex()) {
            val on = i == selected && !showIngredients
            val row = Ui.horizontal(c, Gravity.TOP)
            row.setPadding(Ui.u(13), Ui.u(12), Ui.u(13), Ui.u(12))
            row.background = Ui.pressable(Ui.round(if (on) Ui.INK else Ui.SURFACE, 18f), 18f)
            val n = Ui.text(c, "${i + 1}", 10.5f, Ui.TEXT, Ui.HEADING).apply { gravity = Gravity.CENTER }
            n.background = Ui.round(if (on) Ui.SURFACE else Ui.SURFACE2, 11f)
            row.addView(n, LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)))
            val col = Ui.vertical(c)
            col.addView(Ui.ellipsize(Ui.text(c, s.text.ifBlank { tr("Nouvelle étape") }, 12f, if (on) Ui.ON_INK else Ui.TEXT, Ui.MEDIUM), 2))
            val chip = s.machine?.summary ?: tr("Sans machine")
            col.addView(Ui.ellipsize(Ui.text(c, chip, 10.5f, if (on) Ui.DOCK_TEXT2 else Ui.TEXT2, Ui.HEADING)), Ui.lp(t = 3))
            row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(10) })
            row.isClickable = true
            row.tap { commitText(); selected = i; showIngredients = false; refresh() }
            stepList.addView(row, Ui.lp(t = if (i > 0) 7 else 0))
        }
        refreshPanel()
    }

    private var stepInput: EditText? = null

    /** Garde le texte de la consigne en cours dans la liste des étapes. */
    private fun commitText() {
        val e = stepInput ?: return
        val i = selected.coerceIn(0, steps.size - 1)
        if (steps.isNotEmpty() && e.tag == i) steps[i] = steps[i].copy(text = e.text.toString().trim())
    }

    private fun refreshPanel() {
        panel.removeAllViews()
        stepInput = null
        ingButton.background = Ui.pressable(Ui.round(if (showIngredients) Ui.INK else Ui.SURFACE2, 22.5f), 22.5f)
        (ingButton.getChildAt(ingButton.childCount - 1) as? TextView)?.setTextColor(if (showIngredients) Ui.ON_INK else Ui.TEXT)
        val box = Ui.vertical(c)
        box.setPadding(Ui.u(22), Ui.u(19), Ui.u(22), Ui.u(19))
        if (showIngredients) {
            box.addView(label(tr("Ingrédients")))
            (ingredientsInput.parent as? LinearLayout)?.removeView(ingredientsInput)
            box.addView(ingredientsInput, Ui.lp(Ui.MATCH, 0, 1f, t = 8))
            panel.addView(box, Ui.flp())
            return
        }
        if (steps.isEmpty()) steps.add(Step(""))
        selected = selected.coerceIn(0, steps.size - 1)
        val s = steps[selected]
        box.addView(label(tr("Étape %d · Consigne", selected + 1)))
        val input = EditText(c).apply {
            tag = selected
            background = Ui.round(Ui.BG, 14f)
            setPadding(Ui.u(14), Ui.u(10), Ui.u(14), Ui.u(10))
            setText(s.text)
            hint = tr("Ce qu'il faut faire, par exemple « Ajouter la farine »")
            setHintTextColor(Ui.TEXT3)
            setTextColor(Ui.TEXT)
            typeface = Ui.MEDIUM
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(13.5f))
            maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(x: CharSequence?, a1: Int, b: Int, d: Int) {}
                override fun onTextChanged(x: CharSequence?, a1: Int, b: Int, d: Int) {}
                override fun afterTextChanged(x: Editable?) {
                    val row = stepList.getChildAt(selected) as? LinearLayout ?: return
                    ((row.getChildAt(1) as? LinearLayout)?.getChildAt(0) as? TextView)?.text = x?.toString()?.ifBlank { tr("Nouvelle étape") }
                }
            })
        }
        stepInput = input
        box.addView(input, Ui.lp(Ui.MATCH, Ui.WRAP, t = 6))

        // Mode
        val mode = modeOf(s.machine)
        val modes = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in Mode.values()) {
            modes.addView(Ui.toggleChip(c, m.label, m == mode, idle = Ui.SURFACE2) { commitText(); setMode(m) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 6))
        }
        box.addView(android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(modes) }, Ui.lp(t = 13))

        val m = s.machine
        if (m != null) {
            when (m.program) {
                StepProgram.TURBO -> stepper(box, tr("Impulsions"), m.pulses.coerceAtLeast(1).toString()) { d -> update(m.copy(pulses = (m.pulses + d).coerceIn(1, 10))) }
                else -> {
                    stepper(box, tr("Durée"), Cookidoo.fmt(m.seconds.coerceAtLeast(1))) { d -> update(m.copy(seconds = bumpSeconds(m.seconds, d))) }
                    if (m.program == StepProgram.MANUAL || m.program == StepProgram.FERMENT || m.program == StepProgram.TOAST) {
                        val table = TemperatureTable.current
                        val level = if (m.tempC <= 0) 0 else table.levelAtMost(m.tempC)
                        stepper(box, tr("Température"), if (m.tempC <= 0) tr("Sans chauffe") else "${table.celsius(level)} °C") { d ->
                            val nl = (level + d).coerceIn(0, table.maxLevel)
                            update(m.copy(tempC = if (nl == 0) 0 else table.celsius(nl)))
                        }
                    }
                    if (m.program == StepProgram.MANUAL) {
                        speeds(box, m)
                        val dir = Ui.segmented(c, listOf(null to tr("Sens normal"), Icon.Kind.REVERSE to tr("Sens inverse")), if (m.reverse) 1 else 0, height = 36) { k ->
                            commitText(); update(m.copy(reverse = k == 1))
                        }
                        box.addView(dir, Ui.lp(Ui.WRAP, Ui.WRAP, t = 12))
                    }
                }
            }
            val v = m.violations()
            if (v.isNotEmpty()) {
                val w = Ui.horizontal(c)
                w.setPadding(Ui.u(12), Ui.u(9), Ui.u(12), Ui.u(9))
                w.background = Ui.round(Ui.WARN_SOFT, 13f)
                w.addView(Icon(c, Icon.Kind.WARNING, Ui.WARN), LinearLayout.LayoutParams(Ui.u(16), Ui.u(16)).apply { rightMargin = Ui.u(8) })
                w.addView(Ui.text(c, v.joinToString(" · ") { it.message }, 11.5f, Ui.WARN, Ui.MEDIUM), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                box.addView(w, Ui.lp(t = 12))
            }
        }
        box.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        if (steps.size > 1) box.addView(Ui.button(c, tr("Supprimer l'étape"), Ui.Style.GHOST, Icon.Kind.CLOSE, height = 40, textSize = 12f) {
            steps.removeAt(selected); selected = (selected - 1).coerceAtLeast(0); refresh()
        }, Ui.lp(Ui.WRAP, Ui.WRAP))
        panel.addView(ScrollView(c).apply { isFillViewport = true; addView(box) }, Ui.flp())
    }

    private fun label(t: String) = Ui.text(c, t.uppercase(), 10.5f, Ui.TEXT2, Ui.HEADING).apply { letterSpacing = 0.06f }

    private fun stepper(box: LinearLayout, title: String, value: String, bump: (Int) -> Unit) {
        val row = Ui.horizontal(c)
        row.setPadding(Ui.u(14), Ui.u(5), Ui.u(5), Ui.u(5))
        row.background = Ui.round(Ui.BG, 14f)
        row.addView(Ui.text(c, title, 12.5f, Ui.TEXT, Ui.HEADING), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 34) { commitText(); bump(-1) })
        row.addView(Ui.text(c, value, 13.5f, Ui.TEXT, Ui.HEADING).apply { gravity = Gravity.CENTER; minWidth = Ui.u(96) })
        row.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 34) { commitText(); bump(1) })
        box.addView(row, Ui.lp(t = 12))
    }

    /** Vitesse de 0 à 10 en pastilles bleues (bleu = vitesse, comme la cuisson manuelle). */
    private fun speeds(box: LinearLayout, m: StepMachine) {
        val head = Ui.horizontal(c)
        head.addView(Ui.text(c, tr("Vitesse"), 12.5f, Ui.TEXT, Ui.HEADING), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.text(c, m.speed.toString(), 12.5f, Ui.COOL, Ui.HEADING))
        box.addView(head, Ui.lp(t = 13))
        val row = Ui.horizontal(c)
        for (i in 0..10) {
            val on = i <= m.speed
            val p = Ui.text(c, i.toString(), 11.5f, if (on) Ui.ON_COOL else Ui.COOL, Ui.HEADING).apply { gravity = Gravity.CENTER }
            p.background = Ui.round(if (on) Ui.COOL else Ui.COOL_SOFT, 15f)
            p.isClickable = true
            p.tap { commitText(); update(m.copy(speed = i)) }
            row.addView(p, LinearLayout.LayoutParams(0, Ui.u(32), 1f).apply { if (i > 0) leftMargin = Ui.u(4) })
        }
        box.addView(row, Ui.lp(t = 7))
    }

    private fun bumpSeconds(s: Int, d: Int): Int {
        val step = when {
            s + d < 60 || (s == 60 && d < 0) -> 5
            s + d * 30 <= 600 -> 30
            s + d * 60 <= 3600 -> 60
            else -> 300
        }
        return (s + d * step).coerceIn(1, 12 * 3600)
    }

    private fun modeOf(m: StepMachine?): Mode = when (m?.program) {
        null -> Mode.NONE
        StepProgram.KNEAD -> Mode.KNEAD
        StepProgram.STEAM -> Mode.STEAM
        StepProgram.TOAST -> Mode.TOAST
        StepProgram.FERMENT -> Mode.FERMENT
        StepProgram.TURBO -> Mode.TURBO
        else -> Mode.MANUAL
    }

    private fun setMode(m: Mode) {
        val s = steps[selected]
        val cur = s.machine
        val next = when (m) {
            Mode.NONE -> null
            Mode.TURBO -> StepMachine(StepProgram.TURBO, pulses = 2)
            Mode.MANUAL -> StepMachine(StepProgram.MANUAL, seconds = cur?.seconds?.takeIf { it > 0 } ?: 30, speed = cur?.speed?.takeIf { it > 0 } ?: 3)
            Mode.KNEAD -> StepMachine(StepProgram.KNEAD, seconds = 120)
            Mode.STEAM -> StepMachine(StepProgram.STEAM, seconds = 1200, tempC = 120)
            Mode.TOAST -> StepMachine(StepProgram.TOAST, seconds = 420)
            Mode.FERMENT -> StepMachine(StepProgram.FERMENT, seconds = 3600, tempC = 37)
        }
        steps[selected] = s.copy(machine = next, needsReview = false)
        refresh()
    }

    private fun update(m: StepMachine) {
        steps[selected] = steps[selected].copy(machine = m, needsReview = false)
        refresh()
    }

    // ------------------------------------------------------------------ photo

    private fun photoFile(): File = File(a.app.recipes.imagesDir, "$id.jpg")

    private fun loadThumb() {
        val f = if (imageName.isNotEmpty()) a.app.recipes.imageFile(imageName) else null
        if (f != null && f.exists()) ImageLoader.load(thumb, f, Ui.u(160)) else thumb.setImageDrawable(null)
    }

    private fun photoDialog() {
        commitText()
        val card = Ui.vertical(c)
        card.setPadding(Ui.u(26), Ui.u(22), Ui.u(26), Ui.u(22))
        card.background = Ui.round(Ui.SURFACE, 24f)
        card.addView(Ui.text(c, tr("Photo depuis le téléphone"), 20f, Ui.TEXT, Ui.HEADING))
        val r = receiver ?: PhotoReceiver(photoFile()) { f ->
            imageName = f.name
            ImageLoader.forget(f)
            loadThumb()
            a.closeOverlay()
            a.toast(tr("Photo reçue"), Ui.OK)
        }.also { receiver = it }
        val url = try { if (r.url.isEmpty()) r.start() else r.url } catch (_: Exception) { null }
        if (url == null) {
            card.addView(Ui.text(c, tr("Le robot n'est pas connecté au Wi-Fi."), 13f, Ui.DANGER, Ui.MEDIUM), Ui.lp(t = 10))
        } else {
            // Le QR code à gauche, les explications à droite.
            val row = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
            val qr = QrView(c, url)
            qr.background = Ui.round(android.graphics.Color.WHITE, 14f)
            row.addView(qr, LinearLayout.LayoutParams(Ui.u(190), Ui.u(190)))
            val txt = Ui.vertical(c)
            txt.addView(Ui.text(c, tr("Scannez ce code avec l'appareil photo du téléphone (même Wi-Fi que le robot), prenez le plat en photo et touchez « Envoyer au robot ». La photo apparaît ici aussitôt."), 13f, Ui.TEXT2, Ui.MEDIUM)
                .apply { setLineSpacing(0f, 1.35f) })
            txt.addView(Ui.text(c, tr("Sans QR code : ouvrez %s", url.removePrefix("http://")), 11.5f, Ui.TEXT3, Ui.MEDIUM).apply { setTextIsSelectable(true) }, Ui.lp(t = 10))
            row.addView(txt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(18) })
            card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, t = 14))
        }
        card.addView(Ui.button(c, tr("Fermer"), Ui.Style.SECONDARY, height = 46, textSize = 13f) { a.closeOverlay() }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 16))
        a.showOverlay(card, 600)
    }

    // ------------------------------------------------------------------ enregistrement

    private fun save() {
        commitText()
        val name = titleInput.text.toString().trim()
        if (name.isEmpty()) { a.toast(tr("Donnez un nom à la recette"), Ui.WARN); titleInput.requestFocus(); return }
        val ingredients = ingredientsInput.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { Cookidoo.ingredient(it) }
        val cleanSteps = steps.filter { it.text.isNotBlank() || it.machine != null }
        val bad = cleanSteps.indexOfFirst { it.machine?.valid == false }
        if (bad >= 0) {
            selected = steps.indexOf(cleanSteps[bad]); showIngredients = false; refresh()
            a.toast(tr("Étape %d : réglage refusé par le robot, corrigez-le", selected + 1), Ui.WARN)
            return
        }
        val machineSeconds = cleanSteps.sumOf { it.machine?.seconds ?: 0 }
        val base = original ?: Recipe(id = id, title = name, source = RecipeSource.USER)
        val r = base.copy(
            title = name,
            ingredients = if (ingredients.isEmpty()) emptyList() else listOf(IngredientGroup("", ingredients)),
            steps = cleanSteps,
            source = RecipeSource.USER,
            image = imageName,
            totalMinutes = base.totalMinutes.takeIf { it > 0 } ?: ((machineSeconds + 59) / 60),
        )
        // upsertAll attend le fil des recettes : on l'appelle depuis un fil à part.
        Thread {
            a.app.recipes.upsertAll(listOf(r))
            view.post {
                a.toast(tr("« %s » enregistrée", name), Ui.OK)
                a.back()
                val fresh = a.app.recipes.get(r.id)
                if (original != null) { a.back(); if (fresh != null) a.push(RecipeScreen(a, fresh)) }
                else if (fresh != null) a.push(RecipeScreen(a, fresh))
            }
        }.start()
    }

    override fun onHide() {
        receiver?.stop()
        receiver = null
    }
}
