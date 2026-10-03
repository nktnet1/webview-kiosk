package uk.nktnet.webviewkiosk

/**
 * Lock-task host used only on Android 6.0 (API 23) when Webview Kiosk is launched as HOME.
 *
 * Some Android 6 devices immediately leave lock task mode when the locked task is also the HOME
 * task. [MainActivity] keeps the normal HOME component unchanged and redirects only API 23 HOME
 * launches into this activity, which has a separate task affinity.
 */
class Android6KioskActivity : MainActivity()
