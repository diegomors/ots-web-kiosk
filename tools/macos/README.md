# OTS Kiosk Provisioner for macOS

The provisioner installs or updates OTS Kiosk, grants the one-time Android TV
`WRITE_SETTINGS` app-op, verifies it, and starts the app. It does not use root,
unlock the bootloader, uninstall the app, or erase its settings.

## Package for use

Place these two files in the same folder:

- `OTS-Kiosk-Provisioner.command`
- `OTS-Kiosk.apk`

Then double-click `OTS-Kiosk-Provisioner.command` and follow the prompts.

Before starting, enable Developer Options and Wireless Debugging on the Xiaomi
TV Box and connect the Mac and TV to the same trusted network. Android 11 and
newer normally shows a pairing address, pairing code, and a separate connection
address. Older Android TV firmware commonly uses `<TV-IP>:5555`.

If macOS does not open the file, right-click it and choose **Open**. If its
executable bit was removed while copying it, run:

```sh
chmod +x OTS-Kiosk-Provisioner.command
```

After a successful run, open OTS Kiosk Settings and select the intended screen
orientation again. Wireless Debugging may then be disabled on the TV.

## Automation

For diagnostics or repeatable provisioning, prompts can be bypassed:

```sh
OTS_TV_TARGET="192.168.1.50:5555" \
OTS_APK_PATH="/path/to/OTS-Kiosk.apk" \
OTS_SKIP_PAUSE=1 \
./OTS-Kiosk-Provisioner.command
```

Optional variables are `OTS_PAIR_TARGET` for Android 11+ pairing and
`OTS_ADB_PATH` for a custom `adb` executable.
