package fr.laurent.chargetrotinette

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Même principe que TopDriver : version.json sur GitHub + APK le plus récent. */
object UpdateChecker {
    const val VERSION_URL =
        "https://raw.githubusercontent.com/l0renz044/charge-trotinette/main/version.json"
    const val APK_URL =
        "https://github.com/l0renz044/charge-trotinette/raw/main/ChargeTrotinette_latest.apk"

    /** Dernière version publiée (ex. « v0.1.0 »), ou null si injoignable. */
    suspend fun latestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode != 200) {
                    null
                } else {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val latest = JSONObject(text.trim().removePrefix("﻿")).optString("latest", "")
                    if (latest.isEmpty()) null else latest
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Vrai si [latest] est strictement plus récente que [current] (comparaison numérique). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.trim().removePrefix("v").split(".").map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
