import { expect, test } from "@playwright/test";

test("UnifiedPush debugger saves local credentials and clears them", async ({
  page,
}) => {
  await page.goto("/unifiedpush");
  await expect(
    page.getByRole("heading", { name: "UnifiedPush Debugger" }),
  ).toBeVisible();
  await page
    .getByLabel("Push endpoint URL")
    .fill("https://push.example.org/secret");
  await page.getByLabel("Endpoint auth secret").fill("auth-secret");
  await expect
    .poll(() =>
      page.evaluate(() => {
        const raw = localStorage.getItem("webview-kiosk:unifiedpush-debug:v1");
        return raw ? JSON.parse(raw).auth : null;
      }),
    )
    .toBe("auth-secret");
  await page.reload();
  await expect(page.getByLabel("Push endpoint URL")).toHaveValue(
    "https://push.example.org/secret",
  );
  await expect(page.getByLabel("Endpoint auth secret")).toHaveValue(
    "auth-secret",
  );
  await page.getByRole("button", { name: "Clear credentials" }).click();
  await expect(page.getByLabel("Push endpoint URL")).toBeEmpty();
  await expect(page.getByLabel("Endpoint auth secret")).toBeEmpty();
  await expect
    .poll(() =>
      page.evaluate(() =>
        localStorage.getItem("webview-kiosk:unifiedpush-debug:v1"),
      ),
    )
    .toBeNull();
});

test("UnifiedPush debugger uses form controls and preserves raw JSON edits", async ({
  page,
}) => {
  await page.goto("/unifiedpush");
  await expect(page.getByLabel("Command", { exact: true })).toHaveValue(
    "toast",
  );
  await page.getByLabel("Message", { exact: true }).fill("A device toast");
  await page.getByRole("tab", { name: "JSON" }).click();
  await expect(page.getByLabel("JSON payload")).toHaveValue(/A device toast/);
  await page
    .getByLabel("JSON payload")
    .fill(
      '{ "type": "command", "command": "go_to_url", "data": { "url": "https://example.net", "custom": true }, "messageId": "abc-123" }',
    );
  await page.getByRole("tab", { name: "Form" }).click();
  await expect(page.getByLabel("Command", { exact: true })).toHaveValue(
    "go_to_url",
  );
  await page.getByLabel("URL", { exact: true }).fill("https://example.org");
  await page.getByRole("tab", { name: "JSON" }).click();
  const payload = JSON.parse(
    await page.getByLabel("JSON payload").inputValue(),
  );
  expect(payload.data).toEqual({ url: "https://example.org", custom: true });
  expect(payload.messageId).toBe("abc-123");
  await page.getByLabel("JSON payload").fill("invalid");
  await page.getByRole("tab", { name: "Form" }).click();
  await expect(page.getByRole("alert")).toContainText("valid JSON");
  await expect(page.getByLabel("JSON payload")).toHaveValue("invalid");
  await page.getByRole("button", { name: "Prepare cURL fallback" }).click();
  await expect(page.getByRole("status")).toContainText("valid JSON");
});

test("UnifiedPush debugger builds settings updates with typed controls", async ({
  page,
}) => {
  await page.goto("/unifiedpush");
  await page.getByLabel("Message type").selectOption("settings");
  await page.getByLabel("appearance.theme value").selectOption("DARK");
  await page
    .getByLabel("Setting to add")
    .selectOption("web_browsing.allow_refresh");
  await page.getByRole("button", { name: "Add setting" }).click();
  await page.getByLabel("Enabled").click();
  await page
    .getByLabel("Setting to add")
    .selectOption("web_browsing.address_bar_actions");
  await page.getByRole("button", { name: "Add setting" }).click();
  await page.getByLabel("Back", { exact: true }).click();
  await page.getByRole("tab", { name: "JSON" }).click();
  const payload = JSON.parse(
    await page.getByLabel("JSON payload").inputValue(),
  );
  expect(payload).toEqual({
    type: "settings",
    data: {
      settings: {
        "appearance.theme": "DARK",
        "web_browsing.allow_refresh": true,
        "web_browsing.address_bar_actions": ["BACK"],
      },
    },
  });
  await page
    .getByLabel("JSON payload")
    .fill(
      '{"type":"settings","data":{"settings":{"appearance.theme":"LIGHT","future.key":{"nested":true}}},"messageId":"id-2"}',
    );
  await page.getByRole("tab", { name: "Form" }).click();
  await expect(page.getByText("future.key")).toBeVisible();
  await page.getByLabel("appearance.theme value").selectOption("SYSTEM");
  await page.getByRole("tab", { name: "JSON" }).click();
  const preserved = JSON.parse(
    await page.getByLabel("JSON payload").inputValue(),
  );
  expect(preserved.data.settings["future.key"]).toEqual({ nested: true });
  expect(preserved.messageId).toBe("id-2");
});

test("UnifiedPush debugger prepares an encrypted request entirely in the browser", async ({
  page,
}) => {
  await page.goto("/unifiedpush");
  const { p256dh, auth } = await page.evaluate(async () => {
    const pair = await crypto.subtle.generateKey(
      { name: "ECDH", namedCurve: "P-256" },
      true,
      ["deriveBits"],
    );
    const publicKey = new Uint8Array(
      await crypto.subtle.exportKey("raw", pair.publicKey),
    );
    const authSecret = crypto.getRandomValues(new Uint8Array(16));
    const encode = (bytes: Uint8Array) =>
      btoa(String.fromCharCode(...bytes))
        .replaceAll("+", "-")
        .replaceAll("/", "_")
        .replaceAll("=", "");
    return { p256dh: encode(publicKey), auth: encode(authSecret) };
  });
  await page
    .getByLabel("Push endpoint URL")
    .fill("https://push.example.org/token");
  await page.getByLabel("Endpoint public key (p256dh)").fill(p256dh);
  await page.getByLabel("Endpoint auth secret").fill(auth);
  await page.getByRole("button", { name: "Generate VAPID keys" }).click();
  await expect(page.getByLabel("VAPID public key")).toHaveValue(
    /^[a-zA-Z0-9_-]{87}$/,
  );
  await page.getByRole("button", { name: "Prepare cURL fallback" }).click();
  await expect(page.getByRole("status")).toContainText(
    "Encrypted request prepared locally",
  );
  await expect(
    page.getByRole("button", { name: "Copy cURL command" }),
  ).toBeVisible();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download .bin" }).click();
  expect((await downloadPromise).suggestedFilename()).toBe(
    "unifiedpush-message.bin",
  );
});
