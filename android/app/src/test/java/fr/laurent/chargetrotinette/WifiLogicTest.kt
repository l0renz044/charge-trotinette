package fr.laurent.chargetrotinette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiLogicTest {

    private fun decide(
        onWifi: Boolean = true,
        permission: Boolean = true,
        location: Boolean = true,
        current: String? = "MaBox",
        expected: String = "MaBox",
    ) = WifiLogic.decide(onWifi, permission, location, current, expected)

    @Test
    fun sameNetwork_isOk() {
        val c = decide()
        assertEquals(WifiState.OK, c.state)
        assertEquals("MaBox", c.currentSsid)
    }

    @Test
    fun otherNetwork_isDifferent() {
        val c = decide(current = "Voisin")
        assertEquals(WifiState.DIFFERENT, c.state)
        assertEquals("Voisin", c.currentSsid)
        assertEquals("MaBox", c.expectedSsid)
    }

    @Test
    fun ssidIsCaseSensitive() {
        assertEquals(WifiState.DIFFERENT, decide(current = "mabox").state)
    }

    @Test
    fun expectedIsTrimmed() {
        assertEquals(WifiState.OK, decide(expected = "  MaBox ").state)
    }

    @Test
    fun noWifi_hasPriority() {
        assertEquals(WifiState.NO_WIFI, decide(onWifi = false, permission = false).state)
    }

    @Test
    fun missingPermissionOrLocation() {
        assertEquals(WifiState.NEED_PERMISSION, decide(permission = false).state)
        assertEquals(WifiState.LOCATION_OFF, decide(location = false).state)
    }

    @Test
    fun unreadableSsid() {
        assertEquals(WifiState.UNKNOWN_SSID, decide(current = null).state)
    }

    @Test
    fun notConfigured_showsCurrentNetwork() {
        val c = decide(expected = "")
        assertEquals(WifiState.NOT_CONFIGURED, c.state)
        assertEquals("MaBox", c.currentSsid)
        assertTrue(c.expectedSsid == null)
    }
}
