package fr.laurent.chargetrotinette

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Lecture de l'état Wi-Fi du téléphone.
 * Android ne donne le nom du Wi-Fi (SSID) qu'avec la permission de localisation ET la localisation activée ;
 * l'appli n'utilise pas la position elle-même.
 */
object NetworkChecker {

    @Suppress("DEPRECATION")
    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Nom du Wi-Fi actuel, ou null s'il est illisible. */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSsid(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val raw = wm.connectionInfo?.ssid ?: return null
        val s = raw.removePrefix("\"").removeSuffix("\"")
        return if (s.isEmpty() || s == "<unknown ssid>") null else s
    }

    fun check(context: Context, expected: String): WifiCheck {
        val onWifi = isOnWifi(context)
        val permission = hasLocationPermission(context)
        val location = permission && isLocationEnabled(context)
        val ssid = if (onWifi && permission && location) currentSsid(context) else null
        return WifiLogic.decide(onWifi, permission, location, ssid, expected)
    }
}
