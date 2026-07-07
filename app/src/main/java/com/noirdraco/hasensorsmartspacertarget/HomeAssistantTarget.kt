package com.noirdraco.hasensorsmartspacertarget

import android.content.ComponentName
import android.content.Intent
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

        val configIntent = Intent(context, SetupActivity::class.java)
            .putExtra(SmartspacerConstants.EXTRA_SMARTSPACER_ID, smartspacerId)

        val target = TargetTemplate.Basic(
            id = "ha_$smartspacerId",
            componentName = ComponentName(context, HomeAssistantTarget::class.java),
            title = Text(title),
            subtitle = Text(subtitle),
            icon = Icon(AndroidIcon.createWithResource(context, iconRes)),
            onClick = TapAction(intent = configIntent)
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
        HomeAssistantPrefs.clear(context, smartspacerId)
    }

    private fun isUnavailable(state: String): Boolean =
        state.trim().lowercase() in setOf("unavailable", "unknown", "none", "")
}
