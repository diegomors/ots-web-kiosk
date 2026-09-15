package com.ontimestack.webkiosk.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import com.ontimestack.webkiosk.MainActivity
import com.ontimestack.webkiosk.R
import com.ontimestack.webkiosk.app.KioskRuntime
import com.ontimestack.webkiosk.data.KioskSettingsFactory

class StayOnTopService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recoveryAttempted = false
    private val checkTask = object : Runnable {
        override fun run() {
            val settings = KioskSettingsFactory.get(this@StayOnTopService)
            if (!settings.keepInForeground || KioskRuntime.exitRequested) {
                stopSelf()
                return
            }
            if (isActivityVisible || KioskRuntime.adminMode) {
                recoveryAttempted = false
            } else if (!recoveryAttempted) {
                recoveryAttempted = true
                bringAppToFront()
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForegroundService()
        isRunning = true
        handler.post(checkTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = KioskSettingsFactory.get(this)
        return if (settings.keepInForeground && !KioskRuntime.exitRequested) {
            START_STICKY
        } else {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkTask)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bringAppToFront() {
        val movedExistingTask = runCatching {
            val activityManager =
                getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appTask = activityManager.appTasks.firstOrNull() ?: return@runCatching false
            appTask.moveToFront()
            true
        }.getOrDefault(false)
        if (movedExistingTask) return

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FOREGROUND_RECOVERY, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createOreoNotification())
        } else {
            @Suppress("DEPRECATION")
            startForeground(
                NOTIFICATION_ID,
                Notification.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("Keeping kiosk in foreground")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentIntent(createContentIntent())
                    .build()
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createOreoNotification(): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Keeping kiosk in foreground")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(createContentIntent())
            .setOngoing(true)
            .build()
    }

    private fun createContentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FOREGROUND_RECOVERY, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            flags
        )
    }

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isActivityVisible = false

        private const val CHECK_INTERVAL_MS = 10_000L
        private const val NOTIFICATION_CHANNEL_ID = "ots_web_kiosk_foreground"
        private const val NOTIFICATION_ID = 1
    }
}
