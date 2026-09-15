package com.ontimestack.webkiosk

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.GeolocationPermissions
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ontimestack.webkiosk.app.FullScreenHelper
import com.ontimestack.webkiosk.app.KioskRuntime
import com.ontimestack.webkiosk.app.ScreenOrientationController
import com.ontimestack.webkiosk.app.StayOnTopServiceStarter
import com.ontimestack.webkiosk.components.MainScreen
import com.ontimestack.webkiosk.components.KioskDialog
import com.ontimestack.webkiosk.data.KioskSettingsFactory
import com.ontimestack.webkiosk.service.StayOnTopService
import com.ontimestack.webkiosk.ui.theme.OtsKioskTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val settings by lazy { KioskSettingsFactory.get(this) }
    private val backHandler = Handler(Looper.getMainLooper())
    private var adminScreen by mutableStateOf(AdminScreen.NONE)
    private var pendingGeolocationRequest: PendingGeolocationRequest? = null

    private val openSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        KioskRuntime.adminMode = false
        if (!settings.isConfigured) {
            finishAndRemoveTask()
        } else {
            updateForegroundService()
            recreate()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val request = pendingGeolocationRequest
        pendingGeolocationRequest = null
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        request?.callback?.invoke(request.origin, granted, false)
        KioskRuntime.adminMode = false
        updateForegroundService()
    }

    private val showAdminAccess = Runnable {
        if (settings.isConfigured) {
            KioskRuntime.adminMode = true
            adminScreen = AdminScreen.PIN
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_FOREGROUND_RECOVERY, false) && KioskRuntime.exitRequested) {
            finishAndRemoveTask()
            return
        }
        KioskRuntime.exitRequested = false
        ScreenOrientationController.apply(this, settings.rotation)
        FullScreenHelper.enableImmersiveMode(window)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            OtsKioskTheme {
                if (settings.isConfigured) {
                    MainScreen(activity = this, modifier = Modifier.fillMaxSize())
                }
                AdminDialogs(
                    screen = adminScreen,
                    verifyPin = settings::verifyAdminPin,
                    onScreenChange = {
                        adminScreen = it
                        KioskRuntime.adminMode = it != AdminScreen.NONE
                        if (it == AdminScreen.NONE) updateForegroundService()
                    },
                    onSettings = {
                        adminScreen = AdminScreen.NONE
                        KioskRuntime.adminMode = true
                        stopService(Intent(this, StayOnTopService::class.java))
                        openSettings.launch(
                            Intent(this, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_FIRST_RUN, false)
                        )
                    },
                    onExit = ::exitKiosk
                )
            }
        }

        if (!settings.isConfigured) {
            KioskRuntime.adminMode = true
            openSettings.launch(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_FIRST_RUN, true)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        FullScreenHelper.enableImmersiveMode(window)
        if (settings.isConfigured && !KioskRuntime.adminMode) updateForegroundService()
    }

    override fun onPause() {
        backHandler.removeCallbacks(showAdminAccess)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && settings.isConfigured) FullScreenHelper.enableImmersiveMode(window)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) {
                backHandler.postDelayed(showAdminAccess, ADMIN_LONG_PRESS_MS)
            } else if (event.isLongPress) {
                backHandler.removeCallbacks(showAdminAccess)
                showAdminAccess.run()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backHandler.removeCallbacks(showAdminAccess)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        backHandler.removeCallbacks(showAdminAccess)
        pendingGeolocationRequest?.let { it.callback.invoke(it.origin, false, false) }
        pendingGeolocationRequest = null
        super.onDestroy()
    }

    fun requestGeolocationPermission(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        pendingGeolocationRequest?.let { it.callback.invoke(it.origin, false, false) }
        pendingGeolocationRequest = PendingGeolocationRequest(origin, callback)
        KioskRuntime.adminMode = true
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun updateForegroundService() {
        if (settings.keepInForeground && !KioskRuntime.exitRequested) {
            StayOnTopServiceStarter.ensureRunning(this)
        } else {
            stopService(Intent(this, StayOnTopService::class.java))
        }
    }

    private fun exitKiosk() {
        adminScreen = AdminScreen.NONE
        KioskRuntime.exitRequested = true
        KioskRuntime.adminMode = false
        stopService(Intent(this, StayOnTopService::class.java))
        finishAndRemoveTask()
    }

    private data class PendingGeolocationRequest(
        val origin: String,
        val callback: GeolocationPermissions.Callback
    )

    companion object {
        const val EXTRA_FOREGROUND_RECOVERY = "foreground_recovery"
        private const val ADMIN_LONG_PRESS_MS = 2_000L
    }
}

private enum class AdminScreen { NONE, PIN, MENU }

@Composable
private fun AdminDialogs(
    screen: AdminScreen,
    verifyPin: (CharArray) -> Boolean,
    onScreenChange: (AdminScreen) -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    var pin by remember(screen) { mutableStateOf("") }
    var pinError by remember(screen) { mutableStateOf<String?>(null) }
    var verifyingPin by remember(screen) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pinFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }
    val requestPinFocus = {
        pinFocus.requestFocus()
        keyboardController?.show()
        Unit
    }
    val submitPin = submitPin@{
        if (verifyingPin) return@submitPin
        pinError = when {
            pin.isBlank() -> "Admin PIN is required"
            pin.length !in 4..12 -> "PIN must contain 4 to 12 digits"
            else -> null
        }
        if (pinError != null) {
            requestPinFocus()
            return@submitPin
        }

        dismissKeyboard()
        val candidate = pin.toCharArray()
        verifyingPin = true
        scope.launch {
            val valid = try {
                withContext(Dispatchers.Default) { verifyPin(candidate) }
            } finally {
                candidate.fill('\u0000')
            }
            verifyingPin = false
            pin = ""
            if (valid) {
                onScreenChange(AdminScreen.MENU)
            } else {
                pinError = "Incorrect PIN"
                requestPinFocus()
            }
        }
        Unit
    }

    LaunchedEffect(screen) {
        when (screen) {
            AdminScreen.PIN -> requestPinFocus()
            AdminScreen.MENU -> menuFocus.requestFocus()
            AdminScreen.NONE -> Unit
        }
    }

    if (screen == AdminScreen.NONE) return

    when (screen) {
        AdminScreen.NONE -> Unit
        AdminScreen.PIN -> KioskDialog(
            onDismissRequest = { onScreenChange(AdminScreen.NONE) },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminBrandLabel("OTS / ADMIN ACCESS")
                    Text("Unlock kiosk", style = MaterialTheme.typography.headlineMedium)
                }
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Enter the Admin PIN to change settings or exit.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 12 && it.all(Char::isDigit)) {
                                pin = it
                                pinError = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(pinFocus),
                        label = { Text("Admin PIN") },
                        isError = pinError != null,
                        supportingText = pinError?.let { message -> ({ Text(message) }) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitPin() }
                        ),
                        singleLine = true
                    )
                }
            },
            actions = {
                TextButton(onClick = {
                    pin = ""
                    pinError = null
                    onScreenChange(AdminScreen.NONE)
                }) { Text("Cancel") }
                Button(
                    enabled = !verifyingPin,
                    modifier = Modifier.height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { submitPin() }
                ) { Text(if (verifyingPin) "Checking…" else "Unlock") }
            }
        )
        AdminScreen.MENU -> KioskDialog(
            onDismissRequest = { onScreenChange(AdminScreen.NONE) },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminBrandLabel("OTS / ADMIN")
                    Text("Kiosk controls", style = MaterialTheme.typography.headlineMedium)
                }
            },
            content = {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Configuration is unlocked for this session.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(menuFocus)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Settings")
                    }
                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Exit Kiosk")
                    }
                }
            },
            actions = {
                TextButton(onClick = { onScreenChange(AdminScreen.NONE) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminBrandLabel(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ontimestack_mark),
            contentDescription = "OnTimeStack",
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
