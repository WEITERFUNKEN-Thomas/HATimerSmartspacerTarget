package com.noirdraco.hatimersmartspacertarget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TimerSettings(
    val baseUrl: String,
    val token: String,
    val entityId: String,
    // Nachlaufzeit in Minuten: So lange nach Ablauf bleibt „Fertig“ stehen, danach verschwindet
    // das Target. 0 = sofort ausblenden.
    val remainMinutes: Int = DEFAULT_REMAIN_MINUTES
) {
    companion object {
        const val DEFAULT_REMAIN_MINUTES = 30
    }
}

data class SensorValue(
    val state: String,
    val friendlyName: String,
    // unit_of_measurement — bei Restzeit-Sensoren die Einheit der Zahl (s/min/h)
    val unit: String,
    // MDI-Name aus dem HA-Attribut "icon", z. B. "mdi:washing-machine" (leer, falls nicht gesetzt)
    val icon: String,
    // HA-Attribut "device_class", z. B. "timestamp" — Zweitquelle fürs Icon
    val deviceClass: String,
    // Zeitpunkt des Abrufs — Basis für Restzeit-Sensoren, siehe [Countdown.parseEndTime]
    val timestamp: Long
)

/**
 * Zentrale Ablage der pro-smartspacerId gespeicherten Einstellungen und des zuletzt abgerufenen
 * Sensorwerts — und der einzige Ort, an dem Work und Alarme eingeplant werden.
 *
 * Der Token liegt in EncryptedSharedPreferences (androidx.security), also verschlüsselt im
 * Android-Keystore. Sollte die Verschlüsselung auf einem Gerät fehlschlagen, wird auf normale
 * SharedPreferences zurückgefallen, damit die App nicht abstürzt — dann liegt der Token
 * unverschlüsselt, was für den privaten Gebrauch vertretbar ist.
 */
object TimerPrefs {

    private const val PREFS_NAME = "ha_timer_secure"
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

    fun saveSettings(context: Context, smartspacerId: String, settings: TimerSettings) {
        val json = JSONObject()
            .put("baseUrl", settings.baseUrl)
            .put("token", settings.token)
            .put("entityId", settings.entityId)
            .put("remainMinutes", settings.remainMinutes)
        prefs(context).edit { putString(KEY_SETTINGS_PREFIX + smartspacerId, json.toString()) }
    }

    fun loadSettings(context: Context, smartspacerId: String): TimerSettings? {
        val raw = prefs(context).getString(KEY_SETTINGS_PREFIX + smartspacerId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            TimerSettings(
                baseUrl = json.getString("baseUrl"),
                token = json.getString("token"),
                entityId = json.getString("entityId"),
                // optInt: Beim Erweitern der Einstellungen dürfen bestehende Einträge nicht
                // kaputtgehen — fehlt das Feld, gilt der Standard.
                remainMinutes = json.optInt(
                    "remainMinutes", TimerSettings.DEFAULT_REMAIN_MINUTES
                )
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

    private fun workName(smartspacerId: String) = "ha_timer_refresh_$smartspacerId"

    fun enqueueRefresh(context: Context, smartspacerId: String) {
        val request = OneTimeWorkRequestBuilder<TimerWorker>()
            .setInputData(workDataOf(TimerWorker.KEY_SMARTSPACER_ID to smartspacerId))
            // Expedited: Android gewährt diesem Job auch im Doze/Standby ein kurzes Zeitfenster mit
            // Netzzugriff. Ohne das scheitert der Abruf im Ruhezustand meist schon an DNS, und wir
            // bekämen den Start eines neuen Durchlaufs erst mit, wenn das Gerät wieder wach ist.
            // Fällt bei erschöpftem Expedited-Kontingent auf normale Ausführung zurück.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
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

    // Doze-fester Heartbeat. Weder WorkManager-Periodic noch der Update-Broadcast von Smartspacer
    // sind im Standby/Doze verlässlich; AlarmManager mit setAndAllowWhileIdle feuert dagegen auch
    // im Doze und stößt dann einen expedited Refresh an. Es gibt keine wiederholende Idle-Variante,
    // daher als Einzel-Alarm, den der Receiver bei jedem Feuern neu setzt (selbstheilend auch aus
    // dem Setup und jedem Worker-Lauf, deshalb braucht es keinen BOOT_COMPLETED-Receiver).
    //
    // Der laufende Countdown selbst hängt *nicht* daran: Er zählt im Host-Prozess herunter (siehe
    // [TimerTarget]). Der Heartbeat sorgt nur dafür, dass wir mitbekommen, wenn Home Assistant eine
    // neue Zielzeit setzt oder das Gerät ausgeschaltet wird.
    private const val HEARTBEAT_REQUEST_CODE = 4711
    private val HEARTBEAT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)

    private fun heartbeatPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, TimerAlarmReceiver::class.java)
            .setAction(TimerAlarmReceiver.ACTION_TIMER_HEARTBEAT)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleHeartbeat(context: Context) {
        // Nur sinnvoll, wenn überhaupt ein Target existiert.
        if (targetIds(context).isEmpty()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, heartbeatPendingIntent(context))
    }

    fun cancelHeartbeat(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(heartbeatPendingIntent(context))
    }

    // Register der aktiven Target-IDs: Der Doze-Heartbeat läuft außerhalb von Smartspacer und
    // erfährt sonst nicht, welche Timer es überhaupt gibt. Als \n-getrennter String abgelegt
    // (IDs sind UUIDs), um StringSet-Eigenheiten von EncryptedSharedPreferences zu vermeiden.
    private const val KEY_TARGET_IDS = "target_ids"

    fun targetIds(context: Context): Set<String> =
        prefs(context).getString(KEY_TARGET_IDS, null)
            ?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun addTargetId(context: Context, smartspacerId: String) {
        val ids = targetIds(context)
        // Das Target meldet sich bei jeder Auswertung selbstheilend an — steht die ID schon drin,
        // gar nicht erst schreiben.
        if (smartspacerId in ids) return
        prefs(context).edit { putString(KEY_TARGET_IDS, (ids + smartspacerId).joinToString("\n")) }
    }

    fun removeTargetId(context: Context, smartspacerId: String) {
        val ids = targetIds(context)
        if (smartspacerId !in ids) return
        prefs(context).edit { putString(KEY_TARGET_IDS, (ids - smartspacerId).joinToString("\n")) }
    }
}
