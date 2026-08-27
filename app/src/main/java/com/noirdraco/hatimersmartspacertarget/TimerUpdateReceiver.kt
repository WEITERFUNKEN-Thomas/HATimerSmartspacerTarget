package com.noirdraco.hatimersmartspacertarget

import android.content.Context
import com.kieronquinn.app.smartspacer.sdk.receivers.SmartspacerTargetUpdateReceiver

class TimerUpdateReceiver : SmartspacerTargetUpdateReceiver() {

    override fun onRequestSmartspaceTargetUpdate(
        context: Context,
        requestTargets: List<RequestTarget>
    ) {
        val authority = "${context.packageName}.target.hatimer"
        requestTargets
            .filter { it.authority == authority }
            .forEach { TimerPrefs.enqueueRefresh(context, it.smartspacerId) }
    }
}
