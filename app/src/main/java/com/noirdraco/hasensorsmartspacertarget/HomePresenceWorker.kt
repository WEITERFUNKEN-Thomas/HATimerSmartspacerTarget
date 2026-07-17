package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerRequirementProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holt den Zustand der Anwesenheits-Entität (z. B. `person.thomas`) von Home Assistant und legt
 * ihn im selben Cache wie der Sensor-Target ab (pro smartspacerId). Läuft asynchron, weil
 * [HomePresenceRequirement.isRequirementMet] nicht blockieren darf.
 *
 * Anders als [HomeAssistantWorker] wird [SmartspacerRequirementProvider.notifyChange] nur bei
 * einer tatsächlichen Zustandsänderung ausgelöst — sonst würde jede Auswertung einen neuen
 * Refresh anstoßen und eine Endlosschleife erzeugen.
 */
class HomePresenceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SMARTSPACER_ID = "smartspacer_id"

        private const val MAX_ATTEMPTS = 4

        private val UNAVAILABLE_STATES = setOf("unavailable", "unknown", "none", "")
    }

    override suspend fun doWork(): Result {
        val smartspacerId = inputData.getString(KEY_SMARTSPACER_ID) ?: return Result.failure()
        val settings = HomeAssistantPrefs.loadSettings(applicationContext, smartspacerId)
            ?: return Result.failure()

        // Selbstheilung: sicherstellen, dass der Doze-Heartbeat gesetzt ist (u. a. nach Reboot, wenn
        // WorkManager den periodischen Fallback wiederherstellt und diesen Worker startet).
        HomeAssistantPrefs.schedulePresenceHeartbeat(applicationContext)

        return when (val result = withContext(Dispatchers.IO) { HomeAssistantApi.fetch(settings) }) {
            is FetchResult.Success -> {
                handleSuccess(smartspacerId, result.value)
                Result.success()
            }
            // Dauerhafter Fehler (falscher Token/Entity) — nicht wiederholen
            is FetchResult.HttpError -> Result.failure()
            // Vorübergehender Fehler — mit Backoff wiederholen, bis MAX_ATTEMPTS erreicht ist
            is FetchResult.NetworkError ->
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun handleSuccess(smartspacerId: String, value: SensorValue) {
        val old = HomeAssistantPrefs.loadLastValue(applicationContext, smartspacerId)
        val isUnavailable = value.state.trim().lowercase() in UNAVAILABLE_STATES
        // Kurzzeitig "unavailable"/"unknown" (Tracker offline) nicht sofort übernehmen, wenn schon
        // ein guter Wert vorliegt — sonst würde die Bedingung bei jedem Aussetzer umschlagen.
        if (isUnavailable && old != null) return

        val changed = old == null || !old.state.trim().equals(value.state.trim(), ignoreCase = true)
        HomeAssistantPrefs.saveLastValue(applicationContext, smartspacerId, value)
        if (changed) {
            SmartspacerRequirementProvider.notifyChange(
                applicationContext, HomePresenceRequirement::class.java, smartspacerId
            )
        }
    }
}
