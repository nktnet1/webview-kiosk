"""Offline regression tests for inspecting the *compiled* release DEX."""

import contextlib
import io
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import check_release_dex as dex


CLASS_HEADER = """Class #12            -
  Class descriptor  : 'Ljava9/util/concurrent/ThreadLocalRandom;'
  Access flags      : 0x0001 (PUBLIC)
  Superclass        : 'Ljava/util/Random;'
  Instance fields   -
    #0              : (in Ljava9/util/concurrent/ThreadLocalRandom;)
      name          : 'initialized'
      type          : 'Z'
  Direct methods    -
    #0              : (in Ljava9/util/concurrent/ThreadLocalRandom;)
      name          : '<init>'
      type          : '()V'
      code          -
0010: 7010 0000 0000                         |0000: invoke-direct {v1}, Ljava/util/Random;.<init>:()V // method@0101
0014: 1210                                   |0003: const/4 v0, #int 1 // #1
0016: 5c10 0000                              |0004: iput-boolean v0, v1, Ljava9/util/concurrent/ThreadLocalRandom;.initialized:Z // field@02ce
0018: 0e00                                   |0006: return-void
  Virtual methods   -
    #0              : (in Ljava9/util/concurrent/ThreadLocalRandom;)
      name          : 'setSeed'
      type          : '(J)V'
      code          -
"""
SAFE_SET_SEED = """0020: 5510 0000                              |0000: iget-boolean v0, v1, Ljava9/util/concurrent/ThreadLocalRandom;.initialized:Z // field@02ce
0024: 3900 0300                              |0002: if-nez v0, 0005 // +0003
0028: 0e00                                   |0004: return-void
002a: 2200 0000                              |0005: new-instance v0, Ljava/lang/UnsupportedOperationException; // type@003e
002c: 7010 0000 0000                         |0007: invoke-direct {v0}, Ljava/lang/UnsupportedOperationException;.<init>:()V // method@008e
0030: 2700                                   |000a: throw v0
"""
UNGUARDED_SET_SEED = """0020: 2200 0000                              |0000: new-instance v0, Ljava/lang/UnsupportedOperationException; // type@003e
0024: 7010 0000 0000                         |0002: invoke-direct {v0}, Ljava/lang/UnsupportedOperationException;.<init>:()V // method@008e
0028: 2700                                   |0005: throw v0
"""


class ReleaseDexTest(unittest.TestCase):
    def make_class(self, set_seed=SAFE_SET_SEED):
        return CLASS_HEADER + set_seed + "      catches       : (none)\n"

    def test_original_retrostreams_guard_is_safe(self):
        dex.check_guard(self.make_class())

    def test_eqz_guard_with_return_at_branch_target_is_safe(self):
        source = SAFE_SET_SEED.replace(
            "if-nez v0, 0005 // +0003\n0028: 0e00                                   |0004: return-void\n",
            "if-eqz v0, 000a // +0008\n",
        ).replace(
            "0030: 2700                                   |000a: throw v0",
            "0030: 2700                                   |0009: throw v0\n"
            "0032: 0e00                                   |000a: return-void",
        )
        dex.check_guard(self.make_class(source))

    def test_detects_unconditional_throw_from_r8(self):
        with self.assertRaisesRegex(dex.DexCheckError, "guarded implementation"):
            dex.check_guard(self.make_class(UNGUARDED_SET_SEED))

    def test_detects_inverted_branch_during_construction(self):
        source = SAFE_SET_SEED.replace("if-nez v0, 0005", "if-eqz v0, 0005")
        with self.assertRaisesRegex(dex.DexCheckError, "during construction"):
            dex.check_guard(self.make_class(source))

    def test_rejects_unreachable_exception_after_true_branch_returns(self):
        # Both paths return, with a dead throw after the true-path return.
        source = SAFE_SET_SEED.replace(
            "002a: 2200 0000                              |0005: new-instance",
            "002a: 0e00                                   |0005: return-void\n"
            "002c: 2200 0000                              |0006: new-instance",
        )
        with self.assertRaisesRegex(dex.DexCheckError, "rejects seeding"):
            dex.check_guard(self.make_class(source))

    def test_detects_removed_initialized_field(self):
        source = self.make_class().replace("      name          : 'initialized'\n      type          : 'Z'", "")
        with self.assertRaisesRegex(dex.DexCheckError, "boolean field is missing"):
            dex.check_guard(source)

    def test_detects_removed_constructor_assignment(self):
        source = self.make_class().replace("iput-boolean", "nop")
        with self.assertRaisesRegex(dex.DexCheckError, "constructor does not initialise"):
            dex.check_guard(source)

    def test_detects_constructor_setting_initialized_false(self):
        source = self.make_class().replace("const/4 v0, #int 1", "const/4 v0, #int 0")
        with self.assertRaisesRegex(dex.DexCheckError, "initialized = true"):
            dex.check_guard(source)

    def test_detects_missing_setseed_method(self):
        source = CLASS_HEADER.split("  Virtual methods")[0]
        with self.assertRaisesRegex(dex.DexCheckError, "guarded implementation"):
            dex.check_guard(source)

    def test_extracts_only_target_class_from_large_dexdump(self):
        output = (
            "Class #11            -\n  Class descriptor  : 'Lother/Type;'\n"
            "  Direct methods    -\n    #0              : (in Lother/Type;)\n"
            "      name          : 'setSeed'\n      type          : '(J)V'\n"
            + self.make_class()
            + "Class #13            -\n  Class descriptor  : 'Lnext/Type;'\n"
        )
        result = dex.get_target_class(iter(output.splitlines(keepends=True)))
        self.assertEqual(self.make_class().split("\n", 1)[1], result)
        self.assertIsNone(dex.get_target_class(iter("Class #1\n  Class descriptor  : 'Lother/Type;'\n".splitlines(True))))

    def test_rejects_wrong_superclass(self):
        source = self.make_class().replace("Superclass        : 'Ljava/util/Random;'", "Superclass        : 'Ljava/lang/Object;'")
        with self.assertRaisesRegex(dex.DexCheckError, "no longer extends"):
            dex.check_guard(source)

    def test_scans_multidex_apk_and_verifies_target_class(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "release.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", "dex1")
                archive.writestr("classes2.dex", "dex2")
            calls = []

            def fake_dump(tool, path):
                calls.append((tool, path.name))
                return self.make_class() if path.name == "classes2.dex" else None

            with patch.object(dex, "dump_class", side_effect=fake_dump):
                result = dex.check_apk(apk, "/sdk/dexdump")
            self.assertEqual("classes2.dex", result)
            self.assertEqual([("/sdk/dexdump", "classes.dex"), ("/sdk/dexdump", "classes2.dex")], calls)

    def test_fails_closed_if_r8_renames_or_removes_class(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "release.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", "dex")
            with patch.object(dex, "dump_class", return_value=None):
                with self.assertRaisesRegex(dex.DexCheckError, "renamed, removed, or merged"):
                    dex.check_apk(apk, "dexdump")

    def test_rejects_missing_or_invalid_apk(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "release.apk"
            with self.assertRaisesRegex(dex.DexCheckError, "APK not found"):
                dex.check_apk(path, "dexdump")
            path.write_bytes(b"not an APK")
            with self.assertRaisesRegex(dex.DexCheckError, "File is not a zip file"):
                dex.check_apk(path, "dexdump")

    def test_rejects_apk_without_dex(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "release.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", "test")
            with self.assertRaisesRegex(dex.DexCheckError, "no classes"):
                dex.check_apk(path, "dexdump")

    def test_cli_returns_nonzero_on_regression(self):
        with patch.object(dex, "check_apk", side_effect=dex.DexCheckError("broken setSeed")):
            with contextlib.redirect_stderr(io.StringIO()) as stderr:
                rc = dex.main(["test.apk", "--dexdump", "/sdk/dexdump"])
        self.assertEqual(1, rc)
        self.assertIn("FAIL: release DEX check: broken setSeed", stderr.getvalue())

    def test_dexdump_failure_fails_closed(self):
        with patch.object(dex.subprocess, "Popen") as popen:
            process = popen.return_value.__enter__.return_value
            process.stdout = io.StringIO("invalid DEX version\n")
            process.wait.return_value = 1
            with self.assertRaisesRegex(dex.DexCheckError, "invalid DEX version"):
                dex.dump_class("dexdump", Path("test.dex"))

    def test_finds_latest_sdk_dexdump_without_path_entry(self):
        with tempfile.TemporaryDirectory() as folder:
            sdk = Path(folder)
            for version in ("34.0.0", "36.0.0", "35.0.0"):
                tool = sdk / "build-tools" / version / "dexdump"
                tool.parent.mkdir(parents=True)
                tool.touch(mode=0o755)
                tool.chmod(0o755)
            with patch.object(dex.shutil, "which", return_value=None), patch.dict(
                os.environ, {"ANDROID_HOME": folder, "ANDROID_SDK_ROOT": folder}
            ):
                self.assertEqual(str(sdk / "build-tools/36.0.0/dexdump"), dex.find_dexdump())

    def test_default_cli_builds_smoke_apk_then_inspects_it(self):
        with (
            patch.object(dex, "build_smoke_apk", return_value=Path("/test/smoke.apk")) as build,
            patch.object(dex, "find_dexdump", return_value="dexdump"),
            patch.object(dex, "check_apk", return_value="classes2.dex") as check,
        ):
            with contextlib.redirect_stdout(io.StringIO()) as out:
                status = dex.main([])
        self.assertEqual(status, 0)
        build.assert_called_once_with()
        check.assert_called_once_with(Path("/test/smoke.apk"), "dexdump")
        self.assertIn("PASS:", out.getvalue())

    def test_existing_apk_cli_skips_build(self):
        with (
            patch.object(dex, "build_smoke_apk") as build,
            patch.object(dex, "check_apk", return_value="classes.dex") as check,
        ):
            with contextlib.redirect_stdout(io.StringIO()):
                status = dex.main(["existing.apk", "--dexdump", "dexdump"])
        self.assertEqual(status, 0)
        build.assert_not_called()
        check.assert_called_once_with(Path("existing.apk").resolve(), "dexdump")

    def test_build_uses_minified_smoke_release_and_cached_gradle(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "gradlew").touch()
            apk = root / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk"
            apk.parent.mkdir(parents=True)
            apk.touch()
            with (
                patch.object(dex, "ROOT", root),
                patch.object(dex, "SMOKE_APK", apk),
                patch.object(
                    dex.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)
                ) as run,
            ):
                with contextlib.redirect_stdout(io.StringIO()):
                    result = dex.build_smoke_apk()
            self.assertEqual(result, apk)
            run.assert_called_once_with(
                [str(root / "gradlew"), ":app:assembleSmokeRelease", "--build-cache", "--console=plain"],
                cwd=root, timeout=1200, check=False,
            )
            self.assertNotIn(":app:assembleRelease", run.call_args.args[0])

    def test_build_fails_if_gradle_fails_even_when_old_apk_exists(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "gradlew").touch()
            apk = root / "existing-smoke.apk"
            apk.touch()
            with (
                patch.object(dex, "ROOT", root),
                patch.object(dex, "SMOKE_APK", apk),
                patch.object(dex.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)),
            ):
                with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(
                    dex.DexCheckError, "Gradle exit 1"
                ):
                    dex.build_smoke_apk()

    def test_build_fails_on_missing_output_even_if_gradle_succeeded(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "gradlew").touch()
            with (
                patch.object(dex, "ROOT", root),
                patch.object(dex, "SMOKE_APK", root / "missing.apk"),
                patch.object(dex.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)),
            ):
                with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(
                    dex.DexCheckError, "APK was not found"
                ):
                    dex.build_smoke_apk()

    def test_ci_builds_smoke_release_in_existing_android_job(self):
        root = Path(__file__).resolve().parents[2]
        tests = (root / ".github/workflows/tests.yaml").read_text()
        gradle_setup = tests.index("gradle/actions/setup-gradle@")
        jvm = tests.index("sh gradlew :adblock:test :app:testDebugUnitTest")
        smoke = tests.index("run: python3 scripts/r8_audit.py --require-mapping --fail-on-warnings --json build/reports/r8/smokeRelease.json")
        self.assertLess(gradle_setup, jvm)
        self.assertLess(jvm, smoke)
        self.assertIn("if: ${{ !startsWith(github.ref, 'refs/tags/') }}", tests)
        self.assertNotIn("emulator-runner", tests)
        build_type = (root / "app/build.gradle.kts").read_text()
        self.assertIn('create("smokeRelease")', build_type)
        self.assertIn('signingConfig = signingConfigs.getByName("debug")', build_type)
        self.assertIn("isDebuggable = false", build_type)

    def test_release_ci_checks_the_actual_apk_before_upload(self):
        release = (Path(__file__).resolve().parents[2] / ".github/workflows/release.yaml").read_text()
        build = release.index("run: sh gradlew :app:assembleRelease :app:assembleDebug")
        check = release.index("python3 scripts/r8_audit.py app/build/outputs/apk/release/app-release.apk")
        publish = release.index("name: Store release APK for publication")
        self.assertLess(build, check)
        self.assertLess(check, publish)
        self.assertNotIn("assembleSmokeRelease", release)
        self.assertNotIn("emulator-runner", release)
        self.assertIn("name: Upload R8 diagnostics for analysis", release)
        self.assertIn("path: build/reports/r8/release.json", release)
        self.assertIn("--require-mapping --fail-on-warnings --json build/reports/r8/release.json", release)


if __name__ == "__main__":
    unittest.main()
