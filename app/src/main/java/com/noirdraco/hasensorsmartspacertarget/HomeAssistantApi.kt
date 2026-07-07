package com.noirdraco.hasensorsmartspacertarget

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
    data class HttpError(val code: Int) : FetchResult()
    data class NetworkError(val message: String) : FetchResult()
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

    fun fetch(settings: SensorSettings): FetchResult {
        val url = settings.baseUrl.trimEnd('/') + "/api/states/" + settings.entityId
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.token}")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return FetchResult.NetworkError("Leere Antwort")
                val json = JSONObject(body)
                val attributes = json.optJSONObject("attributes")
                FetchResult.Success(
                    SensorValue(
                        state = json.getString("state"),
                        friendlyName = attributes?.optString("friendly_name")
                            ?.takeIf { it.isNotBlank() } ?: settings.entityId,
                        unit = attributes?.optString("unit_of_measurement").orEmpty(),
                        icon = attributes?.optString("icon").orEmpty(),
                        deviceClass = attributes?.optString("device_class").orEmpty(),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            // u.a. Timeout, kein Netz, DNS, blockierter Klartext-Traffic, JSON-Parsefehler
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }
}
