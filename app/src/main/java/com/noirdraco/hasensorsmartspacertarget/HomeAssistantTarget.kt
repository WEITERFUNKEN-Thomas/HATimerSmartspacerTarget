package com.noirdraco.hasensorsmartspacertarget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants
import com.kieronquinn.app.smartspacer.sdk.model.CompatibilityState
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import android.graphics.drawable.Icon as AndroidIcon

class HomeAssistantTarget : SmartspacerTargetProvider() {

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val context = provideContext()
        // Kein Netzwerk-Call hier — es wird nur der vom Worker gecachte Wert gelesen
        val value = HomeAssistantPrefs.loadLastValue(context, smartspacerId)
        val settings = HomeAssistantPrefs.loadSettings(context, smartspacerId)

        // Selbstheilung: fürs Auffrischen per Doze-Heartbeat registrieren (auch für Targets, die
        // vor dieser Änderung eingerichtet wurden). Schreibt nur, wenn die ID noch fehlt.
        if (settings != null) HomeAssistantPrefs.addTargetId(context, smartspacerId)

        // Anzeige-Filter: Ist einer gesetzt und passt der Zustand nicht, gibt es kein Target —
        // eine leere Liste blendet es im Smartspace komplett aus. Solange noch kein Wert im Cache
        // liegt, wird ebenfalls nichts angezeigt: Der Platzhalter „Lädt …“ wäre sonst ein Treffer,
        // den der Nutzer mit dem Filter ja gerade ausschließen wollte.
        if (settings != null && ShowRule.isSet(settings.showOnlyIf)) {
            if (value == null || !ShowRule.matches(value.state, settings.showOnlyIf)) {
                return emptyList()
            }
        }

        val title: String
        val subtitle: String
        val iconRes: Int
        if (value != null) {
            title = value.friendlyName
            subtitle = if (isUnavailable(value.state)) {
                context.getString(R.string.target_unavailable)
            } else {
                listOf(value.state, value.unit)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
            // Sensor-eigenes Symbol aus HA (mdi:... bzw. device_class), sonst Haus-Fallback
            iconRes = HaIcons.resolve(value.icon, value.deviceClass).drawableRes
        } else {
            title = settings?.entityId ?: context.getString(R.string.target_label)
            subtitle = context.getString(R.string.target_loading)
            iconRes = R.drawable.ic_home_assistant
        }

        // Tap öffnet den Verlauf dieses Sensors in Home Assistant. Ist die HA-App installiert,
        // wird sie per Deep-Link (homeassistant://) und explizit gesetztem Paket geöffnet — so
        // fängt der Browser den Link garantiert nicht ab und die angemeldete Sitzung der App
        // wird genutzt (kein Login-Screen). Nur ohne HA-App als Fallback die Web-Oberfläche.
        val onClickIntent = if (settings != null) {
            val entityQuery = "history?entity_id=" + Uri.encode(settings.entityId)
            val haPackage = installedHaPackage(context)
            if (haPackage != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/$entityQuery"))
                    .setPackage(haPackage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(settings.baseUrl.trimEnd('/') + "/" + entityQuery)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(context, SetupActivity::class.java)
                .putExtra(SmartspacerConstants.EXTRA_SMARTSPACER_ID, smartspacerId)
        }

        val target = TargetTemplate.Basic(
            id = "ha_$smartspacerId",
            componentName = ComponentName(context, HomeAssistantTarget::class.java),
            title = Text(title),
            subtitle = Text(subtitle),
            icon = Icon(AndroidIcon.createWithResource(context, iconRes)),
            onClick = TapAction(intent = onClickIntent)
        ).create().apply {
            canBeDismissed = false
        }
        return listOf(target)
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
        HomeAssistantPrefs.cancelRefresh(context, smartspacerId)
        HomeAssistantPrefs.removeTargetId(context, smartspacerId)
        // Hängt nichts mehr am Heartbeat (kein Target, keine Bedingung), den Alarm einstellen.
        if (HomeAssistantPrefs.hasNothingToRefresh(context)) {
            HomeAssistantPrefs.cancelHeartbeat(context)
        }
        HomeAssistantPrefs.clear(context, smartspacerId)
    }

    private fun isUnavailable(state: String): Boolean =
        state.trim().lowercase() in setOf("unavailable", "unknown", "none", "")

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
