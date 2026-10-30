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
  await page
    .getByRole("button", { name: "Generate QR code", exact: true })
    .click();
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
  await page.goto("tools/qr-installation/");
  await expect(
    page.getByRole("heading", { name: "QR installation", exact: true }),
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

  await page.getByRole("combobox", { name: "Download source" }).click();
  await page.getByRole("option", { name: "F-Droid" }).click();
  await expect(
    page.getByRole("button", { name: "Generate QR code", exact: true }),
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

  await page.getByRole("combobox", { name: "Download source" }).click();
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
  await page.getByRole("button", { name: "Advanced options" }).click();
  await page
    .getByLabel("Network name (SSID)", { exact: true })
    .fill("Test E2E WiFi");
  await page
    .getByLabel("Wi-Fi password", { exact: true })
    .fill("local-only-passphrase");
  await page
    .getByRole("checkbox", { name: "Keep screen on", exact: true })
    .check();
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
  await page.getByRole("button", { name: "Advanced options" }).click();
  await page
    .getByLabel("Admin extras (JSON)", { exact: true })
    .fill("{invalid-json");
  const payload = await generatePayload(page);
  expect(payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"]).toEqual(
    {
      error: "Invalid JSON",
    },
  );
  await expect(page.getByText("Invalid admin extras")).toBeVisible();
});

test("changed settings replace an outdated QR with an update action", async ({
  page,
}) => {
  await generatePayload(page);
  await expect(page.getByRole("status")).toHaveText("Ready to scan");
  await page
    .getByLabel("Network name (SSID)", { exact: true })
    .fill("Updated network");
  await expect(page.getByTestId("provisioning-qr")).toHaveCount(0);
  await expect(page.getByRole("status")).toContainText("Settings changed");
  await page
    .getByRole("button", { name: "Update QR code", exact: true })
    .click();
  const qr = page.getByTestId("provisioning-qr");
  await expect(qr.locator("svg")).toBeVisible();
  const payload = JSON.parse(
    (await qr.getAttribute("data-provisioning-payload")) ?? "{}",
  );
  expect(payload["android.app.extra.PROVISIONING_WIFI_SSID"]).toBe(
    "Updated network",
  );
  await expect(page.getByRole("status")).toHaveText("Ready to scan");
});

test("Wi-Fi password visibility does not change the encoded password", async ({
  page,
}) => {
  const password = page.getByLabel("Wi-Fi password", { exact: true });
  await password.fill("local-only-passphrase");
  await expect(password).toHaveAttribute("type", "password");
  await page
    .getByRole("button", { name: "Show wi-fi password", exact: true })
    .click();
  await expect(password).toHaveAttribute("type", "text");
  await page
    .getByRole("button", { name: "Hide wi-fi password", exact: true })
    .click();
  await expect(password).toHaveAttribute("type", "password");
  const payload = await generatePayload(page);
  expect(payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"]).toBe(
    "local-only-passphrase",
  );
});

test("advanced settings are keyboard accessible and retain edits when collapsed", async ({
  page,
}) => {
  const advanced = page.getByRole("button", { name: "Advanced options" });
  await expect(advanced).toHaveAttribute("aria-expanded", "false");
  await expect(page.getByLabel("Locale", { exact: true })).toBeHidden();
  await advanced.press("Enter");
  await expect(advanced).toHaveAttribute("aria-expanded", "true");
  await page.getByLabel("Locale", { exact: true }).fill("");
  await page.getByLabel("Time zone", { exact: true }).fill("");
  await page
    .getByLabel("Admin extras (JSON)", { exact: true })
    .fill('{\n  "key": "value"\n}');
  await page
    .getByRole("checkbox", { name: "Keep screen on", exact: true })
    .check();
  await advanced.press("Enter");
  await expect(advanced).toHaveAttribute("aria-expanded", "false");
  const payload = await generatePayload(page);
  expect(payload["android.app.extra.PROVISIONING_LOCALE"]).toBeUndefined();
  expect(payload["android.app.extra.PROVISIONING_TIME_ZONE"]).toBeUndefined();
  expect(payload["android.app.extra.PROVISIONING_KEEP_SCREEN_ON"]).toBe(true);
  expect(payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"]).toEqual(
    { key: "value" },
  );
});

test("the legacy provisioning URL still renders the installer", async ({
  page,
}) => {
  await page.goto("provision-device/");
  await expect(
    page.getByRole("heading", { name: "QR installation", exact: true }),
  ).toBeVisible();
  await generatePayload(page);
});

for (const theme of ["light", "dark"]) {
  test(`the installer fits narrow screens in the ${theme} theme`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.evaluate((value) => {
      document.documentElement.classList.toggle("dark", value === "dark");
    }, theme);
    await page.getByRole("button", { name: "Advanced options" }).click();
    await page
      .getByLabel("Admin extras (JSON)", { exact: true })
      .fill('{"mobile":"test"}');
    await page
      .getByRole("button", { name: "Generate QR code", exact: true })
      .click();
    await expect(
      page.getByTestId("provisioning-qr").locator("svg"),
    ).toBeInViewport();
    await page.getByText("Show JSON", { exact: true }).click();
    await expect(
      page.getByText(
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
      ),
    ).toBeVisible();
    const dimensions = await page.evaluate(() => ({
      content: document.documentElement.scrollWidth,
      viewport: document.documentElement.clientWidth,
    }));
    expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport);
  });
}
