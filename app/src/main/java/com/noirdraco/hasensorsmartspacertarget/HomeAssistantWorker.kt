package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HomeAssistantWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SMARTSPACER_ID = "smartspacer_id"

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val smartspacerId = inputData.getString(KEY_SMARTSPACER_ID) ?: return Result.failure()
        try {
            val settings = HomeAssistantPrefs.loadSettings(applicationContext, smartspacerId)
                ?: return Result.failure()
            // Bei Fehlern (Timeout, HTTP-Fehler, kein Netz) bleibt der letzte bekannte Wert stehen
            val value = withContext(Dispatchers.IO) { fetchState(settings) }
                ?: return Result.failure()
            HomeAssistantPrefs.saveLastValue(applicationContext, smartspacerId, value)
            return Result.success()
        } finally {
            SmartspacerTargetProvider.notifyChange(
                applicationContext, HomeAssistantTarget::class.java, smartspacerId
            )
        }
    }

    private fun fetchState(settings: SensorSettings): SensorValue? {
        val url = settings.baseUrl.trimEnd('/') + "/api/states/" + settings.entityId
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.token}")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val attributes = json.optJSONObject("attributes")
                SensorValue(
                    state = json.getString("state"),
                    friendlyName = attributes?.optString("friendly_name")
                        ?.takeIf { it.isNotBlank() } ?: settings.entityId,
                    unit = attributes?.optString("unit_of_measurement").orEmpty(),
                    icon = attributes?.optString("icon").orEmpty(),
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
