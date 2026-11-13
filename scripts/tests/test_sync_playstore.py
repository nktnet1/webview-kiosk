"""Exercise Play Store synchronization against disposable local Git repositories."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "maintenance/sync_playstore.py"


class SyncPlaystoreTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="sync-playstore-test-")
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.repo = root / "repo"
        self.repo.mkdir()
        self.remote = root / "remote.git"
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.gradle = self.repo / "app/build.gradle.kts"
        self.gradle.parent.mkdir()
        self.gradle.write_text('applicationId = "uk.nktnet.webviewkiosk"\n')
        self.note = self.repo / "notes.txt"
        self.note.write_text("committed note\n")
        self.git("add", ".")
        self.git("commit", "-m", "baseline")
        self.git("init", "--bare", str(self.remote))
        self.git("remote", "add", "origin", str(self.remote))
        self.git("switch", "-c", "playstore")
        self.git("push", "-u", "origin", "playstore")
        self.git("switch", "main")

    def git(self, *args):
        return subprocess.check_output(
            ["git", *args], cwd=self.repo, text=True, stderr=subprocess.PIPE,
        ).strip()

    def sync(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT)], cwd=self.repo, text=True, capture_output=True,
        )

    def assert_refuses_dirty_tree(self):
        before = self.git("status", "--porcelain")
        original_head = self.git("rev-parse", "HEAD")
        original_playstore = self.git("rev-parse", "playstore")
        result = self.sync()
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("main", self.git("branch", "--show-current"))
        self.assertEqual(original_head, self.git("rev-parse", "HEAD"))
        self.assertEqual(original_playstore, self.git("rev-parse", "playstore"))
        self.assertEqual(before, self.git("status", "--porcelain"))

    def test_preserves_unstaged_edits_before_switching_or_resetting(self):
        self.note.write_text("uncommitted work\n")
        self.assert_refuses_dirty_tree()
        self.assertEqual("uncommitted work\n", self.note.read_text())

    def test_preserves_staged_edits_before_switching_or_resetting(self):
        self.note.write_text("staged work\n")
        self.git("add", "notes.txt")
        self.assert_refuses_dirty_tree()
        self.assertEqual("staged work\n", self.note.read_text())

    def test_refuses_to_carry_untracked_work_onto_the_recreated_branch(self):
        draft = self.repo / "draft.txt"
        draft.write_text("untracked work\n")
        self.assert_refuses_dirty_tree()
        self.assertEqual("untracked work\n", draft.read_text())

    def test_clean_sync_changes_only_the_application_id_and_pushes_playstore(self):
        original_main = self.git("rev-parse", "main")
        result = self.sync()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("playstore", self.git("branch", "--show-current"))
        self.assertEqual(original_main, self.git("rev-parse", "main"))
        self.assertEqual(
            'applicationId = "com.nktnet.webview_kiosk"\n', self.gradle.read_text(),
        )
        self.assertEqual("app/build.gradle.kts", self.git("diff", "--name-only", "main"))
        self.assertEqual(self.git("rev-parse", "HEAD"), self.git("rev-parse", "origin/playstore"))
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_matching_push_configuration_does_not_push_other_branches(self):
        self.git("push", "origin", "main")
        remote_main = self.git("rev-parse", "origin/main")
        self.note.write_text("local main commit\n")
        self.git("add", "notes.txt")
        self.git("commit", "-m", "local main work")
        self.git("config", "push.default", "matching")

        result = self.sync()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(
            remote_main,
            self.git("--git-dir=" + str(self.remote), "rev-parse", "main"),
        )
        self.assertEqual(
            self.git("rev-parse", "HEAD"),
            self.git("--git-dir=" + str(self.remote), "rev-parse", "playstore"),
        )

    def test_sync_honours_the_configured_remote_name(self):
        self.git("remote", "rename", "origin", "publish")
        result = self.sync()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(
            self.git("rev-parse", "HEAD"), self.git("rev-parse", "publish/playstore"),
        )

    def test_sync_preserves_remote_changes_that_have_not_been_fetched(self):
        original = self.git("rev-parse", "origin/playstore")
        tree = self.git("rev-parse", "origin/playstore^{tree}")
        newer = self.git(
            "-c", "user.name=Other", "-c", "user.email=other@example.invalid",
            "--git-dir=" + str(self.remote),
            "commit-tree", tree, "-p", original, "-m", "other contributor's work",
        )
        self.git(
            "--git-dir=" + str(self.remote), "update-ref", "refs/heads/playstore", newer,
        )

        result = self.sync()

        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(
            newer,
            self.git("--git-dir=" + str(self.remote), "rev-parse", "playstore"),
        )


if __name__ == "__main__":
    unittest.main()
