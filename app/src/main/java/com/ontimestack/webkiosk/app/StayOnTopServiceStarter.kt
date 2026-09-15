package com.ontimestack.webkiosk.app

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ontimestack.webkiosk.data.KioskSettingsFactory
import com.ontimestack.webkiosk.service.StayOnTopService

object StayOnTopServiceStarter {
    fun ensureRunning(context: Context) {
        val settings = KioskSettingsFactory.get(context)
        if (!settings.keepInForeground || KioskRuntime.exitRequested || StayOnTopService.isRunning) {
            return
        }

        val intent = Intent(context, StayOnTopService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
