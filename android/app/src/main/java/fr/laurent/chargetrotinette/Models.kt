package fr.laurent.chargetrotinette

import org.json.JSONObject

/** Réglages enregistrés sur le téléphone. */
data class Settings(
    val plugHost: String = "192.168.1.23",
    val scooterMac: String = "F0:65:C0:46:26:95",
    val targetPercent: Int = 80,
    val chargerAmps: Double = 2.0,
    val batteryAh: Double = 15.0,
    val coefficient: Double = 1.0,
    val coolingMinutes: Int = 30,
    val maxHours: Double = 8.0,
)

enum class StartMode { NOW, READY_BY }

/** Données d'entrée du calcul de charge. */
data class PlanInput(
    val currentPercent: Int,
    val targetPercent: Int,
    val chargerAmps: Double,
    val batteryAh: Double,
    val coefficient: Double,
    val coolingMinutes: Int,
    val maxHours: Double,
    val batteryHot: Boolean,
    val mode: StartMode,
    val readyByHour: Int = 7,
    val readyByMinute: Int = 30,
)

/** Résultat du calcul : durée, heures de début et de fin. */
data class ChargePlan(
    val durationSec: Int,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val immediate: Boolean,
    val capped: Boolean,
    val tooTight: Boolean,
)

enum class CommandType { START_NOW, SCHEDULE, STOP, TEST }

/** Dernière commande envoyée à la prise, affichée à l'ouverture de l'appli. */
data class LastCommand(
    val type: CommandType,
    val sentAtMillis: Long,
    val startPercent: Int?,
    val targetPercent: Int?,
    val durationSec: Int?,
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?,
    /** true = confirmée par la prise, false = non confirmée, null = non vérifiable. */
    val verified: Boolean?,
    val note: String,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("type", type.name)
        o.put("sentAt", sentAtMillis)
        o.put("startPercent", startPercent)
        o.put("targetPercent", targetPercent)
        o.put("durationSec", durationSec)
        o.put("plannedStart", plannedStartMillis)
        o.put("plannedEnd", plannedEndMillis)
        o.put("verified", verified)
        o.put("note", note)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): LastCommand = LastCommand(
            type = CommandType.valueOf(o.getString("type")),
            sentAtMillis = o.getLong("sentAt"),
            startPercent = o.intOrNull("startPercent"),
            targetPercent = o.intOrNull("targetPercent"),
            durationSec = o.intOrNull("durationSec"),
            plannedStartMillis = o.longOrNull("plannedStart"),
            plannedEndMillis = o.longOrNull("plannedEnd"),
            verified = if (o.has("verified") && !o.isNull("verified")) o.getBoolean("verified") else null,
            note = o.optString("note", ""),
        )
    }
}

private fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
