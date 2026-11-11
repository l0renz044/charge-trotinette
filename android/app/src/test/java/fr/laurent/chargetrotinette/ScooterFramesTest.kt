package fr.laurent.chargetrotinette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScooterFramesTest {

    private fun hex(s: String): ByteArray =
        s.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    // Trames relevées sur la trottinette (nRF Connect) et comparées à l'appli iSinwheel.
    @Test
    fun percentFrame_type20() {
        val r = ScooterFrames.parse(hex("1A A1 20 03 12 00 00 00 53 00 00 00 1B 00 00 00 00 23 20 20 02 03 08 1A 2E 1F F1"))
        assertNotNull(r)
        assertEquals(83, r!!.percent) // appli : 83 %
        assertNull(r.voltage)
    }

    @Test
    fun voltageFrame_type21_51v9() {
        val r = ScooterFrames.parse(hex("1A A1 21 03 12 00 00 46 14 00 00 00 00 0B 00 00 00 00 00 19 0F 03 08 B3 4B 1F F1"))
        assertNotNull(r)
        assertEquals(51.90, r!!.voltage!!, 0.001) // appli : 51,8 V (relevée 3 min plus tard)
        assertEquals(25, r.motorTempC)           // appli : 25 °C
        assertNull(r.percent)
    }

    @Test
    fun voltageFrame_type21_49v8() {
        val r = ScooterFrames.parse(hex("1A A1 21 03 12 00 00 74 13 00 00 00 00 08 00 00 00 00 00 1E 0F 03 08 C9 DB 1F F1"))
        assertNotNull(r)
        assertEquals(49.80, r!!.voltage!!, 0.001) // appli : 49,8 V
        assertEquals(30, r.motorTempC)           // appli : 30 °C
    }

    @Test
    fun invalidFramesAreIgnored() {
        assertNull(ScooterFrames.parse(hex("1A A1 20 03")))
        assertNull(ScooterFrames.parse(hex("2B A1 20 03 12 00 00 00 53 00 00 00 1B 00 00 00 00 23 20 20 02 03 08 1A 2E 1F F1")))
        assertNull(ScooterFrames.parse(hex("1A A1 20 03 12 00 00 00 53 00 00 00 1B 00 00 00 00 23 20 20 02 03 08 1A 2E 1F F2")))
        assertNull(ScooterFrames.parse(hex("1A A1 22 03 12 00 00 00 53 00 00 00 1B 00 00 00 00 23 20 20 02 03 08 1A 2E 1F F1")))
    }

    @Test
    fun mergeKeepsBothValues() {
        val a = ScooterReading(percent = 83)
        val b = ScooterReading(voltage = 51.9, motorTempC = 25)
        val m = ScooterFrames.merge(a, b)
        assertEquals(83, m.percent)
        assertEquals(51.9, m.voltage!!, 0.001)
        assertEquals(25, m.motorTempC)
    }
}
