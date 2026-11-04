# The instrumentation APK contains only a Java subclass of this smoke-only
# runner. Preserve its binary name, public constructor and inherited lifecycle
# methods across the APK boundary. All probe dependencies are optimised with
# smokeRelease itself; production release builds do not include this file.
# This class is declared in src/smokeRelease (not src/main), so the IDE
# inspection may not resolve it when another build variant is selected.
# noinspection ShrinkerUnresolvedReference
-keep class uk.nktnet.webviewkiosk.r8.SmokeReleaseInstrumentation { public *; }
