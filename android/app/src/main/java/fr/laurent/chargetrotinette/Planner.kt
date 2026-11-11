package fr.laurent.chargetrotinette

import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

/** Calcul de la durée de charge et des heures de début et de fin. */
object Planner {

    /**
     * Durée (s) = (objectif - niveau) / 100 x capacité (Ah) / courant du chargeur (A) x coefficient x 3600.
     * Renvoie null s'il n'y a rien à charger ou si les réglages sont invalides.
     */
    fun plan(
        input: PlanInput,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ChargePlan? {
        val missing = input.targetPercent - input.currentPercent
        if (missing <= 0 || input.chargerAmps <= 0.0 || input.batteryAh <= 0.0) return null

        val rawSec = missing / 100.0 * input.batteryAh / input.chargerAmps * input.coefficient * 3600.0
        val maxSec = input.maxHours * 3600.0
        val capped = rawSec > maxSec
        val durationSec = min(rawSec, maxSec).roundToInt()

        val coolingMillis = if (input.batteryHot) input.coolingMinutes * 60_000L else 0L
        val earliest = nowMillis + coolingMillis
        var start = earliest
        var tooTight = false

        if (input.mode == StartMode.READY_BY) {
            val nowZoned = Instant.ofEpochMilli(nowMillis).atZone(zone)
            var ready = nowZoned
                .withHour(input.readyByHour)
                .withMinute(input.readyByMinute)
                .withSecond(0)
                .withNano(0)
            if (!ready.isAfter(nowZoned)) ready = ready.plusDays(1)
            val wanted = ready.toInstant().toEpochMilli() - durationSec * 1000L
            if (wanted > earliest) start = wanted else tooTight = true
        }

        val immediate = start - nowMillis < 60_000L
        val end = start + durationSec * 1000L
        return ChargePlan(
            durationSec = durationSec,
            startAtMillis = start,
            endAtMillis = end,
            immediate = immediate,
            capped = capped,
            tooTight = tooTight,
        )
    }
}
