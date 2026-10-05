package uk.nktnet.webviewkiosk

/**
 * Lock-task host used only on Android 6.0 (API 23).
 *
 * Some Android 6 devices immediately leave lock task mode when the locked task is also the HOME
 * task. [MainActivity] keeps the normal HOME component unchanged and redirects API 23 HOME
 * launches and lock requests into this activity, which has a separate task affinity.
 */
class Android6KioskActivity : MainActivity()
