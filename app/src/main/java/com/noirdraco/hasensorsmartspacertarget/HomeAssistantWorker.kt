package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeAssistantWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SMARTSPACER_ID = "smartspacer_id"

        // Nach so vielen vergeblichen Versuchen aufgeben (bis zum nächsten periodischen Refresh)
        private const val MAX_ATTEMPTS = 4

        // HA-Zustände, die "kein echter Wert" bedeuten
        private val UNAVAILABLE_STATES = setOf("unavailable", "unknown", "none", "")
    }

    override suspend fun doWork(): Result {
        val smartspacerId = inputData.getString(KEY_SMARTSPACER_ID) ?: return Result.failure()
        try {
            val settings = HomeAssistantPrefs.loadSettings(applicationContext, smartspacerId)
                ?: return Result.failure()

            return when (val result = withContext(Dispatchers.IO) { HomeAssistantApi.fetch(settings) }) {
                is FetchResult.Success -> {
                    handleSuccess(smartspacerId, result.value)
                    Result.success()
                }
                // Dauerhafter Fehler (falscher Token/Entity) — letzten Wert stehen lassen, nicht wiederholen
                is FetchResult.HttpError -> Result.failure()
                // Vorübergehender Fehler — mit Backoff wiederholen, bis MAX_ATTEMPTS erreicht ist
                is FetchResult.NetworkError ->
                    if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } finally {
            SmartspacerTargetProvider.notifyChange(
                applicationContext, HomeAssistantTarget::class.java, smartspacerId
            )
        }
    }

    private fun handleSuccess(smartspacerId: String, value: SensorValue) {
        val isUnavailable = value.state.trim().lowercase() in UNAVAILABLE_STATES
        // Wenn der Sensor gerade nicht verfügbar ist, aber schon ein guter Wert im Cache liegt,
        // den alten Wert behalten statt "unavailable" anzuzeigen.
        if (isUnavailable && HomeAssistantPrefs.loadLastValue(applicationContext, smartspacerId) != null) {
            return
        }
        HomeAssistantPrefs.saveLastValue(applicationContext, smartspacerId, value)
    }
}
