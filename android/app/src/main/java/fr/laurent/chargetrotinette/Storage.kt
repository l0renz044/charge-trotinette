package fr.laurent.chargetrotinette

import android.content.Context
import org.json.JSONObject

/** Enregistrement local : réglages et dernière commande envoyée à la prise. */
class Storage(context: Context) {

    private val prefs = context.getSharedPreferences("charge_trotinette", Context.MODE_PRIVATE)

    private fun str(key: String, default: String): String = prefs.getString(key, null) ?: default
    private fun dbl(key: String, default: Double): Double = prefs.getString(key, null)?.toDoubleOrNull() ?: default
    private fun int(key: String, default: Int): Int = prefs.getString(key, null)?.toIntOrNull() ?: default

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            plugHost = str("plugHost", d.plugHost),
            scooterMac = str("scooterMac", d.scooterMac),
            targetPercent = int("targetPercent", d.targetPercent),
            chargerAmps = dbl("chargerAmps", d.chargerAmps),
            batteryAh = dbl("batteryAh", d.batteryAh),
            coefficient = dbl("coefficient", d.coefficient),
            coolingMinutes = int("coolingMinutes", d.coolingMinutes),
            maxHours = dbl("maxHours", d.maxHours),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("plugHost", s.plugHost)
            .putString("scooterMac", s.scooterMac)
            .putString("targetPercent", s.targetPercent.toString())
            .putString("chargerAmps", s.chargerAmps.toString())
            .putString("batteryAh", s.batteryAh.toString())
            .putString("coefficient", s.coefficient.toString())
            .putString("coolingMinutes", s.coolingMinutes.toString())
            .putString("maxHours", s.maxHours.toString())
            .apply()
    }

    fun loadLastCommand(): LastCommand? {
        val raw = prefs.getString("lastCommand", null) ?: return null
        return try {
            LastCommand.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastCommand(c: LastCommand) {
        prefs.edit().putString("lastCommand", c.toJson().toString()).apply()
    }
}
