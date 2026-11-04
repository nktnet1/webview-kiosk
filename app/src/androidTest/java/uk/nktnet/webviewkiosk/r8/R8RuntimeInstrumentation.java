package uk.nktnet.webviewkiosk.r8;

/**
 * Android loads this entry point from the instrumentation APK. All probe logic
 * lives in smokeRelease, where R8 can optimise it together with the app and
 * its Kotlin dependencies instead of exposing a fragile cross-APK Kotlin ABI.
 */
public final class R8RuntimeInstrumentation extends SmokeReleaseInstrumentation {
}
