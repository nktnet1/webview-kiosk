"""Offline tests for the Android 16 / 16 KiB release startup smoke runner."""

import contextlib
import io
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.r8 import release_startup_smoke as smoke


class FakeTools:
    def __init__(self):
        self.calls = []
        self.api = "36"
        self.devices = ["emulator-5554"]
        self.device_apis = {}
        self.page_size = "16384"
        self.fingerprint = "google/sdk_gphone16k_arm64/emu64a16k:16/test:userdebug/dev-keys"
        self.qemu = "1"
        self.badging = (
            "package: name='com.nktnet.webview_kiosk' versionCode='140' "
            "versionName='0.26.26'\n"
            "launchable-activity: name='uk.nktnet.webviewkiosk.MainActivity' label=''\n"
        )
        self.logs = ""
        self.pid = "1234\n"
        self.start_output = "Starting: Intent { cmp=... }\nStatus: ok\n"
        self.start_status = 0
        self.install_status = 0

    def __call__(self, args, *, timeout=30, check=True):
        args = [str(arg) for arg in args]
        self.calls.append(args)
        rest = args[1:]
        serial = None
        if rest[:1] == ["-s"]:
            serial = rest[1]
            rest = rest[2:]
        code = 0
        output = ""
        error = ""
        if args[0] == "aapt" and args[1:3] == ["dump", "badging"]:
            output = self.badging
        elif rest == ["devices"]:
            output = "List of devices attached\n" + "".join(
                f"{device}\tdevice\n" for device in self.devices
            )
        elif rest[:2] == ["shell", "getprop"]:
            properties = {
                "ro.build.version.sdk": self.device_apis.get(serial, self.api),
                "ro.build.fingerprint": self.fingerprint,
                "ro.kernel.qemu": self.qemu,
                "ro.boot.qemu": self.qemu,
            }
            output = properties[rest[2]] + "\n"
        elif rest == ["shell", "getconf", "PAGESIZE"]:
            output = self.page_size + "\n"
        elif rest[:2] == ["install", "-r"]:
            code = self.install_status
            output = "Success\n" if code == 0 else "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"
        elif rest[:4] == ["shell", "am", "start", "-W"]:
            code = self.start_status
            output = self.start_output
        elif rest[:3] == ["logcat", "-d", "-b"]:
            output = self.logs
        elif rest[:3] == ["shell", "pidof", "com.nktnet.webview_kiosk"]:
            output = self.pid
            code = 0 if output else 1
        elif rest[:3] == ["shell", "am", "force-stop"] or rest == ["logcat", "-c"]:
            pass
        else:
            raise AssertionError(f"Unexpected external command: {args}")
        if check and code != 0:
            raise smoke.SmokeTestError(f"Command failed: {' '.join(args)}\n{error or output}")
        return subprocess.CompletedProcess(args, code, output, error)


class ReleaseStartupSmokeTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "play-release.apk"
        self.apk.write_bytes(b"fake APK; parsed by test double")
        self.tools = FakeTools()
        mock_tools = patch.object(smoke, "find_sdk_tool", side_effect=lambda name, **_: name)
        mock_run = patch.object(smoke, "command", side_effect=self.tools)
        mock_sleep = patch.object(smoke.time, "sleep")
        for item in (mock_tools, mock_run, mock_sleep):
            item.start()
            self.addCleanup(item.stop)

    def run_smoke(self, serial=None):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            smoke.smoke_test(self.apk, serial=serial)
        return output.getvalue()

    def adb_calls(self, *prefix):
        return [call for call in self.tools.calls if call[0] == "adb" and call[1:1 + len(prefix)] == list(prefix)]

    def test_single_cold_launch_of_play_package_and_stays_alive(self):
        result = self.run_smoke()
        self.assertIn("PASS:", result)
        self.assertIn("com.nktnet.webview_kiosk", result)
        starts = [call for call in self.tools.calls if "start" in call]
        self.assertEqual(
            [["adb", "-s", "emulator-5554", "shell", "am", "start", "-W", "-n",
              "com.nktnet.webview_kiosk/uk.nktnet.webviewkiosk.MainActivity"]],
            starts,
        )
        calls = self.tools.calls
        self.assertLess(
            calls.index(["adb", "-s", "emulator-5554", "shell", "am", "force-stop", "com.nktnet.webview_kiosk"]),
            calls.index(["adb", "-s", "emulator-5554", "logcat", "-c"]),
        )
        self.assertLess(calls.index(["adb", "-s", "emulator-5554", "logcat", "-c"]), calls.index(starts[0]))

    def test_serial_is_forwarded_to_every_adb_command(self):
        self.run_smoke(serial="emulator-5554")
        for call in self.tools.calls:
            if call[0] == "adb" and call[1:] != ["devices"]:
                self.assertEqual(["-s", "emulator-5554"], call[1:3])

    def test_rejects_wrong_android_version_before_installing(self):
        self.tools.api = "35"
        with self.assertRaisesRegex(smoke.SmokeTestError, "API 35"):
            self.run_smoke(serial="emulator-5554")
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_rejects_4k_page_emulator_before_installing(self):
        self.tools.page_size = "4096"
        with self.assertRaisesRegex(smoke.SmokeTestError, "4096-byte pages"):
            self.run_smoke(serial="emulator-5554")
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_rejects_physical_device_before_installing(self):
        self.tools.qemu = "0"
        self.tools.fingerprint = "google/panther/panther:16/test:user/release-keys"
        with self.assertRaisesRegex(smoke.SmokeTestError, "Requires an Android 16"):
            self.run_smoke(serial="emulator-5554")
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_detects_target_crash_even_if_process_was_restarted(self):
        self.tools.logs = (
            "11-02 18:00:00.000 E AndroidRuntime: FATAL EXCEPTION: main\n"
            "11-02 18:00:00.001 E AndroidRuntime: Process: com.nktnet.webview_kiosk, PID: 42\n"
            "11-02 18:00:00.002 E AndroidRuntime: java.lang.ExceptionInInitializerError\n"
            "11-02 18:00:00.003 E AndroidRuntime: Caused by: java.lang.UnsupportedOperationException\n"
        )
        with self.assertRaisesRegex(smoke.SmokeTestError, "UnsupportedOperationException"):
            self.run_smoke()

    def test_ignores_other_packages_androidruntime_crashes(self):
        self.tools.logs = (
            "E AndroidRuntime: FATAL EXCEPTION: main\n"
            "E AndroidRuntime: Process: com.example.other, PID: 42\n"
            "E AndroidRuntime: java.lang.ExceptionInInitializerError\n"
        )
        self.assertIn("PASS:", self.run_smoke())

    def test_missing_app_process_fails_even_without_crash_log(self):
        self.tools.pid = ""
        with self.assertRaisesRegex(smoke.SmokeTestError, "No running process"):
            self.run_smoke()

    def test_failed_activity_launch_is_not_reported_as_success(self):
        self.tools.start_status = 1
        self.tools.start_output = "Error type 3\nError: Activity class does not exist.\n"
        with self.assertRaisesRegex(smoke.SmokeTestError, "Activity launch failed"):
            self.run_smoke()

    def test_rejects_debuggable_apk(self):
        self.tools.badging += "application-debuggable\n"
        with self.assertRaisesRegex(smoke.SmokeTestError, "Refusing a debuggable APK"):
            self.run_smoke()
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_install_failure_does_not_uninstall_or_clear_user_data(self):
        self.tools.install_status = 1
        with self.assertRaisesRegex(smoke.SmokeTestError, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"):
            self.run_smoke()
        self.assertFalse(any("uninstall" in call or "pm clear" in " ".join(call) for call in self.tools.calls))

    def test_missing_apk_fails_before_any_tool_is_invoked(self):
        with self.assertRaisesRegex(smoke.SmokeTestError, "APK not found"):
            smoke.smoke_test(self.apk.with_name("missing.apk"))
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_builds_smoke_release_automatically_when_no_apk_is_provided(self):
        with patch.object(smoke, "build_smoke_apk", return_value=self.apk) as build:
            with contextlib.redirect_stdout(io.StringIO()) as output:
                smoke.smoke_test()
        build.assert_called_once_with()
        self.assertIn("PASS:", output.getvalue())

    def test_explicit_apk_skips_build(self):
        with patch.object(smoke, "build_smoke_apk") as build:
            self.run_smoke()
        build.assert_not_called()

    def test_does_not_build_without_a_compatible_emulator(self):
        self.tools.api = "35"
        with patch.object(smoke, "build_smoke_apk") as build:
            with self.assertRaisesRegex(smoke.SmokeTestError, "No running Android 16"):
                smoke.smoke_test()
        build.assert_not_called()

    def test_selects_the_only_compatible_emulator(self):
        self.tools.devices = ["emulator-5554", "emulator-5556"]
        self.tools.device_apis["emulator-5554"] = "35"
        result = self.run_smoke()
        self.assertIn("Device: emulator-5556", result)
        self.assertIn("PASS:", result)

    def test_multiple_compatible_emulators_require_serial(self):
        self.tools.devices = ["emulator-5554", "emulator-5556"]
        with self.assertRaisesRegex(smoke.SmokeTestError, "Choose one with --serial"):
            self.run_smoke()
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_disconnected_serial_fails_without_installing(self):
        with self.assertRaisesRegex(smoke.SmokeTestError, "is not connected"):
            self.run_smoke(serial="emulator-5556")
        self.assertFalse(any("install" in call for call in self.tools.calls))

    def test_build_invokes_isolated_minified_variant_without_release_keystore(self):
        with patch.object(smoke, "SMOKE_APK", self.apk), patch.object(
            smoke.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)
        ) as run:
            built = smoke.build_smoke_apk()
        self.assertEqual(self.apk, built)
        invocation = run.call_args.args[0]
        self.assertEqual(":app:assembleSmokeRelease", invocation[1])
        self.assertNotIn(":app:assembleRelease", invocation)
        self.assertNotIn("keystore.jks", " ".join(invocation))
        self.assertEqual(smoke.ROOT, run.call_args.kwargs["cwd"])

    def test_gradle_failure_is_clear_and_does_not_install_apk(self):
        with patch.object(smoke.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaisesRegex(smoke.SmokeTestError, "smokeRelease build failed"):
                smoke.build_smoke_apk()

    def test_build_fails_if_gradle_succeeds_without_producing_apk(self):
        with patch.object(smoke, "SMOKE_APK", self.apk.with_name("missing.apk")), patch.object(
            smoke.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)
        ):
            with self.assertRaisesRegex(smoke.SmokeTestError, "APK is missing"):
                smoke.build_smoke_apk()


if __name__ == "__main__":
    unittest.main()
