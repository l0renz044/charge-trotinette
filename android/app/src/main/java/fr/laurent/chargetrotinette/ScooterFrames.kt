package fr.laurent.chargetrotinette

/** Valeurs lues dans les trames Bluetooth de la trottinette. */
data class ScooterReading(
    val percent: Int? = null,
    val voltage: Double? = null,
    val motorTempC: Int? = null,
)

/**
 * Décodage des trames envoyées par la trottinette (service 0xFF60, caractéristique 0xFF61).
 *
 * Trame de 27 octets : 1A A1 <type> ... 1F F1. Deux types alternent :
 *  - type 0x20 : octet 8 = niveau de batterie (%).
 *  - type 0x21 : octets 7-8 = tension en centièmes de volt (petit-boutiste), octet 19 = température du moteur (°C).
 * Positions relevées sur une D1 Plus (comparées à l'appli iSinwheel) ; les autres octets sont inconnus.
 */
object ScooterFrames {
    const val FRAME_SIZE = 27

    fun parse(bytes: ByteArray): ScooterReading? {
        if (bytes.size != FRAME_SIZE) return null
        fun u(i: Int): Int = bytes[i].toInt() and 0xFF
        if (u(0) != 0x1A || u(1) != 0xA1 || u(25) != 0x1F || u(26) != 0xF1) return null

        return when (u(2)) {
            0x20 -> {
                val percent = u(8)
                if (percent in 0..100) ScooterReading(percent = percent) else null
            }
            0x21 -> ScooterReading(
                voltage = ((u(8) shl 8) or u(7)) / 100.0,
                motorTempC = u(19),
            )
            else -> null
        }
    }

    /** Fusionne deux lectures : les valeurs de [newer] priment quand elles existent. */
    fun merge(older: ScooterReading, newer: ScooterReading): ScooterReading = ScooterReading(
        percent = newer.percent ?: older.percent,
        voltage = newer.voltage ?: older.voltage,
        motorTempC = newer.motorTempC ?: older.motorTempC,
    )
}
