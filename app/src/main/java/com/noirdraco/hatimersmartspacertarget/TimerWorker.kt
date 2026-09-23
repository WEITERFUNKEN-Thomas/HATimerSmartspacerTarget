package com.noirdraco.hatimersmartspacertarget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SMARTSPACER_ID = "smartspacer_id"

        // Nach so vielen vergeblichen Versuchen aufgeben (bis zum nächsten Heartbeat)
        private const val MAX_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result {
        val smartspacerId = inputData.getString(KEY_SMARTSPACER_ID) ?: return Result.failure()
        try {
            val settings = TimerPrefs.loadSettings(applicationContext, smartspacerId)
                ?: return Result.failure()

            // Selbstheilung: sicherstellen, dass der Doze-Heartbeat gesetzt ist (u. a. nach einem
            // Neustart, der eingeplante Alarme verwirft).
            TimerPrefs.scheduleHeartbeat(applicationContext)

            return when (val result = withContext(Dispatchers.IO) { HomeAssistantApi.fetch(settings) }) {
                is FetchResult.Success -> {
                    // Anders als bei einer reinen Wertanzeige wird „unavailable“/„unknown“ hier
                    // *übernommen* statt den alten Wert zu behalten: Genau daran erkennen wir, dass
                    // das Gerät aus ist und der Timer verschwinden soll. Ein alter Zielzeitpunkt
                    // würde sonst als Countdown weiterlaufen.
                    val value = result.value
                    val previousEnd =
                        TimerPrefs.loadLastValue(applicationContext, smartspacerId)?.endTimeMs
                    TimerPrefs.saveLastValue(
                        applicationContext, smartspacerId,
                        value.copy(
                            endTimeMs = Countdown.nextEndTime(
                                previousEnd, value.state, value.unit, value.timestamp
                            )
                        )
                    )
                    Result.success()
                }
                // Vorübergehend (HA startet neu, Proxy 502) — wie ein Netzwerkfehler behandeln
                is FetchResult.HttpError ->
                    if (result.isTransient) retryOrGiveUp() else Result.failure()
                // Dauerhaft (falscher Token/Entity, kaputte URL) — letzten Wert stehen lassen
                is FetchResult.ConfigError -> Result.failure()
                // Vorübergehender Fehler — mit Backoff wiederholen, bis MAX_ATTEMPTS erreicht ist
                is FetchResult.NetworkError -> retryOrGiveUp()
            }
        } finally {
            SmartspacerTargetProvider.notifyChange(
                applicationContext, TimerTarget::class.java, smartspacerId
            )
        }
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
}
