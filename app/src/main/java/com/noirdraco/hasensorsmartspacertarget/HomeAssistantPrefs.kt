package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SensorSettings(
    val baseUrl: String,
    val token: String,
    val entityId: String
)

data class SensorValue(
    val state: String,
    val friendlyName: String,
    val unit: String,
    // MDI-Name aus dem HA-Attribut "icon", z. B. "mdi:trash-can" (leer, falls nicht gesetzt)
    val icon: String,
    // HA-Attribut "device_class", z. B. "temperature" — Zweitquelle fürs Icon
    val deviceClass: String,
    val timestamp: Long
)

/**
 * Zentrale Ablage der pro-smartspacerId gespeicherten Einstellungen und des zuletzt
 * abgerufenen Sensorwerts.
 *
 * Der Token liegt in EncryptedSharedPreferences (androidx.security), also verschlüsselt im
 * Android-Keystore. Sollte die Verschlüsselung auf einem Gerät fehlschlagen, wird auf normale
 * SharedPreferences zurückgefallen, damit die App nicht abstürzt — dann liegt der Token
 * unverschlüsselt, was für den privaten Gebrauch vertretbar ist.
 */
object HomeAssistantPrefs {

    private const val PREFS_NAME = "ha_sensor_secure"
    private const val KEY_SETTINGS_PREFIX = "settings_"
    private const val KEY_LAST_VALUE_PREFIX = "last_value_"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: buildPrefs(context.applicationContext).also { cachedPrefs = it }
        }
    }

    private fun buildPrefs(context: Context): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback: unverschlüsselt, aber lauffähig
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSettings(context: Context, smartspacerId: String, settings: SensorSettings) {
        val json = JSONObject()
            .put("baseUrl", settings.baseUrl)
            .put("token", settings.token)
            .put("entityId", settings.entityId)
        prefs(context).edit { putString(KEY_SETTINGS_PREFIX + smartspacerId, json.toString()) }
    }

    fun loadSettings(context: Context, smartspacerId: String): SensorSettings? {
        val raw = prefs(context).getString(KEY_SETTINGS_PREFIX + smartspacerId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SensorSettings(
                baseUrl = json.getString("baseUrl"),
                token = json.getString("token"),
                entityId = json.getString("entityId")
            )
        }.getOrNull()
    }

    fun saveLastValue(context: Context, smartspacerId: String, value: SensorValue) {
        val json = JSONObject()
            .put("state", value.state)
            .put("friendlyName", value.friendlyName)
            .put("unit", value.unit)
            .put("icon", value.icon)
            .put("deviceClass", value.deviceClass)
            .put("timestamp", value.timestamp)
        prefs(context).edit { putString(KEY_LAST_VALUE_PREFIX + smartspacerId, json.toString()) }
    }

    fun loadLastValue(context: Context, smartspacerId: String): SensorValue? {
        val raw = prefs(context).getString(KEY_LAST_VALUE_PREFIX + smartspacerId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SensorValue(
                state = json.getString("state"),
                friendlyName = json.getString("friendlyName"),
                unit = json.optString("unit"),
                icon = json.optString("icon"),
                deviceClass = json.optString("deviceClass"),
                timestamp = json.getLong("timestamp")
            )
        }.getOrNull()
    }

    fun clear(context: Context, smartspacerId: String) {
        prefs(context).edit {
            remove(KEY_SETTINGS_PREFIX + smartspacerId)
            remove(KEY_LAST_VALUE_PREFIX + smartspacerId)
        }
    }

    private fun workName(smartspacerId: String) = "ha_refresh_$smartspacerId"

    fun enqueueRefresh(context: Context, smartspacerId: String) {
        val request = OneTimeWorkRequestBuilder<HomeAssistantWorker>()
            .setInputData(workDataOf(HomeAssistantWorker.KEY_SMARTSPACER_ID to smartspacerId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(smartspacerId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelRefresh(context: Context, smartspacerId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(smartspacerId))
    }

    private fun presenceWorkName(smartspacerId: String) = "ha_presence_$smartspacerId"

    /**
     * Refresh für die Anwesenheits-Bedingung (Requirement). Wird u. a. aus
     * [HomePresenceRequirement.isRequirementMet] angestoßen; deshalb [ExistingWorkPolicy.KEEP],
     * damit ein laufender Abruf nicht bei jeder Auswertung neu gestartet wird.
     */
    fun enqueuePresenceRefresh(context: Context, smartspacerId: String) {
        val request = OneTimeWorkRequestBuilder<HomePresenceWorker>()
            .setInputData(workDataOf(HomePresenceWorker.KEY_SMARTSPACER_ID to smartspacerId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            presenceWorkName(smartspacerId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPresenceRefresh(context: Context, smartspacerId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(presenceWorkName(smartspacerId))
    }
}
