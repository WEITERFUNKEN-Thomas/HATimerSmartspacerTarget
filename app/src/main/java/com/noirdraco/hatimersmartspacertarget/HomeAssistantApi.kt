package com.noirdraco.hatimersmartspacertarget

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Ergebnis eines Sensor-Abrufs. Bewusst getrennt in Netzwerk- vs. HTTP-Fehler, damit der
 * Aufrufer unterscheiden kann: Netzwerkfehler sind meist vorübergehend (Retry sinnvoll),
 * ein HTTP-Fehler wie 401/404 ist dauerhaft (falscher Token / falsche Entity-ID).
 */
sealed class FetchResult {
    data class Success(val value: SensorValue) : FetchResult()
    data class HttpError(val code: Int) : FetchResult() {
        // 5xx kommt z. B. vom Reverse-Proxy, solange Home Assistant neu startet; 408/429 sind
        // ausdrücklich „später nochmal“. Nur 4xx sonst (401 Token, 404 Entity) ist dauerhaft.
        val isTransient: Boolean get() = code == 408 || code == 429 || code >= 500
    }
    data class NetworkError(val message: String) : FetchResult()

    /** Eingabe unbrauchbar (URL ohne `http://`, Steuerzeichen im Token) — Wiederholen hilft nie. */
    data class ConfigError(val message: String) : FetchResult()
}

/**
 * Kapselt den REST-Aufruf gegen die Home-Assistant-API. Blockierend — nur aus einem
 * Hintergrund-Kontext aufrufen (Worker bzw. eigener Thread), nie vom Main-Thread.
 */
object HomeAssistantApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(settings: TimerSettings): FetchResult {
        // Innerhalb des try, aber getrennt vom Abruf: url() und header() werfen bei ungültiger
        // Eingabe IllegalArgumentException — etwa bei „homeassistant.local:8123“ ohne Schema. Das
        // flog früher ungefangen durch und brachte den Verbindungstest zum Absturz.
        val request = try {
            Request.Builder()
                .url(settings.baseUrl.trimEnd('/') + "/api/states/" + settings.entityId)
                .header("Authorization", "Bearer ${settings.token}")
                .build()
        } catch (e: IllegalArgumentException) {
            return FetchResult.ConfigError(e.message ?: e.javaClass.simpleName)
        }
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return FetchResult.NetworkError("Leere Antwort")
                val json = JSONObject(body)
                val attributes = json.optJSONObject("attributes")
                val state = json.getString("state")
                val unit = attributes?.optString("unit_of_measurement").orEmpty()
                val now = System.currentTimeMillis()
                FetchResult.Success(
                    SensorValue(
                        state = state,
                        friendlyName = attributes?.optString("friendly_name")
                            ?.takeIf { it.isNotBlank() } ?: settings.entityId,
                        unit = unit,
                        icon = attributes?.optString("icon").orEmpty(),
                        deviceClass = attributes?.optString("device_class").orEmpty(),
                        timestamp = now,
                        // Ohne Vorgeschichte gerechnet; der Worker verfeinert das mit dem
                        // vorherigen Wert (Countdown.nextEndTime)
                        endTimeMs = Countdown.parseEndTime(state, unit, now)
                    )
                )
            }
        } catch (e: Exception) {
            // u.a. Timeout, kein Netz, DNS, blockierter Klartext-Traffic, JSON-Parsefehler
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }
}
