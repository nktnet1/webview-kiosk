"""Check CI runtime prefetch without a Maven connection or an Android SDK."""

import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from scripts.android.export_android_test_assets import (
    ExportError, ROBOLECTRIC_SDK_VERSIONS, verify_maven_checksum,
)
from scripts.ci import prefetch_robolectric as prefetch


class RobolectricPrefetchTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name) / "repository"

    def test_prefetches_both_artifacts_for_every_test_sdk(self):
        with mock.patch.object(prefetch, "robolectric_artifact") as download:
            prefetch.prefetch(self.repository)
        self.assertEqual(
            [mock.call(self.repository, version, extension,
                       repository_url=prefetch.GOOGLE_MAVEN_MIRROR_URL)
             for version in ROBOLECTRIC_SDK_VERSIONS.values()
             for extension in ("pom", "jar")],
            download.call_args_list,
        )

    def test_valid_local_maven_artifacts_are_reused_without_network(self):
        version = "example-runtime-i7"
        directory = self.repository / "org/robolectric/android-all-instrumented" / version
        directory.mkdir(parents=True)
        for extension in ("pom", "jar"):
            artifact = directory / f"android-all-instrumented-{version}.{extension}"
            contents = f"verified {extension}".encode()
            artifact.write_bytes(contents)
            artifact.with_name(artifact.name + ".sha512").write_text(
                hashlib.sha512(contents).hexdigest()
            )
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", {28: version}),
              mock.patch("scripts.android.export_android_test_assets.download") as download):
            prefetch.prefetch(self.repository)
        download.assert_not_called()

    def test_mirror_downloads_are_preferred_and_verified(self):
        requested = []

        def download(url, target):
            requested.append(url)
            payload = b"runtime data" if ".jar" in url else b"pom data"
            target.write_bytes(hashlib.sha512(payload).hexdigest().encode()
                               if url.endswith(".sha512") else payload)

        version = "example-runtime-i7"
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", {28: version}),
              mock.patch("scripts.android.export_android_test_assets.download",
                         side_effect=download)):
            prefetch.prefetch(self.repository)
        self.assertEqual(4, len(requested))
        self.assertTrue(all(url.startswith(prefetch.GOOGLE_MAVEN_MIRROR_URL)
                            for url in requested))
        directory = self.repository / "org/robolectric/android-all-instrumented" / version
        for extension in ("pom", "jar"):
            name = directory / f"android-all-instrumented-{version}.{extension}"
            self.assertTrue(verify_maven_checksum(name))

    def test_rate_limited_mirror_falls_back_to_central_for_one_jar(self):
        requested = []

        def download(url, target):
            requested.append(url)
            if url.startswith(prefetch.GOOGLE_MAVEN_MIRROR_URL) and url.endswith(".jar"):
                raise ExportError("HTTP Error 429: Too Many Requests")
            payload = b"runtime data" if ".jar" in url else b"pom data"
            target.write_bytes(hashlib.sha512(payload).hexdigest().encode()
                               if url.endswith(".sha512") else payload)

        version = "example-runtime-i7"
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", {28: version}),
              mock.patch("scripts.android.export_android_test_assets.download",
                         side_effect=download),
              mock.patch.object(prefetch.time, "sleep") as sleep):
            prefetch.prefetch(self.repository)
        sleep.assert_not_called()
        google = prefetch.GOOGLE_MAVEN_MIRROR_URL
        central = prefetch.ROBOLECTRIC_MAVEN_URL
        self.assertEqual([google, google, google, central, central],
                         [url.split("/example-runtime-i7/")[0] for url in requested])
        directory = self.repository / "org/robolectric/android-all-instrumented" / version
        for extension in ("pom", "jar"):
            name = directory / f"android-all-instrumented-{version}.{extension}"
            self.assertTrue(verify_maven_checksum(name))

    def test_bad_mirror_checksum_is_not_cached_and_central_repairs_it(self):
        requested = []

        def download(url, target):
            requested.append(url)
            if url.endswith(".sha512"):
                payload = (b"0" * 128 if url.startswith(prefetch.GOOGLE_MAVEN_MIRROR_URL)
                           else hashlib.sha512(b"valid jar").hexdigest().encode())
            else:
                payload = b"valid jar"
            target.write_bytes(payload)

        version = "example-runtime-i7"
        with mock.patch("scripts.android.export_android_test_assets.download",
                        side_effect=download):
            prefetch.fetch_artifact(self.repository, version, "jar")
        self.assertEqual(4, len(requested))
        self.assertTrue(requested[0].startswith(prefetch.GOOGLE_MAVEN_MIRROR_URL))
        self.assertTrue(requested[2].startswith(prefetch.ROBOLECTRIC_MAVEN_URL))
        directory = self.repository / "org/robolectric/android-all-instrumented" / version
        name = directory / f"android-all-instrumented-{version}.jar"
        self.assertTrue(verify_maven_checksum(name))
        self.assertFalse(list(directory.glob("*.tmp*")))

    def test_both_sources_are_identified_when_downloads_fail(self):
        with mock.patch.object(prefetch, "robolectric_artifact",
                               side_effect=[ExportError("mirror 503"), ExportError("central 429")]) as fetch:
            with self.assertRaisesRegex(ExportError, "Google Maven Central mirror.*Maven Central"):
                prefetch.fetch_artifact(self.repository, "a", "jar")
        self.assertEqual(
            [mock.call(self.repository, "a", "jar", repository_url=url)
             for _, url in prefetch.RUNTIME_REPOSITORIES], fetch.call_args_list,
        )

    def test_retries_only_failed_artifact_with_bounded_backoff(self):
        sdk_versions = {21: "a", 22: "b"}
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", sdk_versions),
              mock.patch.object(prefetch, "fetch_artifact",
                                side_effect=[ExportError("429"), ExportError("503"),
                                             None, None, None, None, None]) as download,
              mock.patch.object(prefetch.time, "sleep") as sleep):
            prefetch.prefetch(self.repository)
        self.assertEqual([mock.call(15), mock.call(30)], sleep.call_args_list)
        self.assertEqual(
            [mock.call(self.repository, "a", "pom")] * 3 +
            [mock.call(self.repository, "a", "jar"),
             mock.call(self.repository, "b", "pom"),
             mock.call(self.repository, "b", "jar")],
            download.call_args_list,
        )

    def test_permanent_download_failure_is_fatal(self):
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", {28: "bad"}),
              mock.patch.object(prefetch, "fetch_artifact",
                                side_effect=ExportError("no network")) as download,
              mock.patch.object(prefetch.time, "sleep") as sleep):
            self.assertEqual(1, prefetch.main(["--repository", str(self.repository)]))
        self.assertEqual(3, download.call_count)
        self.assertEqual([mock.call(15), mock.call(30)], sleep.call_args_list)

    def test_programming_errors_are_not_retried_or_masked(self):
        with (mock.patch.object(prefetch, "ROBOLECTRIC_SDK_VERSIONS", {28: "bad"}),
              mock.patch.object(prefetch, "fetch_artifact",
                                side_effect=ValueError("programming bug")) as download,
              mock.patch.object(prefetch.time, "sleep") as sleep):
            with self.assertRaisesRegex(ValueError, "programming bug"):
                prefetch.prefetch(self.repository)
        download.assert_called_once()
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
