package app.toque.core.smart

import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.Phase
import app.toque.core.machine.Setting
import app.toque.core.machine.TemperatureTable
import app.toque.core.machine.Violation
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcs
import app.toque.core.protocol.Operation

/** Mouvement du couteau à un instant d'un programme : vitesse affichée 0..10 et sens. */
data class SmartMotion(val speed: Int, val reverse: Boolean)

/**
 * Programmes propres au Monsieur Cuisine Smart, reproduits d'après l'appli d'origine 2.20 :
 * écoute guidée du 21/11/2026 (trames exactes) et générateurs `s5.*` / tables `y2.*` (rythmes).
 *
 * Température : certains programmes envoient le niveau de la température et la température exacte
 * (octets 3 et 4), d'autres le niveau 1 et la température exacte ; c'est la base qui régule.
 * L'utilisateur ne choisit que la durée et, quand le programme le permet, la température.
 */
enum class SmartProgram(
    val key: String,
    private val fr: String,
    private val descriptionFr: String,
    val function: Int,
    private val heat: Heat,
    val defaultC: Int, val minC: Int, val maxC: Int, val stepC: Int,
    val defaultS: Int, val minS: Int, val maxS: Int, val stepS: Int,
) {
    /** « Mixer » de l'appli : fonction 12, vitesse 8. */
    PUREE("mixer", trKey("Mixer"), trKey("Purées, soupes mixées, compotes. Vitesse 8, sans chauffe."),
        Mcs.FN_PUREE, Heat.NONE, 0, 0, 0, 0, 30, 5, 180, 5),
    SMOOTHIE("smoothie", trKey("Smoothie"), trKey("Fruits et glace pilée. Vitesse 10, sans chauffe."),
        Mcs.FN_SMOOTHIE, Heat.NONE, 0, 0, 0, 0, 60, 10, 180, 5),
    KETTLE("bouilloire", trKey("Chauffer l'eau"), trKey("Fait chauffer de l'eau en remuant doucement en sens inverse."),
        Mcs.FN_STEAM, Heat.LEVEL_AND_EXACT, 100, 40, 100, 5, 900, 60, 1800, 60),
    SLOW_COOK("mijoter", trKey("Mijoter"), trKey("Cuisson lente. Remue 30 s toutes les 2 min 30 pendant 10 min, puis sans remuer."),
        Mcs.FN_SLOW_COOK, Heat.LEVEL1_AND_EXACT, 90, 60, 100, 5, 7200, 60, 28800, 300),
    SOUS_VIDE("sous-vide", trKey("Sous-vide"), trKey("Bain d'eau à température précise. Le minuteur part une fois la température atteinte."),
        Mcs.FN_SOUS_VIDE, Heat.LEVEL1_AND_EXACT, 60, 40, 85, 1, 3600, 60, 43200, 300),
    FERMENT("fermenter", trKey("Fermenter"), trKey("Yaourts, pâtes levées. Remue 5 min au début, puis maintient la température."),
        Mcs.FN_FERMENTATION, Heat.LEVEL1_AND_EXACT, 40, 30, 50, 1, 28800, 60, 43200, 900),
    RICE("riz", trKey("Cuire du riz"), trKey("Riz à 100 °C en remuant doucement en sens inverse."),
        Mcs.FN_MANUAL, Heat.LEVEL_AND_EXACT, 100, 100, 100, 0, 2400, 60, 3600, 60),
    EGGS("oeufs", trKey("Cuire des œufs"), trKey("Œufs dans le panier vapeur, eau dans le bol. Choisissez la durée selon la cuisson."),
        Mcs.FN_EGG, Heat.LEVEL1_AND_EXACT, 130, 130, 130, 0, 480, 300, 1200, 60),
    /** « Prélaver » rapide : 30 s vitesse 10, 30 s vitesse 4 en sens inverse, 30 s vitesse 10, à 37 °C. */
    CLEANING("prelavage", trKey("Prélaver"), trKey("Nettoyage rapide du bol avec de l'eau : 1 min 30 à 37 °C."),
        Mcs.FN_SHORT_CLEANING, Heat.FIXED_37, 37, 37, 37, 0, 90, 90, 90, 0);

    private enum class Heat { NONE, LEVEL_AND_EXACT, LEVEL1_AND_EXACT, FIXED_37 }

    val label: String get() = tr(fr)
    val description: String get() = tr(descriptionFr)
    val heated: Boolean get() = heat != Heat.NONE
    val adjustableTemperature: Boolean get() = heated && minC < maxC
    val adjustableDuration: Boolean get() = minS < maxS

    /** Sous-vide : le minuteur ne part qu'une fois la température atteinte (compte à rebours 3600 avant). */
    val preheat: Boolean get() = this == SOUS_VIDE

    /** Mouvement à [elapsedMs] depuis le début du programme (hors préchauffage). */
    fun motion(elapsedMs: Long): SmartMotion {
        val s = elapsedMs / 1000
        return when (this) {
            PUREE -> SmartMotion(8, false)
            SMOOTHIE -> SmartMotion(10, false)
            KETTLE, RICE, SOUS_VIDE -> SmartMotion(1, true)
            SLOW_COOK -> if (s < 600 && s % 150 < 30) SmartMotion(1, true) else SmartMotion(0, true)
            FERMENT -> if (s < 300) SmartMotion(1, true) else SmartMotion(0, true)
            EGGS -> SmartMotion(1, false)
            CLEANING -> when {
                s < 30 -> SmartMotion(10, false)
                s < 60 -> SmartMotion(4, true)
                else -> SmartMotion(10, false)
            }
        }
    }

    /** Niveau de température envoyé à la base. */
    fun wireLevel(celsius: Int): Int = when (heat) {
        Heat.NONE -> 0
        Heat.LEVEL_AND_EXACT -> TemperatureTable.SMART.levelAtMost(celsius)
        Heat.LEVEL1_AND_EXACT, Heat.FIXED_37 -> 1
    }

    /** Température exacte envoyée à la base (0 : aucune). */
    fun wireExact(celsius: Int): Int = when (heat) {
        Heat.LEVEL_AND_EXACT, Heat.LEVEL1_AND_EXACT -> celsius
        Heat.NONE, Heat.FIXED_37 -> 0
    }

    /** Vitesse la plus élevée du programme (règle du contenu chaud). */
    val maxSpeed: Int get() = when (this) {
        PUREE -> 8
        SMOOTHIE, CLEANING -> 10
        else -> 1
    }

    fun clampTemperature(celsius: Int): Int {
        if (!heated) return 0
        if (minC == maxC) return minC
        val c = celsius.coerceIn(minC, maxC)
        return minC + (c - minC) / stepC * stepC
    }

    /** Problèmes d'un réglage, indépendamment de l'état de la machine. */
    fun check(durationMs: Long, celsius: Int): List<Violation> {
        val v = ArrayList<Violation>(2)
        if (durationMs < minS * 1000L || durationMs > maxS * 1000L)
            v += Violation("program_duration", tr("%s : durée entre %s et %s", label, fmt(minS), fmt(maxS)))
        if (heated && (celsius < minC || celsius > maxC || (stepC > 0 && (celsius - minC) % stepC != 0)))
            v += Violation("program_temp", tr("%s : température entre %d et %d °C", label, minC, maxC))
        if (heat == Heat.LEVEL_AND_EXACT && TemperatureTable.SMART.levelFor(celsius) < 0)
            v += Violation("program_temp", tr("Température absente de la table du Smart"))
        return v
    }

    /** Travail à envoyer à la machine (Smart uniquement). La phase sert à l'affichage. */
    fun job(durationMs: Long, celsius: Int = defaultC, label: String = this.label): JobSpec {
        val t = if (heated) celsius else 0
        val first = motion(0)
        return JobSpec(
            kind = JobKind.MANUAL,
            label = label,
            phases = listOf(Phase(
                Setting(Operation.STANDARD, first.speed, TemperatureTable.SMART.levelAtMost(t),
                    if (first.reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE),
                durationMs,
            )),
            totalMs = durationMs,
            cyclic = false,
            smartProgram = key,
            exactTemperatureC = t,
        )
    }

    companion object {
        fun of(key: String?): SmartProgram? = if (key.isNullOrEmpty()) null else values().firstOrNull { it.key == key }

        /** Le robot piloté depuis ce processus est un Smart (seul robot qui a ces programmes). */
        val available: Boolean get() = TemperatureTable.current === TemperatureTable.SMART

        private fun fmt(s: Int): String = when {
            s < 60 -> "$s s"
            s % 3600 == 0 -> "${s / 3600} h"
            s >= 3600 -> "${s / 3600} h ${s % 3600 / 60} min"
            else -> if (s % 60 == 0) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        }
    }
}
