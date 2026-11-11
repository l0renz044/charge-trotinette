package fr.laurent.chargetrotinette

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class PlugStatus(
    val on: Boolean,
    val watts: Double,
    val totalWh: Double?,
)

/** Commande de la prise Shelly (Gen3) en HTTP sur le réseau local. */
class ShellyClient(host: String) {

    private val base = "http://${host.trim()}"

    private suspend fun request(path: String, body: String? = null, timeoutMs: Int = 4000): String =
        withContext(Dispatchers.IO) {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                if (body != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IOException("HTTP $code $text")
                text
            } finally {
                conn.disconnect()
            }
        }

    private fun rpc(method: String, params: JSONObject? = null): String {
        val o = JSONObject().put("id", 1).put("method", method)
        if (params != null) o.put("params", params)
        return o.toString()
    }

    /** La prise répond-elle ? (limite de 4 s) */
    suspend fun ping(): Boolean = try {
        request("/rpc/Shelly.GetDeviceInfo")
        true
    } catch (e: Exception) {
        false
    }

    suspend fun status(): PlugStatus {
        val j = JSONObject(request("/rpc/Switch.GetStatus?id=0"))
        return PlugStatus(
            on = j.optBoolean("output", false),
            watts = j.optDouble("apower", 0.0),
            totalWh = j.optJSONObject("aenergy")?.optDouble("total"),
        )
    }

    /** Allume la prise ; elle s'éteint seule au bout de [forSeconds] secondes, même sans le téléphone. */
    suspend fun setOn(forSeconds: Int) {
        request("/rpc/Switch.Set?id=0&on=true&toggle_after=$forSeconds")
    }

    suspend fun setOff() {
        request("/rpc/Switch.Set?id=0&on=false")
    }

    suspend fun deleteSchedules() {
        request("/rpc", rpc("Schedule.DeleteAll"))
    }

    /** Planifie, dans la prise, un allumage à l'heure de [timespec] pour [forSeconds] secondes. */
    suspend fun createSchedule(timespec: String, forSeconds: Int) {
        val call = JSONObject()
            .put("method", "Switch.Set")
            .put(
                "params",
                JSONObject().put("id", 0).put("on", true).put("toggle_after", forSeconds)
            )
        val params = JSONObject()
            .put("enable", true)
            .put("timespec", timespec)
            .put("calls", JSONArray().put(call))
        request("/rpc", rpc("Schedule.Create", params))
    }

    suspend fun scheduleCount(): Int {
        val j = JSONObject(request("/rpc/Schedule.List"))
        return j.optJSONArray("jobs")?.length() ?: 0
    }

    /** Heure locale de la prise, au format HH:MM, ou null si elle n'est pas disponible. */
    suspend fun plugClock(): String? {
        val j = JSONObject(request("/rpc/Sys.GetStatus"))
        val t = j.optString("time", "")
        return if (t.isEmpty()) null else t
    }
}
