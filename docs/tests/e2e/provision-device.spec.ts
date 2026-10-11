import { expect, type Page, test } from "@playwright/test";

type ProvisioningPayload = Record<string, unknown> & {
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": string;
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": string;
};

async function generatePayload(page: Page): Promise<ProvisioningPayload> {
  const qr = page.getByTestId("provisioning-qr");
  const previous =
    (await qr.count()) > 0
      ? await qr.getAttribute("data-provisioning-payload")
      : null;
  await page.getByRole("button", { name: /^generate qr code for/i }).click();
  if (previous !== null) {
    await expect
      .poll(() => qr.getAttribute("data-provisioning-payload"))
      .not.toBe(previous);
  }
  await expect(qr.locator("svg")).toBeVisible();
  const value = await qr.getAttribute("data-provisioning-payload");
  expect(value).not.toBeNull();
  return JSON.parse(value ?? "{}");
}

test.beforeEach(async ({ page }) => {
  await page.goto("provision-device/");
  await expect(
    page.getByRole("heading", { name: "Generate QR Code" }),
  ).toBeVisible();
});

test("switching download sources changes the rendered QR payload and URL", async ({
  page,
}) => {
  const github = await generatePayload(page);
  expect(
    github[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    ],
  ).toMatch(/^https:\/\/github\.com\/nktnet1\/webview-kiosk\/releases\//);
  const githubChecksum =
    github["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"];
  expect(githubChecksum).toMatch(/^[A-Za-z0-9+/_-]+={0,2}$/);

  await page.getByRole("combobox").first().click();
  await page.getByRole("option", { name: "F-Droid" }).click();
  await expect(
    page.getByRole("button", { name: /generate qr code for v/i }),
  ).toBeEnabled();
  const fdroid = await generatePayload(page);
  expect(
    fdroid[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    ],
  ).toMatch(/^https:\/\/f-droid\.org\/repo\//);
  expect(
    fdroid[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    ],
  ).not.toEqual(
    github[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    ],
  );
  expect(
    fdroid["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"],
  ).toBe(githubChecksum);

  await page.getByRole("combobox").first().click();
  await page.getByRole("option", { name: "IzzyOnDroid" }).click();
  const izzy = await generatePayload(page);
  expect(
    izzy[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    ],
  ).toMatch(/^https:\/\/apt\.izzysoft\.de\/fdroid\/repo\//);
});

test("advanced edits affect the encoded provisioning data", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Show Advanced Options" }).click();
  await page.locator("#wifiSSID").fill("Test E2E WiFi");
  await page.locator("#wifiPassword").fill("local-only-passphrase");
  await page.getByText("Keep Screen On", { exact: true }).click();
  const payload = await generatePayload(page);
  expect(payload["android.app.extra.PROVISIONING_WIFI_SSID"]).toBe(
    "Test E2E WiFi",
  );
  expect(payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"]).toBe(
    "local-only-passphrase",
  );
  expect(payload["android.app.extra.PROVISIONING_KEEP_SCREEN_ON"]).toBe(true);

  await page.getByText("Show JSON", { exact: true }).click();
  await expect(
    page.getByText("android.app.extra.PROVISIONING_WIFI_SSID"),
  ).toBeVisible();
});

test("malformed admin extras do not prevent a safe QR payload", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Show Advanced Options" }).click();
  await page.locator("#adminExtras").fill("{invalid-json");
  const payload = await generatePayload(page);
  expect(payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"]).toEqual(
    {
      error: "Invalid JSON",
    },
  );
  await expect(page.getByText("Invalid admin extras")).toBeVisible();
});
