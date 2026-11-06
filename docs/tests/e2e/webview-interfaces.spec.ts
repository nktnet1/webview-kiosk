import { expect, test } from "@playwright/test";

test("WebView interface tool detects bridges and exercises native APIs", async ({
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

  await page.goto("tools/webview-interfaces/");
  await expect(
    page.getByRole("heading", { name: "WebView interfaces", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Detected", { exact: true })).toHaveCount(5);

  await page.getByRole("button", { name: "Read battery status" }).click();
  await expect(page.getByText("62%", { exact: true })).toBeVisible();
  await expect(page.getByText("27.3 °C", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Read", exact: true }).click();
  await expect(page.getByText("Current window brightness: 42%.")).toBeVisible();
  await page.getByLabel("Brightness (-1 to 100)").fill("101");
  await expect(
    page.getByRole("button", { name: "Set brightness" }),
  ).toBeDisabled();
  await page.getByLabel("Brightness (-1 to 100)").fill("25");
  await page.getByRole("button", { name: "Set brightness" }).click();
  await page.getByRole("button", { name: "Restore 42%" }).click();
  await page.getByRole("button", { name: "Read", exact: true }).click();
  await expect(page.getByText("Current window brightness: 42%.")).toBeVisible();

  await page.getByRole("button", { name: "Test vibration (150 ms)" }).click();
  await expect(
    page.getByText("150 ms vibration requested. Confirm the device vibrated."),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as Window & { __vibrateMs?: number }).__vibrateMs,
    ),
  ).toBe(150);

  await page.getByRole("button", { name: "Start tag scan" }).click();
  await expect(
    page.getByText("Scanning. Hold an NFC tag near your device."),
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
  await page.getByRole("button", { name: "Download test file" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("webview-kiosk-test.txt");

  await page.getByRole("button", { name: "Stop scan" }).click();
  await expect(page.getByText("Scan stopped.")).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as Window & { __stopCalls?: number }).__stopCalls,
    ),
  ).toBe(1);
});

test("unavailable bridges are reported instead of invoked", async ({
  page,
}) => {
  await page.goto("tools/webview-interfaces/");
  await expect(
    page.getByRole("button", { name: "Read battery status" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Start tag scan" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Download test file" }),
  ).toBeDisabled();
});
