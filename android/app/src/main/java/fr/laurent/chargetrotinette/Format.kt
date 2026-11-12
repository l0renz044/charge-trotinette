package fr.laurent.chargetrotinette

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Mise en forme des textes affichés. */
object Fmt {
    private val hmFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")

    fun hm(millis: Long): String =
        hmFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun dateTime(millis: Long): String =
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** 13500 s -> « 3 h 45 » */
    fun duration(sec: Int): String {
        val totalMin = (sec + 30) / 60
        return "${totalMin / 60} h ${(totalMin % 60).toString().padStart(2, '0')}"
    }

    fun num(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    fun volts(v: Double): String = String.format(Locale.FRANCE, "%.1f", v)

    /** Texte du test « le téléphone est-il sur le Wi-Fi de la prise ? ». */
    fun wifi(c: WifiCheck): String = when (c.state) {
        WifiState.OK -> "✅ Téléphone sur le Wi-Fi de la prise (« ${c.currentSsid} »)."
        WifiState.DIFFERENT ->
            "⚠ Le téléphone est sur « ${c.currentSsid} » alors que la prise est sur « ${c.expectedSsid} »."
        WifiState.NO_WIFI -> "⚠ Le téléphone n'est pas connecté en Wi-Fi (données mobiles ?)."
        WifiState.NEED_PERMISSION -> "Autorise la localisation pour que l'appli lise le nom du Wi-Fi (touche ici)."
        WifiState.LOCATION_OFF -> "Active la localisation du téléphone pour lire le nom du Wi-Fi."
        WifiState.UNKNOWN_SSID -> "Nom du Wi-Fi illisible."
        WifiState.NOT_CONFIGURED ->
            "Wi-Fi actuel : « ${c.currentSsid} ». Renseigne le réseau de la prise dans les Réglages pour le vérifier."
    }

    /** Description de la dernière commande envoyée à la prise. */
    fun describe(c: LastCommand): String {
        val title = when (c.type) {
            CommandType.START_NOW -> "Charge lancée"
            CommandType.SCHEDULE -> "Charge planifiée"
            CommandType.STOP -> "Coupure et annulation des planifications"
            CommandType.TEST -> "Test de la prise (3 impulsions)"
        }
        val sb = StringBuilder("$title, le ${dateTime(c.sentAtMillis)}")

        if (c.type == CommandType.START_NOW || c.type == CommandType.SCHEDULE) {
            val from = c.startPercent
            val to = c.targetPercent
            if (from != null && to != null) sb.append("\nDe $from % à $to %")
            val dur = c.durationSec
            if (dur != null) sb.append(if (from != null) " · durée ${duration(dur)}" else "\nDurée ${duration(dur)}")
            val ps = c.plannedStartMillis
            val pe = c.plannedEndMillis
            if (c.type == CommandType.SCHEDULE && ps != null) sb.append("\nDémarrage prévu à ${hm(ps)}")
            if (pe != null) sb.append("\nFin prévue à ${hm(pe)}")
        }

        sb.append(
            when (c.verified) {
                true -> "\n✅ Confirmée par la prise"
                false -> "\n⚠ Non confirmée par la prise"
                null -> "\nÉtat non vérifiable"
            }
        )
        if (c.note.isNotBlank()) sb.append("\n${c.note}")
        return sb.toString()
    }

    /** Compare la dernière commande à l'état réel de la prise ; null s'il n'y a rien à signaler. */
    fun consistency(c: LastCommand?, plug: PlugStatus?, nowMillis: Long): String? {
        if (c == null || plug == null) return null
        if (c.type != CommandType.START_NOW && c.type != CommandType.SCHEDULE) return null
        val start = c.plannedStartMillis ?: c.sentAtMillis
        val end = c.plannedEndMillis ?: return null

        return when {
            nowMillis < start ->
                if (plug.on) "⚠ La prise est allumée alors que la charge ne devait démarrer qu'à ${hm(start)}." else null
            nowMillis < end ->
                if (plug.on) "La charge est en cours (${plug.watts.roundToInt()} W), fin prévue à ${hm(end)}."
                else "⚠ La prise est éteinte alors que la charge devait durer jusqu'à ${hm(end)}."
            else ->
                if (plug.on) "⚠ La prise est encore allumée alors que la charge devait se terminer à ${hm(end)}." else null
        }
    }
}
