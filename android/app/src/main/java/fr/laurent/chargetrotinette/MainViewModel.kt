package fr.laurent.chargetrotinette

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min

data class UiState(
    val settings: Settings,
    val currentPercentText: String = "",
    val reading: ScooterReading? = null,
    val readingAtMillis: Long? = null,
    val mode: StartMode = StartMode.NOW,
    val readyByText: String = "07:30",
    val batteryHot: Boolean = false,
    val fullCharge: Boolean = false,
    val plugReachable: Boolean? = null,
    val plugStatus: PlugStatus? = null,
    val lastCommand: LastCommand? = null,
    val message: String = "Prêt.",
    val busy: Boolean = false,
    val updateLatest: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val MIN_CHARGE_WATTS = 5.0
        const val UNREACHABLE =
            "Prise injoignable : vérifie qu'elle est alimentée, sur le Wi-Fi 2,4 GHz, et que l'adresse IP est la bonne."
    }

    private val storage = Storage(app)
    private val ble = ScooterBle(app)

    var ui by mutableStateOf(
        UiState(settings = storage.loadSettings(), lastCommand = storage.loadLastCommand())
    )
        private set

    init {
        refreshPlug()
        checkUpdate()
    }

    // ---------- Saisies ----------

    fun onSettings(s: Settings) {
        ui = ui.copy(settings = s)
        storage.saveSettings(s)
    }

    fun onCurrentText(t: String) {
        ui = ui.copy(currentPercentText = t.filter { it.isDigit() }.take(3))
    }

    fun onMode(m: StartMode) {
        ui = ui.copy(mode = m)
    }

    fun onReadyBy(t: String) {
        ui = ui.copy(readyByText = t)
    }

    fun onHot(b: Boolean) {
        ui = ui.copy(batteryHot = b)
    }

    fun onFull(b: Boolean) {
        ui = ui.copy(fullCharge = b)
    }

    fun onMessage(m: String) {
        ui = ui.copy(message = m)
    }

    // ---------- Calcul ----------

    private fun parseHm(t: String): Pair<Int, Int>? {
        val r = Regex("""^\s*(\d{1,2})[:hH](\d{2})\s*$""").find(t) ?: return null
        val h = r.groupValues[1].toInt()
        val m = r.groupValues[2].toInt()
        return if (h in 0..23 && m in 0..59) Pair(h, m) else null
    }

    fun readyByValid(): Boolean = parseHm(ui.readyByText) != null

    fun currentPlan(): ChargePlan? {
        val cur = ui.currentPercentText.toIntOrNull() ?: return null
        if (cur !in 0..100) return null
        val s = ui.settings
        val hm = parseHm(ui.readyByText) ?: Pair(7, 30)
        return Planner.plan(
            PlanInput(
                currentPercent = cur,
                targetPercent = if (ui.fullCharge) 100 else s.targetPercent,
                chargerAmps = s.chargerAmps,
                batteryAh = s.batteryAh,
                coefficient = s.coefficient,
                coolingMinutes = s.coolingMinutes,
                maxHours = s.maxHours,
                batteryHot = ui.batteryHot,
                mode = ui.mode,
                readyByHour = hm.first,
                readyByMinute = hm.second,
            )
        )
    }

    // ---------- Trottinette (Bluetooth) ----------

    fun readScooter() {
        if (ui.busy) return
        viewModelScope.launch {
            ui = ui.copy(busy = true, message = "Lecture de la trottinette… (allumée, appli iSinwheel fermée)")
            var result: ScooterReading? = null
            var lastError = ""
            for (attempt in 1..2) {
                try {
                    result = ble.read(ui.settings.scooterMac)
                    break
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    delay(1000)
                }
            }
            val r = result
            ui = if (r != null) {
                val volts = r.voltage?.let { " · ${Fmt.volts(it)} V" } ?: ""
                ui.copy(
                    busy = false,
                    reading = r,
                    readingAtMillis = System.currentTimeMillis(),
                    currentPercentText = r.percent?.toString() ?: ui.currentPercentText,
                    message = "Trottinette lue : ${r.percent ?: "?"} %$volts",
                )
            } else {
                ui.copy(
                    busy = false,
                    message = "Lecture impossible : $lastError\nTu peux saisir le niveau à la main.",
                )
            }
        }
    }

    // ---------- Prise (Shelly) ----------

    fun refreshPlug() {
        val host = ui.settings.plugHost
        viewModelScope.launch {
            val client = ShellyClient(host)
            val ok = client.ping()
            val st = if (ok) runCatching { client.status() }.getOrNull() else null
            ui = ui.copy(plugReachable = ok, plugStatus = st)
        }
    }

    private fun record(c: LastCommand, message: String) {
        storage.saveLastCommand(c)
        ui = ui.copy(busy = false, lastCommand = c, message = message)
        refreshPlug()
    }

    private fun clockDiffMinutes(plugHm: String): Int? {
        val p = parseHm(plugHm) ?: return null
        val now = ZonedDateTime.now()
        val d = abs((p.first * 60 + p.second) - (now.hour * 60 + now.minute))
        return min(d, 1440 - d)
    }

    fun start() {
        if (ui.busy) return
        val plan = currentPlan()
        if (plan == null) {
            ui = ui.copy(message = "Rien à charger : renseigne un niveau inférieur à l'objectif.")
            return
        }
        val startPercent = ui.currentPercentText.toIntOrNull()
        val target = if (ui.fullCharge) 100 else ui.settings.targetPercent
        val client = ShellyClient(ui.settings.plugHost)

        viewModelScope.launch {
            ui = ui.copy(busy = true, message = "Vérification de la prise…")
            if (!client.ping()) {
                ui = ui.copy(busy = false, plugReachable = false, message = "$UNREACHABLE\nRien n'a été lancé.")
                return@launch
            }
            ui = ui.copy(plugReachable = true)

            val sentAt = System.currentTimeMillis()
            var type = CommandType.START_NOW
            var verified: Boolean? = null
            var note = ""
            var plannedStart = plan.startAtMillis
            var plannedEnd = plan.endAtMillis
            var message: String

            try {
                client.deleteSchedules()
                if (plan.immediate) {
                    client.setOn(plan.durationSec)
                    delay(3500)
                    val st = runCatching { client.status() }.getOrNull()
                    if (st == null) {
                        verified = null
                        message = "Commande envoyée : charge de ${Fmt.duration(plan.durationSec)}, mais l'état de la prise n'est pas lisible."
                    } else if (!st.on) {
                        verified = false
                        message = "⚠ La prise est éteinte : la commande n'a pas été prise en compte."
                    } else if (st.watts < MIN_CHARGE_WATTS) {
                        verified = false
                        note = "Prise allumée mais seulement ${st.watts.toInt()} W : chargeur branché à la trottinette ?"
                        message = "⚠ $note"
                    } else {
                        verified = true
                        message = "✅ Charge en cours : ${st.watts.toInt()} W. La prise se coupera toute seule à ${Fmt.hm(plannedEnd)}."
                    }
                } else {
                    type = CommandType.SCHEDULE
                    val startZ = Instant.ofEpochMilli(plan.startAtMillis).atZone(ZoneId.systemDefault())
                    val t = startZ.plusSeconds(59).withSecond(0).withNano(0)
                    plannedStart = t.toInstant().toEpochMilli()
                    plannedEnd = plannedStart + plan.durationSec * 1000L
                    val timespec = "0 ${t.minute} ${t.hour} ${t.dayOfMonth} ${t.monthValue} *"
                    client.createSchedule(timespec, plan.durationSec)

                    val count = runCatching { client.scheduleCount() }.getOrNull()
                    verified = if (count == null) null else count > 0

                    val plugClock = runCatching { client.plugClock() }.getOrNull()
                    val diff = if (plugClock != null) clockDiffMinutes(plugClock) else null
                    if (diff != null && diff > 2) {
                        note = "⚠ L'heure de la prise ($plugClock) diffère de celle du téléphone : " +
                            "la planification peut partir à la mauvaise heure. Vérifie le fuseau horaire dans l'appli Shelly."
                    }
                    message = when (verified) {
                        true -> "✅ Planification enregistrée dans la prise : démarrage à ${Fmt.hm(plannedStart)}, charge de ${Fmt.duration(plan.durationSec)}."
                        false -> "⚠ La prise n'a enregistré aucune planification."
                        null -> "Planification envoyée (démarrage à ${Fmt.hm(plannedStart)}), non vérifiable."
                    }
                    if (note.isNotEmpty()) message += "\n$note"
                }
            } catch (e: Exception) {
                verified = false
                note = "Échec : ${e.message}"
                message = "⚠ $note"
            }

            record(
                LastCommand(
                    type = type,
                    sentAtMillis = sentAt,
                    startPercent = startPercent,
                    targetPercent = target,
                    durationSec = plan.durationSec,
                    plannedStartMillis = plannedStart,
                    plannedEndMillis = plannedEnd,
                    verified = verified,
                    note = note,
                ),
                message,
            )
        }
    }

    fun stop() {
        if (ui.busy) return
        val client = ShellyClient(ui.settings.plugHost)
        viewModelScope.launch {
            ui = ui.copy(busy = true, message = "Vérification de la prise…")
            if (!client.ping()) {
                ui = ui.copy(busy = false, plugReachable = false, message = "$UNREACHABLE\nLa coupure n'a PAS été envoyée.")
                return@launch
            }
            val sentAt = System.currentTimeMillis()
            var verified: Boolean? = null
            var note = ""
            var message: String
            try {
                client.deleteSchedules()
                client.setOff()
                delay(1500)
                val st = runCatching { client.status() }.getOrNull()
                if (st == null) {
                    verified = null
                    message = "Coupure envoyée, état non vérifiable."
                } else if (st.on) {
                    verified = false
                    message = "⚠ La prise est encore allumée : réessaie."
                } else {
                    verified = true
                    message = "✅ Planifications annulées, prise coupée."
                }
            } catch (e: Exception) {
                verified = false
                note = "Échec : ${e.message}"
                message = "⚠ $note"
            }
            record(
                LastCommand(
                    type = CommandType.STOP,
                    sentAtMillis = sentAt,
                    startPercent = null,
                    targetPercent = null,
                    durationSec = null,
                    plannedStartMillis = null,
                    plannedEndMillis = null,
                    verified = verified,
                    note = note,
                ),
                message,
            )
        }
    }

    /** Trois impulsions de 1 s pour entendre le relais et voir le voyant (refusé si la prise est allumée). */
    fun testPlug() {
        if (ui.busy) return
        val client = ShellyClient(ui.settings.plugHost)
        viewModelScope.launch {
            ui = ui.copy(busy = true, message = "Vérification de la prise…")
            if (!client.ping()) {
                ui = ui.copy(busy = false, plugReachable = false, message = UNREACHABLE)
                return@launch
            }
            val before = runCatching { client.status() }.getOrNull()
            if (before != null && before.on) {
                ui = ui.copy(busy = false, message = "La prise est allumée (charge en cours ?). Test annulé pour ne pas couper la charge.")
                return@launch
            }
            val sentAt = System.currentTimeMillis()
            var verified: Boolean? = null
            var note = ""
            var message: String
            try {
                for (i in 1..3) {
                    ui = ui.copy(message = "Test $i/3 : impulsion…")
                    client.setOn(1)
                    delay(2500)
                }
                message = "Test terminé, prise éteinte. As-tu entendu 3 clics ou vu le voyant réagir ?"
            } catch (e: Exception) {
                verified = false
                note = "Échec : ${e.message}"
                message = "⚠ $note"
            }
            record(
                LastCommand(
                    type = CommandType.TEST,
                    sentAtMillis = sentAt,
                    startPercent = null,
                    targetPercent = null,
                    durationSec = null,
                    plannedStartMillis = null,
                    plannedEndMillis = null,
                    verified = verified,
                    note = note,
                ),
                message,
            )
        }
    }

    // ---------- Mise à jour ----------

    private fun checkUpdate() {
        viewModelScope.launch {
            val latest = UpdateChecker.latestVersion()
            if (latest != null && UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) {
                ui = ui.copy(updateLatest = latest)
            }
        }
    }
}
