import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  Combobox,
  type ComboboxOption,
  filterComboboxOptions,
} from "../../../src/components/ui/combobox";

const options: ComboboxOption[] = [
  {
    value: "mqtt.connection.username",
    label: "Username",
    description: "MQTT connection",
  },
  { value: "appearance.theme", label: "Theme", description: "Appearance" },
  { value: "mqtt.enabled", label: "Enabled", description: "MQTT" },
];

describe("shared setting autocomplete", () => {
  it("matches labels, keys, groups, and multiple terms", () => {
    expect(filterComboboxOptions(options, "theme")).toEqual([options[1]]);
    expect(filterComboboxOptions(options, "connection.user")).toEqual([
      options[0],
    ]);
    expect(filterComboboxOptions(options, "mqtt enabled")).toEqual([
      options[2],
    ]);
    expect(filterComboboxOptions(options, " ")).toEqual(options);
    expect(filterComboboxOptions(options, "unknown")).toEqual([]);
  });

  it("renders a single autocomplete input with keyboard and screen reader semantics", () => {
    const html = renderToStaticMarkup(
      createElement(Combobox, {
        options,
        onSelect: () => {},
        id: "setting-picker",
        placeholder: "Choose a setting…",
      }),
    );
    expect(html).toContain('id="setting-picker"');
    expect(html).toContain('role="combobox"');
    expect(html).toContain('aria-autocomplete="list"');
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain("Search by key or name");
  });
});
