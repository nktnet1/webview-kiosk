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

    def test_mqtt_websocket_codec_keeps_only_http_and_base_packages(self):
        rules = PROGUARD_RULES.read_text(encoding="utf-8")
        self.assertNotRegex(
            rules,
            re.compile(r"^-keep class io\.netty\.handler\.codec\.\*\*", re.M),
        )
        for keep_rule in (
            "-keep class io.netty.handler.codec.* { *; }",
            "-keep class io.netty.handler.codec.http.* { *; }",
            "-keep class io.netty.handler.codec.http.websocketx.** { *; }",
        ):
            with self.subTest(rule=keep_rule):
                self.assertIn(keep_rule, rules)

    def test_jctools_keeps_queue_field_names_without_all_members(self):
        rules = PROGUARD_RULES.read_text(encoding="utf-8")
        self.assertIn("-keepclassmembernames class io.netty.** { *; }", rules)
        self.assertIn(
            "-keepclassmembers class org.jctools.queues.** { <fields>; }", rules,
        )
        self.assertNotIn("-keepclassmembers class org.jctools.** { *; }", rules)


if __name__ == "__main__":
    unittest.main()
