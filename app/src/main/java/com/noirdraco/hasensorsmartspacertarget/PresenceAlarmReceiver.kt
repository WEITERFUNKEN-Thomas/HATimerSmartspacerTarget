package com.noirdraco.hasensorsmartspacertarget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Doze-fester Heartbeat für die Anwesenheits-Bedingung. Wird von [HomeAssistantPrefs.schedulePresenceHeartbeat]
 * per AlarmManager (`setAndAllowWhileIdle`) getaktet — anders als WorkManager-Periodic feuert das
 * auch im Standby/Doze. Bei jedem Feuern: alle registrierten Presence-IDs auffrischen (expedited,
 * also mit Netzzugriff) und den nächsten Alarm setzen (die Idle-Variante ist ein Einzel-Alarm).
 */
class PresenceAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRESENCE_HEARTBEAT) return
        HomeAssistantPrefs.presenceIds(context)
            .forEach { HomeAssistantPrefs.enqueuePresenceRefresh(context, it) }
        // Nächsten Heartbeat setzen (setAndAllowWhileIdle wiederholt nicht von selbst).
        HomeAssistantPrefs.schedulePresenceHeartbeat(context)
    }

    companion object {
        const val ACTION_PRESENCE_HEARTBEAT = "com.noirdraco.hasensor.PRESENCE_HEARTBEAT"
    }
}
