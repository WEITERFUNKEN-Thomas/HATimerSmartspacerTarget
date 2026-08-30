package com.noirdraco.hatimersmartspacertarget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Der **neustartfeste Anker** der Auffrischung — und das einzige Glied der Kette, das einen Boot
 * überlebt.
 *
 * Warum es ihn braucht: Der eigentliche Takt ist der AlarmManager-Heartbeat
 * ([TimerPrefs.scheduleHeartbeat]), aber Alarme sind nach einem Neustart weg. Nachgezogen werden
 * sie nur aus einem Worker-Lauf — und ein Worker läuft nur, wenn ihn jemand einplant. Nach dem Boot
 * tut das niemand: [TimerWorker] ist einmalige Arbeit und damit erledigt, und der Update-Broadcast
 * von Smartspacer ist im Standby genau so unzuverlässig, wie er es immer war. Ohne diesen Anker
 * steht die Auffrischung nach einem Neustart still, bis jemand von Hand speichert (am Gerät
 * gemessen, 30.08.2026: 13,5 Stunden kein einziger Hintergrundlauf).
 *
 * Als [androidx.work.PeriodicWorkRequest] liegt er in der WorkManager-Datenbank und wird nach einem
 * Neustart automatisch wieder eingeplant. Nach einem Force-Stop holt WorkManagers
 * `ForceStopRunnable` ihn beim nächsten Prozessstart zurück — und der Prozess startet schon dadurch,
 * dass Smartspacer den ContentProvider abfragt.
 *
 * Bewusst ein **eigener** Worker statt [TimerWorker] als periodische Arbeit: Bei periodischer Arbeit
 * beendet ein `Result.failure()` die Wiederholung dauerhaft. [TimerWorker] meldet aber genau das,
 * wenn Token oder Entity-ID nicht stimmen — der Anker wäre nach einem einzigen 401 für immer tot.
 * Dieser hier gibt deshalb **immer** [Result.success] zurück; er ruft selbst nichts ab, sondern
 * setzt nur den Heartbeat und stößt die eigentliche Auffrischung an.
 */
class TimerAnchorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Zuerst der Heartbeat: Er ist der verlässliche Takt im Doze, dieser Anker nur die
        // Rückfallebene, die ihn nach einem Neustart überhaupt erst wieder in Gang setzt.
        TimerPrefs.scheduleHeartbeat(applicationContext)
        TimerPrefs.targetIds(applicationContext)
            .forEach { TimerPrefs.enqueueRefresh(applicationContext, it) }
        // Nie failure: Das würde die periodische Arbeit dauerhaft beenden und damit genau den
        // Fehler wiederherstellen, gegen den dieser Worker existiert.
        return Result.success()
    }
}
