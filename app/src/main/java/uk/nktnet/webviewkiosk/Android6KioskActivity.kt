package uk.nktnet.webviewkiosk

/**
 * Lock-task host used only on Android 6.0 (API 23).
 *
 * Marshmallow can retain the UID that originally created a task as its lock-task owner. Keeping
 * automatic lock-on-launch inside this app-created task avoids Launcher3/system HOME ownership,
 * while the separate affinity also prevents the locked task from being the HOME task itself.
 */
class Android6KioskActivity : MainActivity()
