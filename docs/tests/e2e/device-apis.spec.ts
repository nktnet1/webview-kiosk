import { expect, test } from "@playwright/test";

test("device API tool detects bridges and exercises native APIs", async ({
  page,
}) => {
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
    };
    let brightness = 42;
    api.WebviewKioskBatteryInterface = {
      getBatteryStatus: () =>
        JSON.stringify({
          level: 0.62,
          percentage: 62,
          charging: true,
          chargingType: "usb",
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

  await expect(page.getByRole("button", { name: "Read battery" })).toHaveCSS(
    "cursor",
    "pointer",
  );
  await page.getByRole("button", { name: "Read battery" }).click();
  await expect(page.getByText("62%", { exact: true })).toBeVisible();
  await expect(page.getByText("27.3 °C", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Read", exact: true }).click();
  await expect(
    page.locator("[data-sonner-toast]", { hasText: "Brightness: 42%" }),
  ).toBeVisible();
  await page.getByLabel("Brightness (-1 to 100)").fill("101");
  await expect(page.getByRole("button", { name: "Set" })).toBeDisabled();
  await page.getByLabel("Brightness (-1 to 100)").fill("25");
  await page.getByRole("button", { name: "Set" }).click();
  await page.getByRole("button", { name: "Restore 42%" }).click();
  await page.getByRole("button", { name: "Read", exact: true }).click();
  await expect(
    page.locator("[data-sonner-toast]", { hasText: "Brightness: 42%" }),
  ).toBeVisible();

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

test("unavailable bridges are reported instead of invoked", async ({
  page,
}) => {
  await page.goto("tools/device-apis/");
  const batteryButton = page.getByRole("button", { name: "Read battery" });
  await expect(batteryButton).toBeDisabled();
  await expect(batteryButton).toHaveCSS("cursor", "default");
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
  test(`brightness controls fit at ${width}px with restore visible`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.addInitScript(() => {
      let brightness = 42;
      (
        window as Window & {
          WebviewKioskBrightnessInterface?: {
            getBrightness(): number;
            setBrightness(value: number): void;
          };
        }
      ).WebviewKioskBrightnessInterface = {
        getBrightness: () => brightness,
        setBrightness: (value) => {
          brightness = value;
        },
      };
    });

    await page.goto("tools/device-apis/");
    const field = page.getByLabel("Brightness (-1 to 100)");
    await page.getByRole("button", { name: "Read", exact: true }).click();
    await field.fill("25");
    await page.getByRole("button", { name: "Set", exact: true }).click();
    const restore = page.getByRole("button", { name: "Restore 42%" });
    await expect(restore).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);

    const fieldRect = await field.boundingBox();
    const readRect = await page
      .getByRole("button", { name: "Read", exact: true })
      .boundingBox();
    const setRect = await page
      .getByRole("button", { name: "Set", exact: true })
      .boundingBox();
    const restoreRect = await restore.boundingBox();
    expect(fieldRect).not.toBeNull();
    expect(readRect).not.toBeNull();
    expect(setRect).not.toBeNull();
    expect(restoreRect).not.toBeNull();
    if (width < 640 && fieldRect && readRect && setRect && restoreRect) {
      expect(readRect.y).toBeGreaterThanOrEqual(fieldRect.y + fieldRect.height);
      expect(setRect.y).toBe(readRect.y);
      expect(restoreRect.y).toBeGreaterThanOrEqual(
        readRect.y + readRect.height,
      );
    }
  });
}

test("bridge failures are reported in Sonner", async ({ page }) => {
  await page.addInitScript(() => {
    (
      window as Window & {
        WebviewKioskBatteryInterface?: { getBatteryStatus(): string };
      }
    ).WebviewKioskBatteryInterface = { getBatteryStatus: () => "not JSON" };
  });
  await page.goto("tools/device-apis/");
  await page.getByRole("button", { name: "Read battery" }).click();
  await expect(page.locator("[data-sonner-toast]")).toContainText("JSON");
  await expect(page.locator('[data-slot="card"] [role="alert"]')).toHaveCount(
    0,
  );
});
