package fr.laurent.chargetrotinette

enum class WifiState {
    /** Le téléphone est sur le Wi-Fi de la prise. */
    OK,

    /** Le téléphone est en Wi-Fi, mais sur un autre réseau que celui de la prise. */
    DIFFERENT,

    /** Le téléphone n'est pas connecté en Wi-Fi (données mobiles, mode avion...). */
    NO_WIFI,

    /** Android refuse de donner le nom du Wi-Fi sans la permission de localisation. */
    NEED_PERMISSION,

    /** La localisation du téléphone est désactivée (Android l'exige pour lire le nom du Wi-Fi). */
    LOCATION_OFF,

    /** Connecté en Wi-Fi, mais le nom du réseau est illisible. */
    UNKNOWN_SSID,

    /** Le réseau de la prise n'est pas encore renseigné dans les réglages. */
    NOT_CONFIGURED,
}

data class WifiCheck(
    val state: WifiState,
    val currentSsid: String?,
    val expectedSsid: String?,
)

/** Décision pure (sans accès à Android), testable sur le PC. */
object WifiLogic {
    fun decide(
        onWifi: Boolean,
        hasPermission: Boolean,
        locationOn: Boolean,
        currentSsid: String?,
        expected: String,
    ): WifiCheck {
        val exp = expected.trim().ifEmpty { null }
        if (!onWifi) return WifiCheck(WifiState.NO_WIFI, null, exp)
        if (!hasPermission) return WifiCheck(WifiState.NEED_PERMISSION, null, exp)
        if (!locationOn) return WifiCheck(WifiState.LOCATION_OFF, null, exp)
        if (currentSsid == null) return WifiCheck(WifiState.UNKNOWN_SSID, null, exp)
        if (exp == null) return WifiCheck(WifiState.NOT_CONFIGURED, currentSsid, null)
        return if (currentSsid == exp) {
            WifiCheck(WifiState.OK, currentSsid, exp)
        } else {
            WifiCheck(WifiState.DIFFERENT, currentSsid, exp)
        }
    }
}
