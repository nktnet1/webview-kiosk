"""Ensure the Python graphics helper preserves every ImageMagick conversion."""

import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts.maintenance import update_graphics

ROOT = Path(__file__).resolve().parents[2]


class GraphicsUpdateTests(unittest.TestCase):
    def test_uses_repository_root_and_original_size_options(self):
        with (
            patch.object(update_graphics.shutil, "which", return_value="/usr/bin/magick"),
            patch.object(update_graphics.subprocess, "run", return_value=SimpleNamespace(returncode=0)) as runner,
        ):
            self.assertEqual(0, update_graphics.main())
        self.assertEqual(3, runner.call_count)
        self.assertEqual(ROOT, update_graphics.ROOT)
        for call, args in zip(runner.call_args_list, update_graphics.COMMANDS):
            self.assertEqual(["/usr/bin/magick", *args], call.args[0])
            self.assertEqual(ROOT, call.kwargs["cwd"])
            self.assertIs(False, call.kwargs["check"])
        self.assertEqual(("docs/src/assets/icon.svg", "-background", "none", "-size", "512x512",
                          "metadata/en-US/images/icon.png"), update_graphics.COMMANDS[0])
        self.assertEqual(("misc/featureGraphic.svg", "-background", "none", "-resize", "x640",
                          "-gravity", "center", "-crop", "1280x640+0+0", "+repage",
                          "misc/githubSocialPreview.png"), update_graphics.COMMANDS[2])

    def test_missing_magick_exits_before_running_anything(self):
        with (
            patch.object(update_graphics.shutil, "which", return_value=None),
            patch.object(update_graphics.subprocess, "run") as runner,
        ):
            self.assertEqual(1, update_graphics.main())
        runner.assert_not_called()

    def test_failed_conversion_propagates_exit_and_stops(self):
        with (
            patch.object(update_graphics.shutil, "which", return_value="magick"),
            patch.object(update_graphics.subprocess, "run",
                         side_effect=[SimpleNamespace(returncode=0), SimpleNamespace(returncode=13)]) as runner,
        ):
            self.assertEqual(13, update_graphics.main())
        self.assertEqual(2, runner.call_count)

    def test_missing_binary_after_discovery_returns_failure(self):
        with (
            patch.object(update_graphics.shutil, "which", return_value="/missing/magick"),
            patch.object(update_graphics.subprocess, "run", side_effect=OSError("not found")),
        ):
            self.assertEqual(1, update_graphics.main())


if __name__ == "__main__":
    unittest.main()
