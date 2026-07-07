package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject

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
    val timestamp: Long
)

/**
 * Zentrale Ablage der pro-smartspacerId gespeicherten Einstellungen und des zuletzt
 * abgerufenen Sensorwerts.
 *
 * Sicherheitshinweis: Der Long-Lived Access Token liegt hier in normalen, unverschlüsselten
 * SharedPreferences. Für den privaten Gebrauch auf dem eigenen Gerät ok — bei Bedarf durch
 * androidx.security:security-crypto (EncryptedSharedPreferences) ersetzen.
 */
object HomeAssistantPrefs {

    private const val PREFS_NAME = "ha_sensor"
    private const val KEY_SETTINGS_PREFIX = "settings_"
    private const val KEY_LAST_VALUE_PREFIX = "last_value_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun enqueueRefresh(context: Context, smartspacerId: String) {
        val request = OneTimeWorkRequestBuilder<HomeAssistantWorker>()
            .setInputData(workDataOf(HomeAssistantWorker.KEY_SMARTSPACER_ID to smartspacerId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ha_refresh_$smartspacerId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
