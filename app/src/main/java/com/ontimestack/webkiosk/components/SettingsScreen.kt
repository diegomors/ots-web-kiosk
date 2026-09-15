package com.ontimestack.webkiosk.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ontimestack.webkiosk.R
import com.ontimestack.webkiosk.data.KioskSettings
import com.ontimestack.webkiosk.data.KioskSettingsFactory
import com.ontimestack.webkiosk.data.Rotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    firstRun: Boolean,
    orientationNotice: String?,
    onRotationChange: (Rotation) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { KioskSettingsFactory.get(context) }
    val scope = rememberCoroutineScope()

    var homePageUrl by remember { mutableStateOf(settings.homePageUrl) }
    var rotation by remember { mutableStateOf(settings.rotation) }
    var openOnStartup by remember { mutableStateOf(settings.openOnStartup) }
    var keepInForeground by remember { mutableStateOf(settings.keepInForeground) }
    var setupPin by remember { mutableStateOf("") }
    var setupPinConfirmation by remember { mutableStateOf("") }
    var pendingPinChange by remember { mutableStateOf<String?>(null) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var setupPinError by remember { mutableStateOf<String?>(null) }
    var setupPinConfirmationError by remember { mutableStateOf<String?>(null) }
    var errorFieldTarget by remember { mutableStateOf<SettingsFieldTarget?>(null) }
    var saving by remember { mutableStateOf(false) }
    val urlFocus = remember { FocusRequester() }
    val setupPinFocus = remember { FocusRequester() }
    val setupPinConfirmationFocus = remember { FocusRequester() }
    val urlBringIntoView = remember { BringIntoViewRequester() }
    val setupPinBringIntoView = remember { BringIntoViewRequester() }
    val setupPinConfirmationBringIntoView = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }

    LaunchedEffect(Unit) {
        urlFocus.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(errorFieldTarget) {
        when (errorFieldTarget) {
            SettingsFieldTarget.URL -> {
                urlBringIntoView.bringIntoView()
                urlFocus.requestFocus()
                keyboardController?.show()
            }
            SettingsFieldTarget.PIN -> {
                setupPinBringIntoView.bringIntoView()
                setupPinFocus.requestFocus()
                keyboardController?.show()
            }
            SettingsFieldTarget.PIN_CONFIRMATION -> {
                setupPinConfirmationBringIntoView.bringIntoView()
                setupPinConfirmationFocus.requestFocus()
                keyboardController?.show()
            }
            null -> return@LaunchedEffect
        }
        errorFieldTarget = null
    }

    Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 34.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsHeader(firstRun = firstRun)

                SettingsSection(
                eyebrow = if (firstRun) "01 / PAGE" else "PAGE",
                title = "Home page",
                description = "Secure HTTPS address loaded when the kiosk starts."
            ) {
                OutlinedTextField(
                    value = homePageUrl,
                    onValueChange = {
                        homePageUrl = it
                        urlError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(urlBringIntoView)
                        .focusRequester(urlFocus),
                    label = { Text("Home Page URL") },
                    placeholder = { Text(KioskSettings.DEFAULT_HOME_PAGE_URL) },
                    supportingText = urlError?.let { message -> ({ Text(message) }) },
                    isError = urlError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = if (firstRun) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { setupPinFocus.requestFocus() },
                        onDone = { dismissKeyboard() }
                    ),
                    singleLine = true
                )
            }

                SettingsSection(
                eyebrow = if (firstRun) "02 / DISPLAY" else "DISPLAY",
                title = "Screen orientation",
                description = "Rotate the Android display so the page, touch input, and keyboard stay aligned."
            ) {
                RotationSelector(
                    rotation = rotation,
                    onRotationChange = {
                        rotation = it
                        onRotationChange(it)
                    }
                )
                orientationNotice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

                SettingsSection(
                eyebrow = if (firstRun) "03 / SECURITY" else "SECURITY",
                title = "Admin access",
                description = "Use 4 to 12 digits. The PIN is stored as a salted secure hash."
            ) {
                if (firstRun) {
                    PinField(
                        label = "Create Admin PIN",
                        value = setupPin,
                        error = setupPinError,
                        modifier = Modifier
                            .bringIntoViewRequester(setupPinBringIntoView)
                            .focusRequester(setupPinFocus),
                        imeAction = ImeAction.Next,
                        onImeAction = { setupPinConfirmationFocus.requestFocus() },
                        onValueChange = {
                            setupPin = it
                            setupPinError = null
                            setupPinConfirmationError = null
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    PinField(
                        label = "Confirm Admin PIN",
                        value = setupPinConfirmation,
                        error = setupPinConfirmationError,
                        modifier = Modifier
                            .bringIntoViewRequester(setupPinConfirmationBringIntoView)
                            .focusRequester(setupPinConfirmationFocus),
                        imeAction = ImeAction.Done,
                        onImeAction = dismissKeyboard,
                        onValueChange = {
                            setupPinConfirmation = it
                            setupPinConfirmationError = null
                        }
                    )
                } else {
                    OutlinedButton(
                        onClick = { showPinChangeDialog = true },
                        modifier = Modifier.height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            if (pendingPinChange == null) "Change Admin PIN"
                            else "PIN change ready to save"
                        )
                    }
                }
            }

                SettingsSection(
                eyebrow = if (firstRun) "04 / KIOSK" else "KIOSK",
                title = "Device behavior",
                description = "Choose how the kiosk behaves after boot or an accidental exit."
            ) {
                ToggleSetting(
                    label = "Open on Device Startup",
                    description = "Start the kiosk after the device finishes booting.",
                    checked = openOnStartup,
                    onCheckedChange = { openOnStartup = it }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ToggleSetting(
                    label = "Keep App in Foreground",
                    description = "Try to recover the kiosk after it leaves the screen.",
                    checked = keepInForeground,
                    onCheckedChange = { keepInForeground = it }
                )
            }

                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
                ) {
                if (!firstRun) {
                    TextButton(
                        enabled = !saving,
                        onClick = onClose,
                        modifier = Modifier.height(52.dp)
                    ) { Text("Cancel") }
                }
                Button(
                    enabled = !saving,
                    onClick = {
                        val normalizedUrl = KioskSettings.normalizeHomePageUrl(homePageUrl)
                        urlError = when {
                            homePageUrl.isBlank() -> "Home Page URL is required"
                            normalizedUrl == null -> "Enter a valid HTTPS URL"
                            else -> null
                        }

                        val pinToSave = if (firstRun) setupPin else pendingPinChange
                        setupPinError = when {
                            !firstRun -> null
                            setupPin.isBlank() -> "Admin PIN is required"
                            !isValidPin(setupPin) -> "PIN must contain 4 to 12 digits"
                            else -> null
                        }
                        setupPinConfirmationError = when {
                            !firstRun -> null
                            setupPinConfirmation.isBlank() -> "Confirm the Admin PIN"
                            setupPinError == null && setupPin != setupPinConfirmation ->
                                "PINs do not match"
                            else -> null
                        }

                        val invalidTarget = when {
                            urlError != null -> SettingsFieldTarget.URL
                            setupPinError != null -> SettingsFieldTarget.PIN
                            setupPinConfirmationError != null ->
                                SettingsFieldTarget.PIN_CONFIRMATION
                            else -> null
                        }
                        if (invalidTarget != null) {
                            errorFieldTarget = invalidTarget
                            return@Button
                        }
                        val validUrl = normalizedUrl ?: return@Button
                        dismissKeyboard()
                        saving = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                pinToSave?.toCharArray()?.let { pinChars ->
                                    try {
                                        settings.setAdminPin(pinChars)
                                    } finally {
                                        pinChars.fill('\u0000')
                                    }
                                }
                                settings.homePageUrl = validUrl
                                settings.rotation = rotation
                                settings.openOnStartup = openOnStartup
                                settings.keepInForeground = keepInForeground
                                settings.isConfigured = true
                            }
                            onClose()
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (firstRun) "Start Kiosk" else "Save changes")
                    }
                }
                }
            }
        }

        if (showPinChangeDialog) {
            ChangePinDialog(
                onDismiss = { showPinChangeDialog = false },
                onConfirm = {
                    pendingPinChange = it
                    showPinChangeDialog = false
                }
            )
        }
}

@Composable
private fun SettingsHeader(firstRun: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ontimestack_mark),
                    contentDescription = "OnTimeStack",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "OTS / WEB KIOSK",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Text(
            text = if (firstRun) "Configure your kiosk" else "Kiosk settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (firstRun) {
                "Complete the four steps below to start this device."
            } else {
                "Only the essential controls for this dedicated display."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SettingsSection(
    eyebrow: String,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = eyebrow,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun PinField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.length <= 12 && candidate.all(Char::isDigit)) onValueChange(candidate)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = error?.let { message -> ({ Text(message) }) },
        isError = error != null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        singleLine = true
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    val pinFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
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
    val requestConfirmationFocus = {
        confirmationFocus.requestFocus()
        keyboardController?.show()
        Unit
    }
    val submitPinChange = submit@{
        pinError = when {
            pin.isBlank() -> "Admin PIN is required"
            !isValidPin(pin) -> "PIN must contain 4 to 12 digits"
            else -> null
        }
        confirmationError = when {
            confirmation.isBlank() -> "Confirm the Admin PIN"
            pinError == null && pin != confirmation -> "PINs do not match"
            else -> null
        }

        when {
            pinError != null -> requestPinFocus()
            confirmationError != null -> requestConfirmationFocus()
            else -> {
                dismissKeyboard()
                onConfirm(pin)
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        requestPinFocus()
    }

    KioskDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "SECURITY",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text("Change Admin PIN", style = MaterialTheme.typography.headlineMedium)
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose 4 to 12 digits for administrative access.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PinField(
                    label = "New Admin PIN",
                    value = pin,
                    error = pinError,
                    modifier = Modifier.focusRequester(pinFocus),
                    imeAction = ImeAction.Next,
                    onImeAction = { requestConfirmationFocus() },
                    onValueChange = {
                        pin = it
                        pinError = null
                        confirmationError = null
                    }
                )
                PinField(
                    label = "Confirm Admin PIN",
                    value = confirmation,
                    error = confirmationError,
                    modifier = Modifier.focusRequester(confirmationFocus),
                    imeAction = ImeAction.Done,
                    onImeAction = { submitPinChange() },
                    onValueChange = {
                        confirmation = it
                        confirmationError = null
                    }
                )
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                shape = MaterialTheme.shapes.medium,
                onClick = { submitPinChange() }
            ) { Text("Change PIN") }
        }
    )
}

private fun isValidPin(pin: String): Boolean = pin.length in 4..12 && pin.all(Char::isDigit)

private enum class SettingsFieldTarget { URL, PIN, PIN_CONFIRMATION }
