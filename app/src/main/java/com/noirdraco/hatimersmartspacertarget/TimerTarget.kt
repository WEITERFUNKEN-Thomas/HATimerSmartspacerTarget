package com.noirdraco.hatimersmartspacertarget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants
import com.kieronquinn.app.smartspacer.sdk.model.CompatibilityState
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import java.util.Date
import android.graphics.drawable.Icon as AndroidIcon

/**
 * Zeigt im Smartspace, wann ein Home-Assistant-Sensor fertig wird — als **Zielzeitpunkt**
 * („fertig um 14:02“), nicht als Restzeit.
 *
 * Das ist bewusst so und die sparsamste Lösung überhaupt: Ein fester Zeitpunkt ändert sich nicht,
 * muss also nie aktualisiert werden und kann nicht veralten. Eine eingefrorene Restzeit dagegen
 * wäre schlicht falsch, sobald sie ein paar Minuten alt ist.
 *
 * **Warum kein tickender Countdown?** Ein erster Ansatz lieferte [TargetTemplate.RemoteViews] mit
 * einem `Chronometer`, der im Prozess der anzeigenden App herunterzählt (am Gerät nachgewiesen,
 * 27.08.2026). Das funktionierte, zeigt aber zwingend Sekunden: `Chronometer` formatiert intern
 * über `DateUtils.formatElapsedTime`, das immer `MM:SS` oder `H:MM:SS` liefert — `setFormat` legt
 * nur Text drumherum. Eine Anzeige ohne Sekunden ist damit nur als statischer Text möglich, und der
 * kann höchstens so frisch sein wie unser Abrufzyklus (15 Min). Also: Zielzeitpunkt.
 *
 * Angenehmer Nebeneffekt: Mit [TargetTemplate.Basic] übernimmt Smartspacer das Aussehen selbst.
 * Das eigene RemoteViews-Layout musste Untergrund, Schrift und Position von Hand nachbauen — samt
 * am Gerät ausgemessener Werte, die nur auf genau diesem Launcher gestimmt hätten.
 */
class TimerTarget : SmartspacerTargetProvider() {

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val context = provideContext()
        // Kein Netzwerk-Call hier — es wird nur der vom Worker gecachte Wert gelesen
        val settings = TimerPrefs.loadSettings(context, smartspacerId)
            ?: return listOf(setupTarget(context, smartspacerId))

        // Selbstheilung: fürs Auffrischen per Doze-Heartbeat registrieren. Schreibt nur, wenn die
        // ID noch fehlt — diese Methode wird sehr oft gerufen.
        TimerPrefs.addTargetId(context, smartspacerId)
        // Ebenfalls selbstheilend, und hier genau richtig aufgehoben: Diese Methode ist der einzige
        // Pfad, der nach einem Neustart verlässlich läuft — Smartspacer fragt den ContentProvider
        // ab, sobald der Smartspace gezeichnet wird. Beides zusammen schließt die Lücke: Der Anker
        // bringt den Takt über Neustarts, der Heartbeat ist der einzige, der im Doze feuert — und
        // er wird hier nur gesetzt, wenn er fehlt, sonst schöbe ihn jede Abfrage nach hinten.
        TimerPrefs.ensureAnchor(context)
        TimerPrefs.scheduleHeartbeatIfMissing(context)

        val value = TimerPrefs.loadLastValue(context, smartspacerId)
        val now = System.currentTimeMillis()
        val endTime = value?.let { Countdown.parseEndTime(it.state, it.unit, it.timestamp) }

        return when (val state = Countdown.evaluate(endTime, now, settings.remainMinutes)) {
            // Kein Zielzeitpunkt (Gerät aus, Sensor leer) oder Nachlauf vorbei: Das Target
            // verschwindet komplett aus dem Smartspace.
            is CountdownState.None -> emptyList()
            is CountdownState.Finished -> listOf(
                basicTarget(
                    context = context,
                    smartspacerId = smartspacerId,
                    settings = settings,
                    title = title(context, value),
                    subtitle = context.getString(R.string.target_finished),
                    iconRes = iconRes(value)
                )
            )
            is CountdownState.Running -> listOf(
                basicTarget(
                    context = context,
                    smartspacerId = smartspacerId,
                    settings = settings,
                    title = title(context, value),
                    subtitle = context.getString(
                        R.string.target_ready_at, formatEndTime(context, state.endTimeMs, now)
                    ),
                    iconRes = iconRes(value)
                )
            )
        }
    }

    private fun basicTarget(
        context: Context,
        smartspacerId: String,
        settings: TimerSettings,
        title: String,
        subtitle: String,
        iconRes: Int
    ): SmartspaceTarget =
        basicTemplate(context, smartspacerId, settings, title, subtitle, iconRes)
            .create().apply { canBeDismissed = false }

    private fun basicTemplate(
        context: Context,
        smartspacerId: String,
        settings: TimerSettings,
        title: String,
        subtitle: String,
        iconRes: Int
    ) = TargetTemplate.Basic(
        id = "ha_timer_$smartspacerId",
        componentName = ComponentName(context, TimerTarget::class.java),
        title = Text(title),
        subtitle = Text(subtitle),
        icon = Icon(AndroidIcon.createWithResource(context, iconRes)),
        onClick = TapAction(intent = tapIntent(context, settings))
    )

    /** Noch nicht eingerichtet: ein Target, das die Einrichtungsseite öffnet. */
    private fun setupTarget(context: Context, smartspacerId: String): SmartspaceTarget =
        TargetTemplate.Basic(
            id = "ha_timer_$smartspacerId",
            componentName = ComponentName(context, TimerTarget::class.java),
            title = Text(context.getString(R.string.target_label)),
            subtitle = Text(context.getString(R.string.target_setup_needed)),
            icon = Icon(AndroidIcon.createWithResource(context, R.drawable.ic_home_assistant)),
            onClick = TapAction(
                intent = Intent(context, SetupActivity::class.java)
                    .putExtra(SmartspacerConstants.EXTRA_SMARTSPACER_ID, smartspacerId)
            )
        ).create().apply { canBeDismissed = false }

    /**
     * Tap öffnet den Verlauf dieses Sensors in Home Assistant. Ist die HA-App installiert, wird sie
     * per Deep-Link (`homeassistant://`) und explizit gesetztem Paket geöffnet — so fängt der
     * Browser den Link garantiert nicht ab und die angemeldete Sitzung der App wird genutzt (kein
     * Login-Screen). Nur ohne HA-App als Fallback die Web-Oberfläche.
     */
    private fun tapIntent(context: Context, settings: TimerSettings): Intent {
        val entityQuery = "history?entity_id=" + Uri.encode(settings.entityId)
        val haPackage = installedHaPackage(context)
        return if (haPackage != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/$entityQuery"))
                .setPackage(haPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(settings.baseUrl.trimEnd('/') + "/" + entityQuery)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Der `friendly_name` wird gekürzt: „Waschmaschine Fertigstellungszeit“ wird zu
     * „Waschmaschine“. Die Zeitangabe steht ohnehin direkt darunter, und auf der schmalen Karte
     * würde der volle Name abgeschnitten (siehe [SensorTitle]).
     */
    private fun title(context: Context, value: SensorValue?): String =
        value?.friendlyName?.let { SensorTitle.shorten(it) }?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.target_label)

    /**
     * Der friendlyName geht **ungekürzt** hinein: Gerade der Teil, den [SensorTitle] für die
     * Anzeige abschneidet, stört hier nicht — und das Gerätewort, auf das es ankommt, bleibt so
     * garantiert erhalten.
     */
    private fun iconRes(value: SensorValue?): Int = value
        ?.let { HaIcons.resolve(it.icon, it.deviceClass, it.friendlyName).drawableRes }
        ?: R.drawable.ic_home_assistant

    /** Uhrzeit im Format des Nutzers; liegt die Zielzeit nicht mehr heute, mit Datum davor. */
    private fun formatEndTime(context: Context, endTimeMs: Long, nowMs: Long): String {
        val time = DateFormat.getTimeFormat(context).format(Date(endTimeMs))
        if (DateUtils.isToday(endTimeMs)) return time
        val date = DateUtils.formatDateTime(
            context, endTimeMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
        )
        return "$date, $time"
    }

    override fun getConfig(smartspacerId: String?): Config {
        val context = provideContext()
        return Config(
            label = context.getString(R.string.target_label),
            description = context.getString(R.string.target_description),
            icon = AndroidIcon.createWithResource(context, R.drawable.ic_home_assistant),
            allowAddingMoreThanOnce = true,
            configActivity = Intent(context, SetupActivity::class.java),
            setupActivity = Intent(context, SetupActivity::class.java),
            refreshPeriodMinutes = 15,
            compatibilityState = CompatibilityState.Compatible
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        return false
    }

    override fun onProviderRemoved(smartspacerId: String) {
        val context = provideContext()
        TimerPrefs.cancelRefresh(context, smartspacerId)
        TimerPrefs.removeTargetId(context, smartspacerId)
        // Hängt kein Timer mehr daran, Takt und Anker einstellen.
        if (TimerPrefs.targetIds(context).isEmpty()) {
            TimerPrefs.cancelHeartbeat(context)
            TimerPrefs.cancelAnchor(context)
        }
        TimerPrefs.clear(context, smartspacerId)
    }

    // Paketnamen der Home-Assistant-App (Vollversion und minimale Variante)
    private val haPackages = listOf(
        "io.homeassistant.companion.android",
        "io.homeassistant.companion.android.minimal"
    )

    private fun installedHaPackage(context: Context): String? = haPackages.firstOrNull {
        try {
            context.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
