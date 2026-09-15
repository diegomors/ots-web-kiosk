package com.ontimestack.webkiosk.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import com.ontimestack.webkiosk.service.StayOnTopService

class KioskApplication : Application() {
    private var resumedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                StayOnTopService.isActivityVisible = resumedActivities > 0
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                StayOnTopService.isActivityVisible = resumedActivities > 0
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
