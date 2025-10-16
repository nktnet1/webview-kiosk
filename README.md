> [!WARNING]
>
> Google's new developer verification requirements for 2026-2027 will
> mean the end for side-loading and many alternate stores and projects
> such as F-Droid and New Pipe.
>
> For more information, please refer to the following sources:
>
> - F-Droid: https://f-droid.org/2025/09/29/google-developer-registration-decree.html
> - Video context: https://www.youtube.com/watch?v=wRvqdLsnsKY

# Webview Kiosk

> Docs: https://webviewkiosk.nktnet.uk

<div align="center">

[<img src="./docs/public/static/images/badges/github.png" alt="Get it on GitHub" width="260px" />](https://github.com/nktnet1/webview-kiosk/releases)
[<img src="./docs/public/static/images/badges/obtanium.png" alt="Get it on Obtanium" width="260px" />](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nktnet1/webview-kiosk)
[<img src="./docs/public/static/images/badges/google-play.svg" alt="Get it on Google Play" width="260px" />](https://play.google.com/store/apps/details?id=com.nktnet.webview_kiosk)
[<img src="./docs/public/static/images/badges/f-droid.svg" alt="Get it on F-Droid" width="260px" />](https://f-droid.org/packages/uk.nktnet.webviewkiosk)
[<img src="./docs/public/static/images/badges/izzy-on-droid.svg" alt="Get it on IzzyOnDroid" width="260px" />](https://apt.izzysoft.de/fdroid/index/apk/uk.nktnet.webviewkiosk)

</div>

Webview Kiosk is a free and open-source Android application for secure,
kiosk-style web browsing.

Designed for small businesses and device owners who are looking for a simple
and standalone Kiosk solution.

## Key Features

- **Lock Task Mode (Pin):** prevent access to your device's home screen, apps and status bar
- **Protected Settings:** all configurations are secured with biometrics or device credentials
- **URL Filtering:** use regular expression to control web access through blacklisting and whitelisting
- **Export/Import:** allows for the backup and restore of user settings and configurations
- **Local files:** display an image, audio, video, or HTML file in kiosk mode from your device
- **Managed Configurations:** for fully-managed (company-owned) devices, settings can be set
  and restricted remotely through a certified MDM/EMM provider

## Additional Permissions

The Android permissions below are **optional**, and their corresponding features
are **disabled in the app settings by default**:

- CAMERA
- RECORD_AUDIO
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION

You should only enable them if they are needed by your website or web application.

These permissions will not be used for any other purpose.

## Installation Notes

From [v0.17.0](https://github.com/nktnet1/webview-kiosk/releases/tag/v0.17.0),
Google Play's [automatic protection](https://support.google.com/googleplay/android-developer/answer/10183279)
has been intentionally **disabled** to allow installations from the Aurora Store.

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
  <img src="./metadata/en-US/images/phoneScreenshots/003.phone-page-blocked.png" width="275px" alt="Phone Screenshot 3" />&nbsp;
  <img src="./metadata/en-US/images/phoneScreenshots/004.phone-settings.png" width="275px" alt="Phone Screenshot 4" />
</div>
