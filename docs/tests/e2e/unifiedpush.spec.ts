import { readFile } from "node:fs/promises";
import { expect, type Page, test } from "@playwright/test";

async function uploadCredentials(page: Page, content: string) {
  await page.getByRole("button", { name: "Import JSON" }).click();
  const dialog = page.getByRole("dialog", { name: "Import credentials" });
  const choosing = page.waitForEvent("filechooser");
  await dialog.getByRole("button", { name: "Choose file" }).click();
  const chooser = await choosing;
  await chooser.setFiles({
    name: "unifiedpush-credentials.json",
    mimeType: "application/json",
    buffer: Buffer.from(content),
  });
  await expect(dialog.getByLabel("Credentials JSON")).toHaveValue(content);
  await dialog.getByRole("button", { name: "Import credentials" }).click();
}

test("UnifiedPush documentation links to the debugger from a top-level tip", async ({
  page,
}) => {
  await page.goto("/docs/settings/unifiedpush");
  await expect(page.getByText("Test UnifiedPush")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "UnifiedPush Debugger" }),
  ).toHaveAttribute("href", "/debugger/unifiedpush");
});

test("Credential actions keep equal widths", async ({ page }) => {
  await page.goto("/debugger/unifiedpush");
  const labels = [
    "Reveal credentials",
    "Generate VAPID keys",
    "Clear credentials",
  ];
  const widths = await Promise.all(
    labels.map(async (label) => {
      const button = page.getByRole("button", { name: label });
      await expect(button).toBeVisible();
      const bounds = await button.boundingBox();
      expect(bounds).not.toBeNull();
      return bounds?.width;
    }),
  );
  expect(widths[0]).toBe(widths[1]);
  expect(widths[1]).toBe(widths[2]);
});

test("Credential fields have individual visibility toggles and a show-all action", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  const secrets = [
    { label: "Push endpoint URL", reveal: "endpoint" },
    { label: "Endpoint public key (p256dh)", reveal: "p256dh key" },
    { label: "Endpoint auth secret", reveal: "auth secret" },
    { label: "VAPID public key", reveal: "VAPID verification key" },
    { label: "VAPID private key", reveal: "VAPID signing key" },
  ];

  for (const secret of secrets) {
    await expect(
      page.getByLabel(secret.label, { exact: true }),
    ).toHaveAttribute("type", "password");
    await expect(
      page.getByRole("button", { name: `Show ${secret.reveal}` }),
    ).toBeVisible();
  }
  await expect(page.getByLabel("VAPID contact (optional)")).toHaveAttribute(
    "type",
    "text",
  );

  await page.getByRole("button", { name: "Show p256dh key" }).click();
  await expect(page.getByLabel("Endpoint public key (p256dh)")).toHaveAttribute(
    "type",
    "text",
  );
  await expect(page.getByLabel("VAPID public key")).toHaveAttribute(
    "type",
    "password",
  );
  await page.getByRole("button", { name: "Reveal credentials" }).click();
  for (const secret of secrets) {
    await expect(
      page.getByLabel(secret.label, { exact: true }),
    ).toHaveAttribute("type", "text");
  }
  await page
    .getByRole("button", { name: "Hide VAPID verification key" })
    .click();
  await expect(page.getByLabel("VAPID public key")).toHaveAttribute(
    "type",
    "password",
  );
  await page.getByRole("button", { name: "Hide credentials" }).click();
  for (const secret of secrets) {
    await expect(
      page.getByLabel(secret.label, { exact: true }),
    ).toHaveAttribute("type", "password");
  }
  // The global toggle resets individual visibility overrides.
  await page.getByRole("button", { name: "Reveal credentials" }).click();
  await expect(page.getByLabel("VAPID public key")).toHaveAttribute(
    "type",
    "text",
  );
});

test("Replacing VAPID keys requires a custom confirmation", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  await page.getByRole("button", { name: "Generate VAPID keys" }).click();
  const vapidPublic = page.getByLabel("VAPID public key");
  await expect(vapidPublic).toHaveValue(/^[a-zA-Z0-9_-]{87}$/);
  const oldKey = await vapidPublic.inputValue();

  await page.getByRole("button", { name: "Generate VAPID keys" }).click();
  const confirmation = page.getByRole("alertdialog", {
    name: "Replace existing VAPID keys?",
  });
  await expect(confirmation).toContainText("re-registered");
  await confirmation.getByRole("button", { name: "Cancel" }).click();
  await expect(vapidPublic).toHaveValue(oldKey);

  await page.getByRole("button", { name: "Generate VAPID keys" }).click();
  await confirmation.getByRole("button", { name: "Replace keys" }).click();
  await expect.poll(() => vapidPublic.inputValue()).not.toBe(oldKey);
  await expect
    .poll(() =>
      page.evaluate(() => {
        const data = localStorage.getItem("webview-kiosk:unifiedpush-debug:v1");
        return data ? JSON.parse(data).vapidPublicKey : "";
      }),
    )
    .toBe(await vapidPublic.inputValue());
});

test("Delivery mode uses an accessible select and TTL is validated", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  const deliverySelect = page.getByRole("combobox", { name: "Delivery mode" });
  await deliverySelect.click();
  const popup = page.locator('[data-slot="select-content"]');
  await expect(popup).toHaveAttribute("data-side", "bottom");
  const triggerBox = await deliverySelect.boundingBox();
  const popupBox = await popup.boundingBox();
  if (!triggerBox || !popupBox) {
    throw new Error("Expected a visible trigger and dropdown");
  }
  expect(popupBox.y).toBeGreaterThanOrEqual(triggerBox.y + triggerBox.height);
  await page
    .getByRole("option", { name: "Plaintext (ntfy-compatible only)" })
    .click();
  await expect(page.getByLabel("Endpoint auth secret")).not.toBeVisible();
  await expect(page.getByLabel("Time to live (seconds)")).not.toBeVisible();

  await page.getByRole("combobox", { name: "Delivery mode" }).click();
  await page
    .getByRole("option", { name: "Encrypted Web Push (recommended)" })
    .click();
  const ttl = page.getByLabel("Time to live (seconds)");
  await ttl.fill("2419201");
  await ttl.blur();
  await expect(page.getByText(/TTL must be a whole number/)).toBeVisible();
  await ttl.fill("60");
  await ttl.blur();
  await expect(page.getByText(/TTL must be a whole number/)).toHaveCount(0);
});

test("UnifiedPush debugger saves local credentials and clears them", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
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
  const dialog = page.getByRole("alertdialog", {
    name: "Clear saved credentials?",
  });
  await expect(dialog).toContainText("cannot be undone");
  await dialog.getByRole("button", { name: "Cancel" }).click();
  await expect(page.getByLabel("Push endpoint URL")).toHaveValue(
    "https://push.example.org/secret",
  );
  await page.getByRole("button", { name: "Clear credentials" }).click();
  await dialog.getByRole("button", { name: "Clear credentials" }).click();
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

test("UnifiedPush credential JSON dialogs support clipboard, files, paste and replacement", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  const endpoint = page.getByLabel("Push endpoint URL");
  const auth = page.getByLabel("Endpoint auth secret");
  const vapidKey = page.getByLabel("VAPID private key");
  await endpoint.fill("https://push.example.org/initial");
  await auth.fill("private-auth");
  await vapidKey.fill("private-vapid-key");

  await page.getByRole("button", { name: "Export JSON" }).click();
  const exportDialog = page.getByRole("dialog", { name: "Export credentials" });
  await expect(exportDialog).toBeVisible();
  const exportText = exportDialog.getByLabel("Credentials JSON");
  await expect(exportText).toHaveAttribute("readonly", "");
  const exported = await exportText.inputValue();
  expect(JSON.parse(exported)).toEqual({
    endpoint: "https://push.example.org/initial",
    p256dh: "",
    auth: "private-auth",
    vapidPublicKey: "",
    vapidPrivateKey: "private-vapid-key",
    vapidSubject: "",
  });
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await exportDialog.getByRole("button", { name: "Copy JSON" }).click();
  await expect
    .poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toBe(exported);

  const downloading = page.waitForEvent("download");
  await exportDialog.getByRole("button", { name: "Download file" }).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe("unifiedpush-credentials.json");
  expect(await readFile(await download.path(), "utf8")).toBe(exported);
  await exportDialog.getByRole("button", { name: "Close dialog" }).click();
  await expect(page.getByRole("button", { name: "Export JSON" })).toBeFocused();

  await page.getByRole("button", { name: "Import JSON" }).click();
  let importDialog = page.getByRole("dialog", { name: "Import credentials" });
  await importDialog.getByLabel("Credentials JSON").fill("not JSON");
  await importDialog
    .getByRole("button", { name: "Import credentials" })
    .click();
  await expect(importDialog.getByRole("alert")).toContainText("valid JSON");
  await expect(endpoint).toHaveValue("https://push.example.org/initial");

  // Pasted JSON is reviewed before any existing credentials are replaced.
  await importDialog
    .getByLabel("Credentials JSON")
    .fill('{"endpoint":"https://push.example.org/new"}');
  await importDialog
    .getByRole("button", { name: "Import credentials" })
    .click();
  const confirmation = page.getByRole("dialog", {
    name: "Replace existing credentials?",
  });
  await expect(confirmation).toContainText(
    "Missing JSON fields will be cleared",
  );
  await confirmation.getByRole("button", { name: "Cancel" }).click();
  await expect(endpoint).toHaveValue("https://push.example.org/initial");
  await expect(auth).toHaveValue("private-auth");
  importDialog = page.getByRole("dialog", { name: "Import credentials" });
  await importDialog
    .getByRole("button", { name: "Import credentials" })
    .click();
  await confirmation
    .getByRole("button", { name: "Replace credentials" })
    .click();
  await expect(endpoint).toHaveValue("https://push.example.org/new");
  await expect(auth).toBeEmpty();
  await expect(vapidKey).toBeEmpty();
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          JSON.parse(
            localStorage.getItem("webview-kiosk:unifiedpush-debug:v1") ?? "{}",
          ).endpoint,
      ),
    )
    .toBe("https://push.example.org/new");

  // A downloaded JSON backup can also be loaded from a file.
  await uploadCredentials(page, exported);
  await confirmation
    .getByRole("button", { name: "Replace credentials" })
    .click();
  await expect(auth).toHaveValue("private-auth");
  await expect(vapidKey).toHaveValue("private-vapid-key");
  await page.reload();
  await expect(auth).toHaveValue("private-auth");
});

test("Credential help uses focusable info tooltips", async ({ page }) => {
  await page.goto("/debugger/unifiedpush");
  await expect(
    page.getByText(
      "The capability URL supplied by the UnifiedPush distributor.",
    ),
  ).toHaveCount(0);
  const info = page
    .locator('label[for="push-endpoint"]')
    .locator("..")
    .getByRole("button", { name: "More information" });
  await info.focus();
  await expect(page.getByRole("tooltip")).toContainText(
    "The capability URL supplied by the UnifiedPush distributor.",
  );
});

test("UnifiedPush debugger uses form controls and preserves raw JSON edits", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  await expect(page.getByRole("combobox", { name: "Command" })).toContainText(
    "Toast",
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
  await expect(page.getByRole("combobox", { name: "Command" })).toContainText(
    "Go to URL",
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
  await page.getByRole("button", { name: "Send test push" }).click();
  await expect(page.getByRole("status")).toContainText("valid JSON");
});

test("UnifiedPush debugger builds settings updates with typed controls", async ({
  page,
}) => {
  await page.goto("/debugger/unifiedpush");
  await page.getByRole("combobox", { name: "Message type" }).click();
  await page.getByRole("option", { name: "Settings update" }).click();
  await page.getByRole("combobox", { name: "appearance.theme value" }).click();
  await page.getByRole("option", { name: "Dark", exact: true }).click();
  await page.getByRole("combobox", { name: "Setting to add" }).click();
  await page
    .getByRole("option", { name: /web_browsing\.allow_refresh/ })
    .click();
  await page.getByRole("button", { name: "Add setting" }).click();
  await page.getByLabel("Enabled").click();
  await page.getByRole("combobox", { name: "Setting to add" }).click();
  await page
    .getByRole("option", { name: /web_browsing\.address_bar_actions/ })
    .click();
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
  await page.getByRole("combobox", { name: "appearance.theme value" }).click();
  await page.getByRole("option", { name: "System", exact: true }).click();
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
  await page.goto("/debugger/unifiedpush");
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
  await page.route("https://push.example.org/token", async (route) => {
    if (route.request().method() === "OPTIONS") {
      await route.fulfill({
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "POST, OPTIONS",
          "access-control-allow-headers":
            "authorization, content-encoding, content-type, ttl",
        },
      });
      return;
    }
    await route.fulfill({
      status: 201,
      headers: { "access-control-allow-origin": "*" },
      body: "accepted",
    });
  });
  const sent = page.waitForRequest(
    (request) =>
      request.url() === "https://push.example.org/token"
      && request.method() === "POST",
  );
  await page.getByRole("button", { name: "Send test push" }).click();
  const request = await sent;
  expect(request.headers().ttl).toBe("60");
  expect(request.headers()["content-encoding"]).toBe("aes128gcm");
  expect(request.headers().authorization).toContain("vapid t=");
  expect(request.postDataBuffer()?.length).toBeGreaterThan(86);
  await expect(page.getByRole("status")).toContainText(
    "Push server accepted the request (HTTP 201)",
  );
  await expect(page.getByRole("button", { name: /cURL/i })).toHaveCount(0);
});
