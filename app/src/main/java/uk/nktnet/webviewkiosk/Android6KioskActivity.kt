package uk.nktnet.webviewkiosk

/**
 * Lock-task host used only on Android 6.0 (API 23).
 *
 * Some Android 6 devices immediately leave lock task mode when the locked task is
 * also the HOME task. [HomeActivity] redirects HOME launches here, while
 * [MainActivity] redirects explicit lock requests. This host uses a separate task
 * affinity so the system does not immediately exit lock task mode.
 */
class Android6KioskActivity : MainActivity()
