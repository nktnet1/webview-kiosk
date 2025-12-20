> [!WARNING]
>
> Google's new developer verification requirements for 2026-2027 will
> mean the end for side-loading and many alternate stores and projects
> such as F-Droid and New Pipe.
>
> For more information, please refer to the following sources:
>
> - F-Droid: https://f-droid.org/en/2025/10/28/sideloading.html
> - Keep Android Open: https://keepandroidopen.org
> - Video context: https://www.youtube.com/watch?v=wRvqdLsnsKY

# Webview Kiosk

> Docs: https://webviewkiosk.nktnet.uk

<div align="center">

[<img src="./docs/public/static/images/badges/github.png" alt="Get it on GitHub" width="260px" />](https://github.com/nktnet1/webview-kiosk/releases)
[<img src="./docs/public/static/images/badges/obtainium.png" alt="Get it on Obtainium" width="260px" />](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nktnet1/webview-kiosk)
[<img src="./docs/public/static/images/badges/google-play.svg" alt="Get it on Google Play" width="260px" />](https://play.google.com/store/apps/details?id=com.nktnet.webview_kiosk)
[<img src="./docs/public/static/images/badges/f-droid.svg" alt="Get it on F-Droid" width="260px" />](https://f-droid.org/packages/uk.nktnet.webviewkiosk)
[<img src="./docs/public/static/images/badges/izzy-on-droid.svg" alt="Get it on IzzyOnDroid" width="260px" />](https://apt.izzysoft.de/fdroid/index/apk/uk.nktnet.webviewkiosk)

</div>

Webview Kiosk is a free and open-source Android application for secure,
kiosk-style web browsing.

It can function as digital signage, a kid-friendly restricted browser, an
immersive web reader, an interactive signup form, a home assistant dashboard,
or simply a wall clock.

Designed for small businesses and device owners who are looking for a simple
and standalone Kiosk solution.

## Key Features

### Core / Standalone

- <b>Lock Task Mode (Pin):</b> prevent access to your device's home screen, apps and status bar
- <b>Secure Settings:</b> configurations are protected by biometrics, device credentials or custom password
- <b>URL Filtering:</b> use regular expressions to control web access through a URL blacklist and whitelist
- <b>Export/Import:</b> backup and restore user settings in Base64 or JSON format
- <b>Local files:</b> display an image, audio, video, or HTML file in kiosk mode from your device
- <b>Default Launcher:</b> functions as a home app that and can launch other apps

### Remote Management / Enterprise

- <b>Message Queuing Telemetry Transport (MQTT)</b>
  - Monitor events, update settings, execute commands and build custom
    automations using the API
  - Requires an MQTT broker (e.g. Mosquitto, EMQX or HiveMQ)
- <b>Managed Configurations (App Restrictions)</b>
  - For fully-managed (company-owned) devices, settings can be remotely
    configured via an MDM/EMM provider
  - Advanced users using a device policy controller (e.g. Test DPC or OwnDroid)
    can also enforce configurations locally

## Permissions

- <b>INTERNET</b>: for general web browsing
- <b>CAMERA</b>: (optional) for use with web apps that requires photo/video capture
- <b>RECORD_AUDIO</b>: (optional) for use with web apps that requires audio capture
- <b>MODIFY_AUDIO_SETTINGS</b>: for routing audio (microphone will not work without this)
- <b>ACCESS_FINE_LOCATION</b>: (optional) for web apps that needs precise geolocation
- <b>ACCESS_COARSE_LOCATION</b>: (optional) for web apps that needs approximate geolocation
- <b>QUERY_ALL_PACKAGES</b>: to find launchable apps, device owners and lock task packages
- <b>POST_NOTIFICATIONS</b>: (optional) for lock task mode (kiosk) launches and MQTT
- <b>FOREGROUND_SERVICE</b>: for persistent notification and management with lock task and MQTT
- <b>FOREGROUND_SERVICE_SPECIAL_USE</b>: provides a return mechanism for opening apps in kiosk
- <b>USE_BIOMETRIC</b>: replaces USE_FINGERPRINT from Android 9 onwards
- <b>USE_FINGERPRINT</b>: use fingerprint hardware when accessing settings or unlocking kiosk
- <b>API (Dhizuku)</b>: to request shared device owner privileges

## Installation Notes

From [v0.17.0](https://github.com/nktnet1/webview-kiosk/releases/tag/v0.17.0),
Google Play's [automatic protection](https://support.google.com/googleplay/android-developer/answer/10183279)
has been intentionally <b>disabled</b> to allow installations from the Aurora Store.

From [v0.15.7](https://github.com/nktnet1/webview-kiosk/releases/tag/v0.15.7),
the package name for all installation sources except the Google Play Store has
changed from `com.nktnet.webview_kiosk` to `uk.nktnet.webviewkiosk`.

## Contact

- support@webviewkiosk.nktnet.uk

## License

This project is licensed under the GNU Affero General Public License v3.0 or later.

See the [LICENSE](./LICENSE) file for details.

## Screenshots

<div align="center">
  <img src="./metadata/en-US/images/phoneScreenshots/001.phone-default.png" width="275px" alt="Phone Screenshot 1" />&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/002.phone-locked.png" width="275px" alt="Phone Screenshot 2"/>&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/003.phone-kiosk-control-panel.png" width="275px" alt="Phone Screenshot 3" />&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/004.phone-page-blocked.png" width="275px" alt="Phone Screenshot 4" />&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/005.phone-settings.png" width="275px" alt="Phone Screenshot 5" />&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/006.phone-settings-mqtt.png" width="275px" alt="Phone Screenshot 6" />
</div>
