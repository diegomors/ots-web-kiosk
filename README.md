# ots-web-kiosk

Minimal Android Web kiosk for Android TV, Google TV, USB touchscreens, mouse, and keyboard. The installed application is named **OTS Kiosk** and uses the package `com.ontimestack.webkiosk`.

The project is based on the MIT-licensed [Screenlite Android Web Kiosk](https://github.com/screenlite/android-web-kiosk). OTS Kiosk uses real Android display orientation so the page, software keyboard, and touch coordinates stay aligned at 0°, 90°, 180°, or 270°.

## Current status

| Area | Current state |
| --- | --- |
| Build | Debug and signed release builds pass. `lintDebug`, `lintVitalRelease`, and the orientation unit tests pass. |
| Identity | Project `ots-web-kiosk`, package `com.ontimestack.webkiosk`, installed name `OTS Kiosk`. |
| UI | First-run setup, Settings, PIN, Admin Menu, launcher icon, and TV banner use the OnTimeStack cube mark and the OTS **Cyber Minimal Dark** palette adapted from `ots-storybook`. |
| WebView | HTTPS, JavaScript, cookies, web storage, redirects, media playback, geolocation, and render-process recovery implemented. |
| Touch and rotation | Manual View transforms were removed. The WebView, native screens, keyboard, and touch surface now share the Android display coordinate system. |
| Native-screen rotation | Setup, Settings, loading/error states, Admin PIN/Menu, Change PIN, WebView, and keyboard follow the selected real 0°/90°/180°/270° orientation. |
| Network recovery | Wi-Fi loss and reconnection validated in the AVD; the page reloads after connectivity returns. |
| Admin access | Short Back is consumed. A physical Back press of approximately two seconds, or Android's native long-press event, opens the PIN dialog. |
| Boot | `BOOT_COMPLETED` reached the app and started its foreground service in the legacy API 31 AVD, but Android 12 blocked the background Activity launch. |
| Foreground recovery | One best-effort return attempt plus a persistent notification; Android can block the automatic return. |
| Target hardware | Xiaomi TV Box S 2nd Gen + Dell P2424HT validation is still required. |

## Kiosk behavior

- Full-screen immersive WebView with system bars hidden when Android permits it.
- Screen kept awake while an Activity is running.
- Touch, mouse, keyboard, pointer, swipe, scroll, drag, and standard WebView input are not disabled on TV devices.
- Real Android orientation keeps the WebView, native OTS screens, dialogs, keyboard, and touch coordinates in the same coordinate space.
- Android TV/Google TV display rotation uses one-time **Modify system settings** access because TV firmware otherwise letterboxes portrait Activities and rescales touch coordinates.
- Automatic reload uses bounded exponential backoff and reacts to real network changes without pinging an external health-check service.
- WebView renderer failure recreates the WebView safely.
- The default Home Page is `https://ontimestack.com` and only HTTPS Home Page URLs are accepted.

## Administrative flow

While the Home Page is visible:

1. A short Back press does nothing.
2. Hold the physical Back button for approximately two seconds.
3. Enter the Admin PIN.
4. Choose **Settings**, **Exit Kiosk**, or **Cancel**.

Settings and Exit Kiosk are unavailable before successful PIN validation. Entering the PIN dialog, Android permission UI, or Settings temporarily suspends foreground recovery. Returning to the kiosk enables it again.

On first launch, the app requires the Home Page URL, rotation, PIN creation/confirmation, and both kiosk toggles before it can start.

Form validation is applied before saving:

- Home Page URL is required, must be syntactically valid, and must use HTTPS.
- Admin PIN is required and accepts only 4 to 12 digits.
- PIN confirmation is required and must exactly match the first PIN field.
- Errors appear below the corresponding field and the first invalid field is brought into view and focused.
- The software keyboard uses **Next** between related fields and **Done** closes the keyboard on the final field.

## Visible settings

The Settings screen intentionally exposes only:

- Home Page URL
- Screen Orientation: 0°, 90°, 180°, or 270°
- Change Admin PIN
- Open on Device Startup
- Keep App in Foreground

## Provision rotation on Android TV

Phones and tablets normally honor Activity orientation without additional access. Android TV and Google TV commonly keep the physical display in landscape and letterbox portrait Activities. That behavior also rescales pointer coordinates, so it cannot provide reliable touchscreen kiosk input.

OTS Kiosk requests **Modify system settings** only to lock auto-rotation and set Android's `USER_ROTATION` to the selected 0°/90°/180°/270° value. No network or account permission is involved. If the TV exposes the standard permission screen, selecting a non-zero orientation opens it automatically.

Google TV images that do not expose that screen must be provisioned once over ADB after installing the APK:

```sh
adb shell appops set com.ontimestack.webkiosk WRITE_SETTINGS allow
```

Restart OTS Kiosk or select the orientation again. Without this access, the app keeps the TV in its native full-screen orientation and shows a setup notice instead of using the touch-breaking letterboxed fallback.

## Security, privacy, and network behavior

- The PIN is never stored as plaintext. It uses PBKDF2-HMAC-SHA256, a random salt, 150,000 iterations, and constant-time comparison.
- Android backup and device-to-device extraction are disabled for app data.
- HTTP navigation, mixed content, local file access, and local content access are disabled.
- WebView remote debugging is enabled only in debug builds.
- Geolocation requires an Android runtime permission and is granted only to the configured HTTPS Home Page origin.
- The app contains no native HTTP client, analytics, advertising, crash-reporting SDK, telemetry, remote activation, or license-validation request.
- Runtime network traffic consists of the configured WebView page, its resources and redirects, and any traffic initiated by that page.
- The local MIT license file does not perform validation and cannot remotely disable the app.

See [AUDIT.md](AUDIT.md) for the detailed review, changed-file summary, removed functionality, and validation evidence.

## Web platform support

The kiosk uses Android System WebView, which is the Android browser engine embedded without Chrome's tabs and browser chrome. It can provide the web APIs needed by a normal web application, but it cannot guarantee exact feature parity with the latest desktop or mobile Chrome: support also depends on the Android System WebView version installed on the device, HTTPS/security requirements, and native Android permission bridges.

Verified inside the running debug WebView:

- `localStorage` and `sessionStorage`: write/read/remove round trips passed;
- IndexedDB: database open and deletion passed;
- `fetch`, WebSocket, cookies, Web Crypto, Service Worker, geolocation, and `mediaDevices`: exposed in the secure page context;
- `window.isSecureContext`: `true` for `https://ontimestack.com`.

The app already enables JavaScript, DOM Storage, cookies, autoplay, and geolocation for the configured trusted origin. It intentionally keeps mixed HTTP content, `file://` access, automatic pop-ups, and multiple windows disabled. Browser features that require additional native UI or permissions—such as file selection, managed downloads, camera/microphone capture, Web Notifications, Bluetooth/USB, or opening new tabs—are not currently bridged because they are not required by the kiosk flow.

Keep Android System WebView/Chrome updated on the final device. Use runtime feature detection in the website for optional APIs instead of assuming the engine is identical on every Android firmware.

The Mizz device-activation page requires JavaScript syntax that Android System WebView 91 cannot parse (static class initialization blocks). On the legacy Android 12/API 31 Google TV image, hydration therefore stops before the activation request. The current Android 16/API 36 AVD uses WebView 143: the same activation flow loads, submits the device token, and redirects to the final kiosk route successfully. No token is embedded in the application or repository.

References: [Embed web content in an app](https://developer.android.com/develop/ui/views/layout/webapps/embed-web-content-in-app), [WebView feature support](https://developer.android.com/reference/androidx/webkit/WebViewFeature), and [debug WebView with Chrome DevTools](https://developer.android.com/develop/ui/views/layout/webapps/debug-chrome-devtools).

## Mizz self-service startup error

The message **“Não foi possível iniciar o autoatendimento. Verifique a conexão e tente novamente.”** was produced by `mizz-ui` when `supabase.auth.signInAnonymously()` was rejected by the production Auth configuration. It was not a `localStorage` or OTS Kiosk failure.

This production configuration was corrected on 2026-09-15. Anonymous Auth sessions are enabled for the kiosk, while a Before User Created Auth Hook continues to reject permanent self-service signups. RLS and server-action guards prevent anonymous users from enumerating profiles, creating establishments, joining staff/admin flows, or using upload endpoints. Kiosk checkout still requires the revocable device credential and creates orders through the validated server boundary. The complete flow was revalidated in the API 36/WebView 143 AVD without the startup error.

## Build a debug APK

Use Android Studio's bundled JDK and an Android SDK containing API 36:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
bash ./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build a release APK

Place the signing keystore at `app/release.keystore`, then provide the signing variables:

```sh
export KEYSTORE_PASSWORD='...'
export KEY_ALIAS='...'
export KEY_PASSWORD='...'
bash ./gradlew clean assembleRelease
```

Keep the release keystore securely. Every future update of `com.ontimestack.webkiosk` must use the same signing key.

### Current local release key

A dedicated 4,096-bit RSA release key has been generated for OTS Kiosk. The keystore is stored locally at `app/release.keystore`, is excluded by `.gitignore`, and is not included in the source archive. Its alias is `ots-web-kiosk-release` and its password is stored in the macOS login Keychain under service `ots-web-kiosk-release-keystore` and account `com.ontimestack.webkiosk`.

The generated `app-release.apk` is signed with this key and is not debuggable. Preserve both the keystore and its password: losing either prevents future APK updates from being installed over the current release.

## Sideload on Xiaomi TV Box S 2nd Gen

Enable Developer options and USB debugging, connect through USB or ADB over the network, and run:

```sh
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.ontimestack.webkiosk WRITE_SETTINGS allow
adb shell am start -n com.ontimestack.webkiosk/.MainActivity
```

The OTS package ID allows this fork to coexist with the original Screenlite app during testing.

Before production use, update Android System WebView/Chrome on the target device. The legacy API 31 Google TV emulator image includes WebView 91, which cannot parse JavaScript required by the current Mizz activation page.

## Google TV development device

The current local AVD is named `OTS_Kiosk_Google_TV_API_36` and uses Google TV Android 16 ARM64, 1920×1080, multitouch, and Android System WebView 143:

```sh
$HOME/Library/Android/sdk/emulator/emulator @OTS_Kiosk_Google_TV_API_36
```

The broader interaction validation below was performed across this AVD and the legacy `OTS_Kiosk_Google_TV_API_31` AVD. The Mizz activation compatibility check was specifically performed on API 36/WebView 143:

- installation and first-run setup;
- PIN rejection, unlock, Admin Menu, and Settings;
- short and long Back behavior;
- Cyber Minimal Dark interface;
- OnTimeStack launcher/UI branding;
- real display rotation for Setup, Settings, Change PIN, Admin PIN/Menu, WebView, keyboard, and loading/error states;
- required-field, invalid HTTPS URL, PIN-length, PIN-confirmation, first-error focus, and keyboard Next/Done form behavior;
- tap navigation in the Mizz WebView and native input focus with the real 90° display coordinate system;
- functional `localStorage`, `sessionStorage`, and IndexedDB plus browser-API availability from inside the WebView;
- Wi-Fi disconnection and automatic page recovery;
- boot receiver and foreground-service startup on the legacy API 31 AVD;
- loading and activation of the Mizz device-access URL followed by the expected redirect to the final kiosk route.
- provisioned real display rotation at 0°, 90°, 180°, and 270° without losing the Settings form state;
- portrait software keyboard alignment at 90° and safe full-screen fallback with a visible provisioning notice when `WRITE_SETTINGS` is unavailable;
- release-build touch navigation from the Mizz order-mode screen to the catalog with native, untransformed coordinates.

The AVD does not replace validation with the actual Dell P2424HT USB HID touchscreen.

## Required target-hardware validation

Validate on the Xiaomi TV Box S 2nd Gen with the Dell P2424HT:

1. Tap, scroll, swipe, drag, HTML inputs, pointer events, and touch events at 0°.
2. Repeat at 90° and confirm physical and visual coordinates match exactly.
3. Repeat at 180°.
4. Repeat at 270° and confirm physical and visual coordinates match exactly.
5. Disconnect and reconnect Wi-Fi/Ethernet and confirm the Home Page recovers.
6. Confirm geolocation on the configured HTTPS origin.
7. Confirm wrong PIN, Settings, Exit Kiosk, restart, and package update behavior.
8. Confirm boot and foreground behavior on the Xiaomi firmware.

## Android lockdown limitation

The current build prevents accidental exit with immersive mode and Back interception. It does **not** guarantee that Home, Recents, or application switching can never be used.

Android's supported hard-lock mechanism is Lock Task Mode with the package allowlisted by a Device Policy Controller/Device Owner. Screen pinning without a Device Owner can be exited by the user. A Device Owner, root, Accessibility Service, overlay permission, or aggressive relaunch loop is intentionally not included in the current implementation.

Because modern Android versions can block background Activity starts, **Open on Device Startup** and **Keep App in Foreground** are best-effort options. On the legacy API 31 AVD, the foreground service starts, but the system blocks the automatic Activity launch. The ongoing notification remains available as a manual return path.

Reference: [Android Lock Task Mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode) and [background foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## License

MIT. The original copyright and license notice are preserved in `LICENSE.md`.
