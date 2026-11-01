"""Static checks for Android string resources before they reach translators.

Use Python's standard library so the checks run with the existing CI suite,
without an Android SDK or a network connection.
"""

import re
import unittest
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


RESOURCE_ROOT = Path(__file__).resolve().parents[2] / "app/src/main/res"
# These characters are easy to miss in a source-language string and can cause
# direction, line-breaking, or copy/paste errors. They are not universally
# invalid in translations (e.g. ZWNJ/ZWJ are essential for some languages).
UNEXPECTED_SOURCE_CHARACTERS = {
    "\u00ad",  # soft hyphen
    "\u200b",  # zero-width space
    "\u2060",  # word joiner
    "\ufeff",  # byte-order mark / zero-width no-break space
    "\ufffd",  # replacement character
    *(chr(value) for value in range(0x202A, 0x202F)),  # bidi overrides
    *(chr(value) for value in range(0x2066, 0x206A)),  # bidi isolates
}
PRINTF_TOKEN = re.compile(r"%(?:([1-9][0-9]*)\$([sSdDbBoOxXfFeEgGaAcChH])|%)")
ANDROID_ESCAPE = re.compile(r"\\(?:u[0-9a-fA-F]{4}|[\\ntr'\"@?])")
# These embedded examples contain syntax that should remain intact even when
# surrounding explanatory text is translated.
EXAMPLE_TOKENS = {
    "ui_custom_block_page_html_example": (
        "<div ", "</div>", "<h1 ", "</h1>", "<p ", "</p>",
        "<meta ", "10%%", "%1$s",
    ),
    "setting_info_mqtt_publish_event_topic": ("wk/event/${%1$s}",),
    "setting_info_mqtt_publish_response_topic": ("wk/response/${%1$s}",),
    "setting_info_mqtt_subscribe_command_topic": ("wk/${%1$s}/command",),
    "setting_info_mqtt_subscribe_request_topic": ("wk/${%1$s}/request",),
    "setting_info_mqtt_subscribe_settings_topic": ("wk/${%1$s}/settings",),
}
IMMUTABLE_EXAMPLES = {
    "ui_homeassistant",
    "ui_homeassistant_status",
    "ui_mqtt",
    "ui_mqtt_will_payload_example",
    "ui_mutual_tls_host_placeholder",
    "ui_website_blacklist_example",
    "ui_website_bookmarks_example",
    "ui_website_whitelist_example",
    "ui_e_g_document_body_style_backgroundcolor_green",
}


def extract_strings(root):
    for element in root:
        if element.tag == "string":
            yield element


def check_format_tokens(text):
    """Return positional format argument signatures or raise on invalid '%'."""
    arguments = {}
    position = 0
    while position < len(text):
        if text[position] != "%":
            position += 1
            continue
        token = PRINTF_TOKEN.match(text, position)
        if token is None:
            raise ValueError("unescaped or unindexed percent sign")
        if token.group(1) is not None:
            index = int(token.group(1))
            conversion = token.group(2)
            if index in arguments and arguments[index] != conversion:
                raise ValueError(f"argument {index} has inconsistent conversions")
            arguments[index] = conversion
        position = token.end()
    if arguments and set(arguments) != set(range(1, max(arguments) + 1)):
        raise ValueError("positional format arguments have gaps")
    return arguments


class AndroidStringResourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.resources = {}
        cls.source_files = list(RESOURCE_ROOT.glob("values*/*.xml"))
        assert cls.source_files, "No Android resource files found"
        for path in cls.source_files:
            content = path.read_bytes().decode("utf-8", errors="strict")
            cls.resources[path] = (content, ET.fromstring(content))

    def test_xml_is_valid_utf8_without_replacement_or_control_characters(self):
        for path, (content, _) in self.resources.items():
            with self.subTest(path=path.name):
                self.assertFalse(content.startswith("\ufeff"), "UTF-8 BOM")
                self.assertNotIn("\ufffd", content, "Unicode replacement character")
                for character in content:
                    codepoint = ord(character)
                    self.assertFalse(
                        codepoint < 32 and character not in "\n\r\t"
                        or 0x7F <= codepoint <= 0x9F,
                        f"Unexpected control character U+{codepoint:04X}",
                    )

    def test_source_language_strings_have_no_invisible_or_bidi_controls(self):
        for path, (_, root) in self.resources.items():
            if path.parent.name != "values":
                continue
            for element in root.iter():
                if element.tag not in ("string", "item"):
                    continue
                for character in element.text or "":
                    with self.subTest(resource=element.get("name"), file=path.name):
                        self.assertNotIn(
                            character,
                            UNEXPECTED_SOURCE_CHARACTERS,
                            f"Suspicious invisible character U+{ord(character):04X}",
                        )

    def test_android_escapes_and_format_arguments_are_valid(self):
        for path, (_, root) in self.resources.items():
            for element in extract_strings(root):
                name = element.get("name")
                value = "".join(element.itertext())
                with self.subTest(file=path.name, string=name):
                    position = 0
                    while (position := value.find("\\", position)) != -1:
                        match = ANDROID_ESCAPE.match(value, position)
                        self.assertIsNotNone(match, "Invalid Android string escape")
                        position = match.end() if match else position + 1
                    if element.get("formatted") != "false":
                        check_format_tokens(value)
                    else:
                        self.assertNotRegex(
                            value,
                            r"%[1-9][0-9]*\$[a-zA-Z]",
                            "Formatted argument declared as formatted=false",
                        )

    def test_printf_guard_catches_invalid_formatting(self):
        self.assertEqual(check_format_tokens("%1$s / %2$d (10%%)"), {1: "s", 2: "d"})
        for invalid in ("100%", "%s", "%1$q", "%2$s", "%1$d and %1$s"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                check_format_tokens(invalid)

    def test_future_translations_keep_format_argument_types_and_positions(self):
        source_strings = {}
        for path, (_, root) in self.resources.items():
            if path.parent.name == "values":
                source_strings.update(
                    (element.get("name"), element)
                    for element in extract_strings(root)
                )
        for path, (_, root) in self.resources.items():
            if not re.match(r"values-(?!v\d+$)", path.parent.name):
                continue
            for element in extract_strings(root):
                name = element.get("name")
                original = source_strings.get(name)
                if original is None or original.get("translatable") == "false":
                    continue
                if original.get("formatted") == "false":
                    continue
                expected = check_format_tokens("".join(original.itertext()))
                actual = check_format_tokens("".join(element.itertext()))
                with self.subTest(file=path.name, resource=name):
                    self.assertEqual(expected, actual)

    def test_translated_examples_preserve_their_code_syntax(self):
        for resource_name, tokens in EXAMPLE_TOKENS.items():
            for path, (_, root) in self.resources.items():
                for element in extract_strings(root):
                    if element.get("name") != resource_name:
                        continue
                    content = "".join(element.itertext())
                    for token in tokens:
                        with self.subTest(file=path.name, resource=resource_name, token=token):
                            self.assertIn(token, content)

    def test_fixed_values_are_not_sent_for_translation(self):
        for path, (_, root) in self.resources.items():
            for element in root:
                name = element.get("name", "")
                is_machine_array = (
                    element.tag == "string-array" and name.startswith("entry_values_")
                )
                is_technical_example = (
                    element.tag == "string" and name in IMMUTABLE_EXAMPLES
                )
                is_documentation_url = (
                    element.tag == "string"
                    and re.fullmatch(
                        r"https?://\S+", (element.text or "").strip().strip('"')
                    )
                    is not None
                )
                if is_machine_array or is_technical_example or is_documentation_url:
                    with self.subTest(resource=name, file=path.name):
                        self.assertEqual(element.get("translatable"), "false")

    def test_no_duplicate_resource_names_per_configuration(self):
        files_by_qualifier = {}
        for path, (_, root) in self.resources.items():
            files_by_qualifier.setdefault(path.parent.name, []).extend(
                (element.tag, element.get("name"))
                for element in root
                if element.tag in ("string", "string-array", "plurals")
            )
        for qualifier, resources in files_by_qualifier.items():
            duplicates = [name for name, count in Counter(resources).items() if count > 1]
            self.assertFalse(duplicates, f"Duplicate {qualifier} resources: {duplicates}")


if __name__ == "__main__":
    unittest.main()
