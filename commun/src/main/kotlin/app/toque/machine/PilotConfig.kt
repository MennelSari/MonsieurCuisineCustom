package app.toque.machine

import android.content.Context
import android.os.Bundle

/**
 * Réglages de pilotage envoyés par l'interface au pilote (qui n'a pas accès aux réglages de
 * l'interface : ce sont deux applis). Le pilote garde les derniers reçus pour redémarrer seul.
 */
data class PilotConfig(
    /** « demo », « twin » ou « hardware ». */
    val mode: String = "demo",
    /** Clé de [app.toque.core.machine.RobotModel] (« connect », « smart »). */
    val model: String = "",
    val twinHost: String = "",
    val twinPort: Int = 0,
    val serialDevice: String = "",
    val vendorPackage: String = "",
    val requireBowl: Boolean = true,
    val hotRamp: Boolean = false,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString("mode", mode); putString("model", model); putString("twinHost", twinHost); putInt("twinPort", twinPort)
        putString("serialDevice", serialDevice); putString("vendorPackage", vendorPackage)
        putBoolean("requireBowl", requireBowl); putBoolean("hotRamp", hotRamp)
    }

    fun save(c: Context) {
        c.getSharedPreferences("pilote", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putString("model", model).putString("twinHost", twinHost).putInt("twinPort", twinPort)
            .putString("serialDevice", serialDevice).putString("vendorPackage", vendorPackage)
            .putBoolean("requireBowl", requireBowl).putBoolean("hotRamp", hotRamp).putBoolean("set", true).commit()
    }

    companion object {
        fun fromBundle(b: Bundle?): PilotConfig? {
            if (b == null || !b.containsKey("mode")) return null
            return PilotConfig(
                b.getString("mode") ?: "demo", b.getString("model") ?: "", b.getString("twinHost") ?: "", b.getInt("twinPort"),
                b.getString("serialDevice") ?: "", b.getString("vendorPackage") ?: "", b.getBoolean("requireBowl", true), b.getBoolean("hotRamp"),
            )
        }

        /** Derniers réglages reçus ; null si l'interface n'en a encore jamais envoyé. */
        fun load(c: Context): PilotConfig? {
            val p = c.getSharedPreferences("pilote", Context.MODE_PRIVATE)
            if (!p.getBoolean("set", false)) return null
            return PilotConfig(
                p.getString("mode", "demo") ?: "demo", p.getString("model", "") ?: "", p.getString("twinHost", "") ?: "", p.getInt("twinPort", 0),
                p.getString("serialDevice", "") ?: "", p.getString("vendorPackage", "") ?: "", p.getBoolean("requireBowl", true), p.getBoolean("hotRamp", false),
            )
        }
    }
}
