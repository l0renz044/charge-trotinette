package fr.laurent.chargetrotinette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PlannerTest {

    private val zone = ZoneId.of("Europe/Paris")
    // 10/11/2026 20:00 à Paris
    private val now = ZonedDateTime.of(2026, 11, 10, 20, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun input(
        current: Int,
        target: Int = 80,
        hot: Boolean = false,
        mode: StartMode = StartMode.NOW,
        maxHours: Double = 8.0,
        h: Int = 7,
        m: Int = 30,
    ) = PlanInput(
        currentPercent = current,
        targetPercent = target,
        chargerAmps = 2.0,
        batteryAh = 15.0,
        coefficient = 1.0,
        coolingMinutes = 30,
        maxHours = maxHours,
        batteryHot = hot,
        mode = mode,
        readyByHour = h,
        readyByMinute = m,
    )

    @Test
    fun duration_matchesTable() {
        // 30 % -> 80 % : 50 % x 15 Ah / 2 A = 3,75 h
        assertEquals(13_500, Planner.plan(input(30), now, zone)!!.durationSec)
        // 20 % -> 80 % : 4,5 h
        assertEquals(16_200, Planner.plan(input(20), now, zone)!!.durationSec)
        // 50 % -> 80 % : 2,25 h
        assertEquals(8_100, Planner.plan(input(50), now, zone)!!.durationSec)
    }

    @Test
    fun nothingToCharge() {
        assertNull(Planner.plan(input(80), now, zone))
        assertNull(Planner.plan(input(90), now, zone))
    }

    @Test
    fun immediateStart() {
        val p = Planner.plan(input(30), now, zone)!!
        assertTrue(p.immediate)
        assertEquals(now, p.startAtMillis)
        assertEquals(now + 13_500_000L, p.endAtMillis)
    }

    @Test
    fun hotBatteryDelaysStart() {
        val p = Planner.plan(input(30, hot = true), now, zone)!!
        assertFalse(p.immediate)
        assertEquals(now + 30 * 60_000L, p.startAtMillis)
    }

    @Test
    fun cappedDuration() {
        val p = Planner.plan(input(0, target = 100, maxHours = 3.0), now, zone)!!
        assertTrue(p.capped)
        assertEquals(3 * 3600, p.durationSec)
    }

    @Test
    fun readyBy_startsSoThatChargeEndsAtTheRequestedTime() {
        // Prête pour 7 h 30 le lendemain, 3 h 45 de charge -> démarrage à 3 h 45
        val p = Planner.plan(input(30, mode = StartMode.READY_BY), now, zone)!!
        val ready = ZonedDateTime.of(2026, 11, 11, 7, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ready, p.endAtMillis)
        assertEquals(ready - 13_500_000L, p.startAtMillis)
        assertFalse(p.immediate)
        assertFalse(p.tooTight)
    }

    @Test
    fun readyBy_tooTight() {
        // Prête pour 21 h alors qu'il faut 3 h 45 : démarrage au plus tôt
        val p = Planner.plan(input(30, mode = StartMode.READY_BY, h = 21, m = 0), now, zone)
        assertNotNull(p)
        assertTrue(p!!.tooTight)
        assertTrue(p.immediate)
        assertEquals(now, p.startAtMillis)
    }
}
