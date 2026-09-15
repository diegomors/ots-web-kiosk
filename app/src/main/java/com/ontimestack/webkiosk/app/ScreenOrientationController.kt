package com.ontimestack.webkiosk.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ontimestack.webkiosk.data.Rotation

object ScreenOrientationController {
    /**
     * Applies the selected orientation. Phones can rotate a single Activity normally. Android TV
     * keeps its display landscape, so quarter turns must rotate the device display itself to keep
     * the IME and touch coordinate space aligned with the kiosk.
     *
     * @return true when the requested orientation is fully applied, or false when Android TV still
     * needs the user to grant Modify system settings access.
     */
    fun apply(activity: Activity, rotation: Rotation): Boolean {
        if (!activity.isTelevision()) {
            applyActivityOrientation(activity, rotation)
            return true
        }

        if (rotation == Rotation.ROTATION_0 && !canWriteSystemSettings(activity)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            return true
        }

        val displayRotationApplied = isDisplayRotationApplied(activity, rotation)
        if (!displayRotationApplied && canWriteSystemSettings(activity)) {
            val resolver = activity.contentResolver
            val sensorLocked = Settings.System.putInt(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            val rotationUpdated = Settings.System.putInt(
                resolver,
                Settings.System.USER_ROTATION,
                rotation.toSystemRotation()
            )
            if (sensorLocked && rotationUpdated) {
                applyActivityOrientation(activity, rotation)
                return true
            }
        }

        if (displayRotationApplied) {
            applyActivityOrientation(activity, rotation)
            return true
        }

        // Avoid Android TV compatibility letterboxing: it rescales pointer coordinates and makes
        // touchscreen input land away from the visible controls.
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        return false
    }

    fun canRequestSystemSettingsAccess(activity: Activity): Boolean =
        activity.isTelevision() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !canWriteSystemSettings(activity)

    fun createSystemSettingsAccessIntent(activity: Activity): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${activity.packageName}")
    )

    private fun applyActivityOrientation(activity: Activity, rotation: Rotation) {
        val requestedOrientation = rotation.toRequestedOrientation()
        if (activity.requestedOrientation != requestedOrientation) {
            activity.requestedOrientation = requestedOrientation
        }
    }

    private fun canWriteSystemSettings(activity: Activity): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(activity)
        } else {
            activity.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun isDisplayRotationApplied(activity: Activity, rotation: Rotation): Boolean {
        val resolver = activity.contentResolver
        val sensorLocked = Settings.System.getInt(
            resolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 0
        val currentRotation = Settings.System.getInt(
            resolver,
            Settings.System.USER_ROTATION,
            0
        )
        return sensorLocked && currentRotation == rotation.toSystemRotation()
    }

    private fun Activity.isTelevision(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun Rotation.toRequestedOrientation(): Int = when (this) {
    Rotation.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    Rotation.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    Rotation.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    Rotation.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
}

internal fun Rotation.toSystemRotation(): Int = degrees / 90
