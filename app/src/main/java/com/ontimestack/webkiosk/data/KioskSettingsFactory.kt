package com.ontimestack.webkiosk.data

import android.content.Context

object KioskSettingsFactory {
    @Volatile
    private var instance: KioskSettings? = null

    fun get(context: Context): KioskSettings = instance ?: synchronized(this) {
        instance ?: KioskSettings(context.applicationContext).also { instance = it }
    }
}
