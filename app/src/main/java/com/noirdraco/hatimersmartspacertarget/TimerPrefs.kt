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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
    val timestamp: Long,
    // Daraus errechneter Zielzeitpunkt, `null` = keiner. Wird mitgespeichert statt bei jeder
    // Anzeige neu gerechnet, weil er vom vorherigen Abruf abhängen kann (siehe
    // [Countdown.nextEndTime]).
    val endTimeMs: Long?
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
        // Anderer Sensor: Der zwischengespeicherte Wert gehört nicht mehr dazu. Er würde sonst bis
        // zum ersten Abruf angezeigt und dort als „vorheriger Zielzeitpunkt“ mitgerechnet.
        val previous = loadSettings(context, smartspacerId)
        if (previous != null &&
            (previous.baseUrl != settings.baseUrl || previous.entityId != settings.entityId)
        ) {
            prefs(context).edit { remove(KEY_LAST_VALUE_PREFIX + smartspacerId) }
        }
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
            // JSONObject.NULL statt null: put(key, null) entfernte den Schlüssel, und ein fehlender
            // Schlüssel heißt beim Laden „alter Cache, bitte neu rechnen“.
            .put("endTimeMs", value.endTimeMs ?: JSONObject.NULL)
        prefs(context).edit { putString(KEY_LAST_VALUE_PREFIX + smartspacerId, json.toString()) }
    }

    fun loadLastValue(context: Context, smartspacerId: String): SensorValue? {
        val raw = prefs(context).getString(KEY_LAST_VALUE_PREFIX + smartspacerId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val state = json.getString("state")
            val unit = json.optString("unit")
            val timestamp = json.getLong("timestamp")
            SensorValue(
                state = state,
                friendlyName = json.getString("friendlyName"),
                unit = unit,
                icon = json.optString("icon"),
                deviceClass = json.optString("deviceClass"),
                timestamp = timestamp,
                endTimeMs = when {
                    // Cache aus einer Version ohne dieses Feld
                    !json.has("endTimeMs") -> Countdown.parseEndTime(state, unit, timestamp)
                    json.isNull("endTimeMs") -> null
                    else -> json.getLong("endTimeMs")
                }
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

    // Neustartfester Anker der ganzen Auffrischung. Der AlarmManager-Heartbeat unten ist der
    // eigentliche Takt, aber Alarme sind nach einem Neustart weg — und nachgezogen werden sie nur
    // aus einem Worker-Lauf, den nach dem Boot niemand einplant. Periodische Arbeit liegt dagegen
    // in der WorkManager-Datenbank und wird nach Neustart bzw. Force-Stop von selbst wieder
    // eingeplant. Siehe [TimerAnchorWorker], dort steht die vollstaendige Begruendung.
    //
    // Global statt pro smartspacerId: Der Anker frischt — wie [TimerAlarmReceiver] — schlicht alle
    // registrierten Targets auf, ein einziger Auftrag genuegt also.
    private const val ANCHOR_WORK_NAME = "ha_timer_anchor"
    private const val ANCHOR_INTERVAL_MINUTES = 15L

    // Einmal pro Prozessleben genuegt: Genau die Ereignisse, die den Anker verlieren koennen
    // (Neustart, Force-Stop, Daten geloescht), starten den Prozess ohnehin neu. Ohne diese Sperre
    // wuerde jede Smartspacer-Abfrage eine Datenbank-Transaktion ausloesen, und die kommen oft.
    @Volatile
    private var anchorEnsured = false

    /**
     * Plant den Anker ein. [ExistingPeriodicWorkPolicy.KEEP], damit wiederholte Aufrufe den
     * 15-Minuten-Takt nicht staendig zuruecksetzen.
     *
     * Bewusst **ohne** Netz-Bedingung, anders als [enqueueRefresh]: Der Anker ruft selbst nichts ab.
     * Seine Aufgabe ist, den Heartbeat zu setzen — und das muss auch dann gelingen, wenn gerade kein
     * Netz da ist, sonst haengt die Erholung am Netz statt am Takt.
     */
    fun enqueueAnchor(context: Context) {
        val request = PeriodicWorkRequestBuilder<TimerAnchorWorker>(
            ANCHOR_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ANCHOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        anchorEnsured = true
    }

    /** Selbstheilender Aufruf fuer heisse Pfade — tut nach dem ersten Mal je Prozess nichts mehr. */
    fun ensureAnchor(context: Context) {
        if (anchorEnsured) return
        enqueueAnchor(context)
    }

    fun cancelAnchor(context: Context) {
        anchorEnsured = false
        WorkManager.getInstance(context).cancelUniqueWork(ANCHOR_WORK_NAME)
    }

    // Doze-fester Heartbeat. Weder WorkManager-Periodic noch der Update-Broadcast von Smartspacer
    // sind im Standby/Doze verlässlich; AlarmManager mit setAndAllowWhileIdle feuert dagegen auch
    // im Doze und stößt dann einen expedited Refresh an. Es gibt keine wiederholende Idle-Variante,
    // daher als Einzel-Alarm, den der Receiver bei jedem Feuern neu setzt (selbstheilend auch aus
    // dem Setup und jedem Worker-Lauf, deshalb braucht es keinen BOOT_COMPLETED-Receiver).
    //
    // Die Anzeige selbst hängt *nicht* daran: Gezeigt wird ein fester Zielzeitpunkt, der nicht
    // veralten kann (siehe [TimerTarget]). Der Heartbeat sorgt nur dafür, dass wir mitbekommen,
    // wenn Home Assistant eine neue Zielzeit setzt oder das Gerät ausgeschaltet wird.
    private const val HEARTBEAT_REQUEST_CODE = 4711
    private val HEARTBEAT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)

    private fun heartbeatIntent(context: Context) =
        Intent(context.applicationContext, TimerAlarmReceiver::class.java)
            .setAction(TimerAlarmReceiver.ACTION_TIMER_HEARTBEAT)

    private fun heartbeatPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            HEARTBEAT_REQUEST_CODE,
            heartbeatIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** `null`, wenn gerade kein Heartbeat eingeplant ist — FLAG_NO_CREATE legt keinen neuen an. */
    private fun existingHeartbeatPendingIntent(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context.applicationContext,
            HEARTBEAT_REQUEST_CODE,
            heartbeatIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleHeartbeat(context: Context) {
        // Nur sinnvoll, wenn überhaupt ein Target existiert.
        if (targetIds(context).isEmpty()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, heartbeatPendingIntent(context))
    }

    /**
     * Setzt den Heartbeat nur, wenn gerade keiner eingeplant ist — fuer heisse Pfade wie
     * [TimerTarget.getSmartspaceTargets].
     *
     * [scheduleHeartbeat] dort direkt aufzurufen waere falsch: Das schoebe den Alarm bei *jeder*
     * Abfrage auf „jetzt + 15 Minuten“, er kaeme bei aktiver Nutzung also nie zum Feuern.
     *
     * Die Pruefung greift genau in den beiden Faellen, um die es geht: Nach einem Neustart und nach
     * einem Force-Stop sind die PendingIntents der App weg, FLAG_NO_CREATE liefert dann `null`. Das
     * ist wichtig, weil der [TimerAnchorWorker] als gewoehnliche periodische Arbeit im Doze
     * verzoegert werden kann — der doze-feste Alarm darf nicht darauf warten muessen.
     */
    fun scheduleHeartbeatIfMissing(context: Context) {
        if (existingHeartbeatPendingIntent(context) != null) return
        scheduleHeartbeat(context)
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
