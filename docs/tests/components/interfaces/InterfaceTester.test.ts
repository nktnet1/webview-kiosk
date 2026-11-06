import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InterfaceTester from "#/components/interfaces/InterfaceTester";

describe("device API tester", () => {
  const markup = renderToStaticMarkup(createElement(InterfaceTester));

  it("shows the exact Android setting for every exposed feature", () => {
    const settings = [...markup.matchAll(/<code[^>]*>(.*?)<\/code>/gs)].map(
      ([, content]) => content.replace(/<[^>]*>/g, ""),
    );

    expect(settings).toEqual([
      "js_scripts.enable_battery_api",
      "js_scripts.enable_brightness_api",
      "device.allow_nfc",
      "js_scripts.enable_vibration_api",
      "web_engine.allow_file_download",
    ]);
  });

  it("stacks brightness input and buttons on narrow screens", () => {
    expect(markup).toContain(
      "grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center",
    );
    expect(markup).toContain("col-span-2 min-w-0 sm:w-28");
  });
});
