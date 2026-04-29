package com.ducktask.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ducktask.app.util.PendingOverlayRecovery

class DeviceUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        PendingOverlayRecovery.recoverIfPossible(context, "device_unlock")
    }
}
