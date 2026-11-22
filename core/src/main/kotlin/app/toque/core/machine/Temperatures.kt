package app.toque.core.machine

import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Mcs

/**
 * Table des températures d'un robot : niveau → °C. Le niveau est ce que portent [Setting] et
 * [JobSpec] ; sa signification dépend du robot piloté.
 *  - Connect : 0, 37, 45 … 130 °C (niveaux 0..19) ;
 *  - Smart : 0, 37, 40, 45 … 130 °C (niveaux 0..20).
 * Les recettes sont en °C et sont converties avec la table du robot.
 */
class TemperatureTable(private val levels: IntArray) {
    val maxLevel: Int get() = levels.size - 1

    fun celsius(level: Int): Int = levels[level.coerceIn(0, maxLevel)]

    /** Niveau exact d'une température de la table, sinon -1. */
    fun levelFor(celsius: Int): Int = levels.indexOf(celsius)

    /** Niveau le plus proche sans dépasser la température demandée (0 sous 37 °C). */
    fun levelAtMost(celsius: Int): Int {
        var best = 0
        for (i in levels.indices) if (levels[i] <= celsius) best = i
        return best
    }

    /** Niveaux avec chauffe, du plus froid au plus chaud. */
    val heatedLevels: IntArray get() = IntArray(maxLevel) { it + 1 }

    companion object {
        val CONNECT = TemperatureTable(Mcc.TEMPERATURE_LEVELS)
        val SMART = TemperatureTable(Mcs.TEMPERATURE_LEVELS)

        /**
         * Table du robot piloté depuis ce processus, choisie au démarrage et à chaque changement de
         * mode. Interface et recettes s'en servent ; les cœurs de pilotage utilisent la leur.
         */
        @Volatile var current: TemperatureTable = CONNECT
    }
}
