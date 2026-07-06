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
    }
}
