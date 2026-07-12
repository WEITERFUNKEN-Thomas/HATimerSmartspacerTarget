package com.noirdraco.hasensorsmartspacertarget

import android.content.Context
import com.kieronquinn.app.smartspacer.sdk.receivers.SmartspacerTargetUpdateReceiver

class HomeAssistantUpdateReceiver : SmartspacerTargetUpdateReceiver() {

    override fun onRequestSmartspaceTargetUpdate(
        context: Context,
        requestTargets: List<RequestTarget>
    ) {
        val authority = "${context.packageName}.target.homeassistant"
        requestTargets
            .filter { it.authority == authority }
            .forEach { HomeAssistantPrefs.enqueueRefresh(context, it.smartspacerId) }

        // Anwesenheits-Requirements bekommen von Smartspacer keinen eigenen Update-Broadcast.
        // Wir nutzen diesen zuverlässigen, von Smartspacer getakteten Weckruf, um auch sie
        // aufzufrischen (deutlich verlässlicher als WorkManager-Periodic im Standby/Doze).
        HomeAssistantPrefs.presenceIds(context)
            .forEach { HomeAssistantPrefs.enqueuePresenceRefresh(context, it) }
    }
}
