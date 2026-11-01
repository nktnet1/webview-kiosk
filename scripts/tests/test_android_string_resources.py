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

    def test_translated_option_arrays_preserve_item_counts_and_mqtt_codes(self):
        """Legacy entry arrays must retain their lengths and MQTT numeric codes."""
        originals = {
            element.get("name"): ["".join(item.itertext()) for item in element]
            for path, (_, root) in self.resources.items()
            if path.parent.name == "values"
            for element in root
            if element.tag == "string-array"
        }
        numeric_code_arrays = {
            "entries_mqtt_qos_option",
            "ui_labels_mqtt_retain_handling",
        }
        for path, (_, root) in self.resources.items():
            if path.parent.name != "values" and not re.match(
                r"values-(?!v\d+$)", path.parent.name
            ):
                continue
            for element in root:
                if element.tag != "string-array":
                    continue
                name = element.get("name")
                items = ["".join(item.itertext()) for item in element]
                if name not in originals:
                    continue
                with self.subTest(file=path.name, resource=name):
                    self.assertEqual(
                        len(originals[name]), len(items),
                        "Translated option array must preserve all enum positions",
                    )
                    if name in numeric_code_arrays:
                        for index, (source, value) in enumerate(zip(originals[name], items)):
                            expected_code = re.search(r"\([0-9]+\)$", source)
                            self.assertIsNotNone(expected_code, "Source MQTT option is missing its code")
                            self.assertTrue(
                                value.endswith(" " + expected_code.group()),
                                f"MQTT option #{index} must keep its numeric code",
                            )

    def test_enum_numeric_codes_stay_stable_when_localised(self):
        """MQTT codes and device rotation values must not acquire translated units."""
        source = {}
        for path, (_, root) in self.resources.items():
            if path.parent.name != "values":
                continue
            for element in root:
                if element.tag != "string":
                    continue
                name = element.get("name", "")
                if name.startswith("option_display_device_rotation_option_rotation_"):
                    with self.subTest(resource=name):
                        self.assertEqual(element.get("translatable"), "false")
                if name.startswith(
                    (
                        "option_display_mqtt_qos_option_",
                        "option_display_mqtt_retain_handling_option_",
                    )
                ):
                    value = "".join(element.itertext()).strip('"')
                    match = re.search(r" \([012]\)$", value)
                    with self.subTest(resource=name):
                        self.assertIsNotNone(match)
                    if match is not None:
                        source[name] = match.group()
        for path, (_, root) in self.resources.items():
            if path.parent.name == "values":
                continue
            for element in root:
                name = element.get("name", "")
                if element.tag == "string" and name in source:
                    with self.subTest(file=path.name, resource=name):
                        self.assertTrue(
                            "".join(element.itertext()).strip('"').endswith(source[name]),
                            "Translated MQTT labels must retain the literal protocol code",
                        )

    def test_managed_config_reuses_static_setting_descriptions(self):
        """Managed-config entries must describe the same setting as the app UI.

        Android restrictions resolve @string values directly; unlike Compose,
        they cannot provide runtime arguments to formatted strings.
        """
        strings = {
            element.get("name"): element
            for path, (_, root) in self.resources.items()
            if path.parent.name == "values"
            for element in root
            if element.tag == "string"
        }
        android = "{http://schemas.android.com/apk/res/android}"
        restriction_file = RESOURCE_ROOT / "xml/app_restrictions.xml"
        special = {
            "web_engine.enable_javascript": "enable_java_script",
            "web_engine.use_wide_viewport": "use_wide_view_port",
            "web_engine.allow_file_access_from_file_urls": "allow_file_access_from_file_url",
            "appearance.webview_inset": "web_view_inset",
            "device.rotation": "device_rotation",
            "mqtt.home_assistant.enabled": "mqtt_home_assistant_enable_discovery",
            "mqtt.connection.use_websocket": "mqtt_use_web_socket",
            "mqtt.connection.websocket_server_path": "mqtt_web_socket_server_path",
        }
        checked = set()
        for restriction in ET.parse(restriction_file).getroot():
            key = restriction.get(android + "key")
            if key in special:
                info_name = "setting_info_" + special[key]
            elif key.startswith("mqtt.connection."):
                info_name = "setting_info_mqtt_" + key[len("mqtt.connection."):]
            elif key.startswith("device.owner."):
                info_name = "setting_info_" + key[len("device.owner."):].replace(".", "_")
            elif key.startswith("unifiedpush."):
                info_name = "setting_info_unified_push_" + key[len("unifiedpush."):]
            elif key.startswith("mqtt."):
                info_name = "setting_info_" + key.replace(".", "_")
            else:
                info_name = "setting_info_" + key.split(".", 1)[1].replace(".", "_")
            description_ref = restriction.get(android + "description")
            with self.subTest(setting=key):
                self.assertIn(info_name, strings)
                self.assertTrue(description_ref.startswith("@string/"))
                description_name = description_ref.removeprefix("@string/")
                self.assertIn(description_name, strings)
                info = "".join(strings[info_name].itertext())
                has_runtime_arguments = bool(re.search(r"%[1-9][0-9]*\$", info))
                if has_runtime_arguments:
                    self.assertNotEqual(description_name, info_name)
                    self.assertTrue(description_name.endswith("_description"))
                else:
                    self.assertEqual(description_name, info_name)
                description = "".join(strings[description_name].itertext())
                self.assertFalse(re.search(r"%[1-9][0-9]*\$", description))
                self.assertFalse(description.strip('"').startswith("https://"))
                self.assertGreater(len(description.strip('"')), 15)
                checked.add(info_name)
        self.assertEqual(
            checked,
            {name for name in strings if name.startswith("setting_info_")},
            "Every setting help string should be represented by one restriction",
        )

    def test_setting_help_linebreaks_do_not_split_prose(self):
        """Help text uses breaks for paragraphs and lists, not fixed-width lines."""
        for path, (_, root) in self.resources.items():
            if path.parent.name != "values":
                continue
            for element in root:
                if element.tag != "string" or not element.get("name", "").startswith("setting_info_"):
                    continue
                text = "".join(element.itertext())
                with self.subTest(resource=element.get("name")):
                    self.assertNotIn(r"\n", text, "Use literal newlines in settings help XML")
                    self.assertNotIn("\n     to ", text)
                    self.assertNotIn("\n\n\n", text)
                    self.assertNotRegex(
                        text,
                        r"\n(?!https?://)(?=[a-z])",
                        "An explicit newline should not split an ordinary sentence",
                    )

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
