package com.ontimestack.webkiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ontimestack.webkiosk.MainActivity
import com.ontimestack.webkiosk.app.StayOnTopServiceStarter
import com.ontimestack.webkiosk.data.KioskSettingsFactory

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val settings = KioskSettingsFactory.get(context)
        if (!settings.isConfigured || !settings.openOnStartup) return

        if (settings.keepInForeground) StayOnTopServiceStarter.ensureRunning(context)
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
