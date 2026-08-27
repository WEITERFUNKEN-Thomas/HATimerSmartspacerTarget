package com.noirdraco.hatimersmartspacertarget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Doze-fester Heartbeat. Wird von [TimerPrefs.scheduleHeartbeat] per AlarmManager
 * (`setAndAllowWhileIdle`) getaktet — anders als WorkManager-Periodic und der Update-Broadcast von
 * Smartspacer feuert das auch im Standby/Doze. Bei jedem Feuern: alle registrierten Timer
 * auffrischen (expedited, also mit Netzzugriff) und den nächsten Alarm setzen (die Idle-Variante
 * ist ein Einzel-Alarm).
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_HEARTBEAT) return
        TimerPrefs.targetIds(context)
            .forEach { TimerPrefs.enqueueRefresh(context, it) }
        // Nächsten Heartbeat setzen (setAndAllowWhileIdle wiederholt nicht von selbst).
        TimerPrefs.scheduleHeartbeat(context)
    }

    companion object {
        const val ACTION_TIMER_HEARTBEAT = "com.noirdraco.hatimer.TIMER_HEARTBEAT"
    }
}
