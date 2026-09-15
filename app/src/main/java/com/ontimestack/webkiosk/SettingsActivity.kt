package com.ontimestack.webkiosk

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ontimestack.webkiosk.app.KioskRuntime
import com.ontimestack.webkiosk.app.ScreenOrientationController
import com.ontimestack.webkiosk.components.SettingsScreen
import com.ontimestack.webkiosk.data.KioskSettingsFactory
import com.ontimestack.webkiosk.data.Rotation
import com.ontimestack.webkiosk.ui.theme.OtsSettingsTheme

class SettingsActivity : ComponentActivity() {
    private var pendingRotation: Rotation? = null
    private var orientationNotice by mutableStateOf<String?>(null)
    private val requestSystemSettingsAccess = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val applied = pendingRotation?.let { ScreenOrientationController.apply(this, it) } == true
        orientationNotice = if (applied) null else ORIENTATION_PERMISSION_NOTICE
        pendingRotation = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KioskRuntime.adminMode = true
        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        ScreenOrientationController.apply(
            this,
            KioskSettingsFactory.get(this).rotation
        )

        if (firstRun) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })
        }

        setContent {
            OtsSettingsTheme {
                SettingsScreen(
                    firstRun = firstRun,
                    orientationNotice = orientationNotice,
                    onRotationChange = ::applyOrientation,
                    onClose = ::finish
                )
            }
        }
    }

    private fun applyOrientation(rotation: Rotation) {
        if (ScreenOrientationController.apply(this, rotation)) {
            orientationNotice = null
            return
        }
        if (!ScreenOrientationController.canRequestSystemSettingsAccess(this)) {
            orientationNotice = ORIENTATION_PERMISSION_NOTICE
            return
        }

        pendingRotation = rotation
        try {
            requestSystemSettingsAccess.launch(
                ScreenOrientationController.createSystemSettingsAccessIntent(this)
            )
        } catch (_: ActivityNotFoundException) {
            pendingRotation = null
            orientationNotice = ORIENTATION_PERMISSION_NOTICE
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
        private const val ORIENTATION_PERMISSION_NOTICE =
            "Full-screen rotation on this Android TV requires one-time device provisioning. " +
                "See the README setup command."
    }
}
