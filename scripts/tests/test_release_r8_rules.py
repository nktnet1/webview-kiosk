"""Guard against removing the R8 workaround for retrostreams startup crashes."""

import re
import unittest
from pathlib import Path


PROGUARD_RULES = Path(__file__).resolve().parents[2] / "app/proguard-rules.pro"


class ReleaseR8RulesTest(unittest.TestCase):
    def test_retrostreams_thread_local_random_is_kept(self):
        # java.util.Random.<init> calls the subclass's virtual setSeed() before
        # its initialized flag is set. R8 must not optimise that guard away.
        rules = PROGUARD_RULES.read_text(encoding="utf-8")
        self.assertRegex(
            rules,
            re.compile(
                r"^-keep class java9\.util\.concurrent\.ThreadLocalRandom \{ \*; \}$",
                re.M,
            ),
        )


if __name__ == "__main__":
    unittest.main()
