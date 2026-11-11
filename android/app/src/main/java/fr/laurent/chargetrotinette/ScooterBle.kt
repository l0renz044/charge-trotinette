package fr.laurent.chargetrotinette

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Lecture Bluetooth de la trottinette. Lecture seule : l'appli n'écrit jamais sur la trottinette.
 * Une lecture = recherche par adresse, connexion, abonnement aux notifications de 0xFF61,
 * attente du niveau (%) et de la tension, puis déconnexion.
 */
class ScooterBle(private val context: Context) {

    class BleException(message: String) : Exception(message)

    companion object {
        val SERVICE: UUID = UUID.fromString("0000ff60-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000ff61-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    @SuppressLint("MissingPermission")
    suspend fun read(address: String, timeoutMs: Long = 25_000): ScooterReading {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw BleException("Adresse Bluetooth invalide : $address")
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: throw BleException("Ce téléphone n'a pas de Bluetooth.")
        if (!adapter.isEnabled) throw BleException("Active le Bluetooth du téléphone.")
        val scanner = adapter.bluetoothLeScanner ?: throw BleException("Bluetooth indisponible.")

        val result = CompletableDeferred<ScooterReading>()
        var reading = ScooterReading()
        var gatt: BluetoothGatt? = null
        var scanning = false

        fun onFrame(bytes: ByteArray?) {
            val parsed = if (bytes != null) ScooterFrames.parse(bytes) else null
            if (parsed == null) return
            reading = ScooterFrames.merge(reading, parsed)
            if (reading.percent != null && reading.voltage != null) result.complete(reading)
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    result.completeExceptionally(
                        BleException("Connexion perdue avec la trottinette (code $status).")
                    )
                }
            }

            @Suppress("DEPRECATION")
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(SERVICE)?.getCharacteristic(NOTIFY)
                val descriptor = characteristic?.getDescriptor(CCCD)
                if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null || descriptor == null) {
                    result.completeExceptionally(
                        BleException("Service de données introuvable sur la trottinette.")
                    )
                    return
                }
                g.setCharacteristicNotification(characteristic, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }

            // Android 12 et moins
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                onFrame(characteristic.value)
            }

            // Android 13 et plus
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                onFrame(value)
            }
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
                if (!scanning) return
                scanning = false
                scanner.stopScan(this)
                gatt = scanResult.device.connectGatt(
                    context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
            }

            override fun onScanFailed(errorCode: Int) {
                result.completeExceptionally(
                    BleException("Recherche Bluetooth impossible (code $errorCode).")
                )
            }
        }

        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        scanner.startScan(listOf(filter), settings, scanCallback)

        try {
            return withTimeout(timeoutMs) { result.await() }
        } catch (e: TimeoutCancellationException) {
            if (reading.percent != null) return reading
            if (gatt == null) {
                throw BleException(
                    "Trottinette introuvable : allume-la, rapproche le téléphone et ferme l'appli iSinwheel."
                )
            }
            throw BleException("Connectée, mais aucune donnée reçue de la trottinette.")
        } finally {
            if (scanning) {
                scanning = false
                scanner.stopScan(scanCallback)
            }
            gatt?.disconnect()
            gatt?.close()
        }
    }
}
