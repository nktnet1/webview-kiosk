import { expect, test } from "@playwright/test";

test("device API readings poll and native actions work", async ({ page }) => {
  await page.addInitScript(() => {
    class MockNdefReader extends EventTarget {
      onreadingerror: ((error: unknown) => void) | null = null;

      async scan(options?: { signal?: AbortSignal }) {
        api.__testReader = this;
        api.__scanSignal = options?.signal;
      }

      stopScan() {
        api.__stopCalls = (api.__stopCalls ?? 0) + 1;
      }
    }

    const api = window as Window & {
      WebviewKioskBatteryInterface?: { getBatteryStatus(): string };
      WebviewKioskBrightnessInterface?: {
        getBrightness(): number;
        setBrightness(value: number): void;
      };
      WebviewKioskNfcInterface?: {
        openClient(): string;
        scanForClient(): boolean;
      };
      WebviewKioskBlobInterface?: {
        startDownload(): boolean;
        appendChunk(): boolean;
        finishDownload(): boolean;
      };
      __webviewKioskWebNfcInstalled?: boolean;
      NDEFReader?: typeof MockNdefReader;
      __testReader?: MockNdefReader;
      __scanSignal?: AbortSignal;
      __stopCalls?: number;
      __vibrateMs?: number;
      __batteryLevel?: number;
      __batteryCharging?: boolean;
      __setBrightnessExternally?: (value: number) => void;
    };
    let brightness = 42;
    api.__batteryLevel = 62;
    api.__batteryCharging = true;
    api.__setBrightnessExternally = (value) => {
      brightness = value;
    };
    api.WebviewKioskBatteryInterface = {
      getBatteryStatus: () =>
        JSON.stringify({
          level: (api.__batteryLevel ?? 0) / 100,
          percentage: api.__batteryLevel,
          charging: api.__batteryCharging,
          chargingType: api.__batteryCharging ? "usb" : "none",
          voltage: 3.8,
          temperature: 27.3,
          health: "good",
        }),
    };
    api.WebviewKioskBrightnessInterface = {
      getBrightness: () => brightness,
      setBrightness: (value) => {
        brightness = value;
      },
    };
    api.WebviewKioskNfcInterface = {
      openClient: () => "test-client",
      scanForClient: () => true,
    };
    api.__webviewKioskWebNfcInstalled = true;
    api.NDEFReader = MockNdefReader;
    api.WebviewKioskBlobInterface = {
      startDownload: () => true,
      appendChunk: () => true,
      finishDownload: () => true,
    };
    Object.defineProperty(navigator, "vibrate", {
      configurable: true,
      value: (duration: number) => {
        api.__vibrateMs = duration;
        return true;
      },
    });
  });

  await page.goto("tools/device-apis/");
  await expect(
    page.getByRole("heading", { name: "Device APIs", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Detected", { exact: true })).toHaveCount(5);
  for (const setting of [
    "js_scripts.enable_battery_api",
    "js_scripts.enable_brightness_api",
    "device.allow_nfc",
    "js_scripts.enable_vibration_api",
    "web_engine.allow_file_download",
  ]) {
    await expect(page.locator("code", { hasText: setting })).toBeVisible();
  }

  await expect(page.getByText("62%", { exact: true })).toBeVisible();
  await expect(page.getByText("27.3 °C", { exact: true })).toBeVisible();
  await expect(page.getByText("Current: 42%")).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Restore" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Read battery" })).toHaveCount(
    0,
  );

  const field = page.getByLabel("Brightness (-1 to 100)");
  await expect(field).toHaveValue("42");
  await field.fill("101");
  await expect(page.getByRole("button", { name: "Set" })).toBeDisabled();
  await field.fill("25");
  await expect(page.getByRole("button", { name: "Set" })).toHaveCSS(
    "cursor",
    "pointer",
  );
  await page.getByRole("button", { name: "Set" }).click();
  await expect(page.getByText("Current: 25%")).toBeVisible({ timeout: 5_000 });
  await expect(field).toHaveValue("25");

  // Values change in Android without an explicit button click.
  await page.evaluate(() => {
    const api = window as Window & {
      __batteryLevel?: number;
      __batteryCharging?: boolean;
      __setBrightnessExternally?: (value: number) => void;
    };
    api.__batteryLevel = 75;
    api.__batteryCharging = false;
    api.__setBrightnessExternally?.(83);
  });
  await expect(page.getByText("75%", { exact: true })).toBeVisible({
    timeout: 5_000,
  });
  await expect(page.getByText("Current: 83%")).toBeVisible();
  await expect(page.getByText("No", { exact: true })).toBeVisible();
  await expect(page.getByText("none", { exact: true })).toBeVisible();

  // The editor must not be overwritten while a user is entering a new target.
  await field.fill("33");
  await page.evaluate(() => {
    (
      window as Window & {
        __setBrightnessExternally?: (value: number) => void;
      }
    ).__setBrightnessExternally?.(55);
  });
  await expect(page.getByText("Current: 55%")).toBeVisible({ timeout: 5_000 });
  await expect(field).toHaveValue("33");
  await page.getByRole("button", { name: "Set" }).click();
  await expect(page.getByText("Current: 33%")).toBeVisible({ timeout: 5_000 });

  await page.getByRole("button", { name: "Vibrate" }).click();
  await expect(
    page.locator("[data-sonner-toast]", {
      hasText: "Vibration requested (150 ms).",
    }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as Window & { __vibrateMs?: number }).__vibrateMs,
    ),
  ).toBe(150);

  await page.getByRole("button", { name: "Start scan" }).click();
  await expect(
    page.locator("[data-sonner-toast]", {
      hasText: "Scanning. Hold a tag near your device.",
    }),
  ).toBeVisible();
  await page.evaluate(() => {
    const api = window as Window & { __testReader?: EventTarget };
    api.__testReader?.dispatchEvent(
      Object.assign(new Event("reading"), {
        serialNumber: "ABCD",
        message: {
          records: [
            {
              recordType: "text",
              text: async () => "NFC works",
            },
          ],
        },
      }),
    );
  });
  await expect(page.getByText("Serial number: ABCD")).toBeVisible();
  await expect(page.getByText("NFC works")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download sample" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("webview-kiosk-test.txt");

  await page.getByRole("button", { name: "Stop scan" }).click();
  await expect(
    page.locator("[data-sonner-toast]", { hasText: "Scan stopped." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as Window & { __stopCalls?: number }).__stopCalls,
    ),
  ).toBe(1);
});

test("unavailable bridges are detected automatically", async ({ page }) => {
  await page.goto("tools/device-apis/");
  await expect(page.getByText("Not detected", { exact: true })).toHaveCount(5);
  await expect(page.getByRole("button", { name: "Set" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Start scan" })).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Download sample" }),
  ).toBeDisabled();
});

test("legacy interface tool links redirect to Device APIs", async ({
  page,
}) => {
  await page.goto("tools/webview-interfaces/");
  await expect(page).toHaveURL(/\/tools\/device-apis\/?$/);
  await expect(
    page.getByRole("heading", { name: "Device APIs" }),
  ).toBeVisible();
});

for (const width of [320, 375, 768]) {
  test(`brightness input and Set fit at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.addInitScript(() => {
      (
        window as Window & {
          WebviewKioskBrightnessInterface?: {
            getBrightness(): number;
            setBrightness(value: number): void;
          };
        }
      ).WebviewKioskBrightnessInterface = {
        getBrightness: () => 42,
        setBrightness: () => {},
      };
    });

    await page.goto("tools/device-apis/");
    const field = page.getByLabel("Brightness (-1 to 100)");
    await expect(field).toHaveValue("42");
    await expect(page.getByRole("button", { name: "Set" })).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);

    const fieldRect = await field.boundingBox();
    const setRect = await page
      .getByRole("button", { name: "Set" })
      .boundingBox();
    expect(fieldRect).not.toBeNull();
    expect(setRect).not.toBeNull();
    if (fieldRect && setRect) {
      expect(setRect.y).toBeGreaterThanOrEqual(fieldRect.y);
      expect(setRect.y).toBeLessThan(fieldRect.y + fieldRect.height);
      expect(setRect.x).toBeGreaterThanOrEqual(fieldRect.x + fieldRect.width);
    }
  });
}

for (const width of [320, 375, 768, 1280]) {
  test(`primary actions are right-aligned at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.goto("tools/device-apis/");

    for (const [title, action] of [
      ["Brightness", "Set"],
      ["NFC", "Start scan"],
      ["Vibration", "Vibrate"],
      ["Blob downloads", "Download sample"],
    ] as const) {
      const panel = page.locator('[data-slot="card"]').filter({
        has: page.locator('[data-slot="card-title"]', {
          hasText: new RegExp(`^${title}$`),
        }),
      });
      const content = panel.locator('[data-slot="card-content"]');
      const actionRight = await content
        .getByRole("button", { name: action })
        .evaluate((button) => button.getBoundingClientRect().right);
      const contentRight = await content.evaluate(
        (element) =>
          element.getBoundingClientRect().right
          - Number.parseFloat(getComputedStyle(element).paddingRight),
      );
      expect(actionRight).toBeCloseTo(contentRight, 0);
    }

    const nfcPanel = page.locator('[data-slot="card"]').filter({
      has: page.locator('[data-slot="card-title"]', { hasText: /^NFC$/ }),
    });
    const clear = await nfcPanel
      .getByRole("button", { name: "Clear tags" })
      .boundingBox();
    const primary = await nfcPanel
      .getByRole("button", { name: "Start scan" })
      .boundingBox();
    expect(clear).not.toBeNull();
    expect(primary).not.toBeNull();
    if (clear && primary) {
      expect(clear.y).toBeLessThanOrEqual(primary.y);
      if (clear.y === primary.y) {
        expect(clear.x + clear.width).toBeLessThan(primary.x);
      }
    }
  });
}

test("a failing battery read does not stop the brightness poll", async ({
  page,
}) => {
  await page.addInitScript(() => {
    const api = window as Window & {
      WebviewKioskBatteryInterface?: { getBatteryStatus(): string };
      WebviewKioskBrightnessInterface?: {
        getBrightness(): number;
        setBrightness(value: number): void;
      };
    };
    api.WebviewKioskBatteryInterface = { getBatteryStatus: () => "not JSON" };
    api.WebviewKioskBrightnessInterface = {
      getBrightness: () => 35,
      setBrightness: () => {},
    };
  });
  await page.goto("tools/device-apis/");
  await expect(page.getByText("No reading")).toHaveCount(1);
  await expect(page.getByText("Current: 35%")).toBeVisible();
  await expect(page.locator("[data-sonner-toast]")).toHaveCount(0);
});

test("failed NFC start produces one error toast", async ({ page }) => {
  await page.addInitScript(() => {
    class FailingReader extends EventTarget {
      onreadingerror: ((error: unknown) => void) | null = null;
      async scan() {
        this.onreadingerror?.(new Error("Native reader error"));
        throw new Error("NFC scan rejected");
      }
      stopScan() {}
    }
    const api = window as Window & {
      WebviewKioskNfcInterface?: {
        openClient(): string;
        scanForClient(): boolean;
      };
      __webviewKioskWebNfcInstalled?: boolean;
      NDEFReader?: typeof FailingReader;
    };
    api.WebviewKioskNfcInterface = {
      openClient: () => "test",
      scanForClient: () => true,
    };
    api.__webviewKioskWebNfcInstalled = true;
    api.NDEFReader = FailingReader;
  });
  await page.goto("tools/device-apis/");
  await page.getByRole("button", { name: "Start scan" }).click();
  await expect(
    page.locator("[data-sonner-toast]", { hasText: "NFC scan rejected" }),
  ).toBeVisible();
  await expect(page.locator("[data-sonner-toast]")).toHaveCount(1);
  await expect(page.getByRole("button", { name: "Start scan" })).toBeEnabled();
});
