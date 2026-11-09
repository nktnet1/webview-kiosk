import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProfileOwnerSetup from "#/components/qr/ProfileOwnerSetup";
import QRCodeForm from "#/components/qr/QrCodeForm";

describe("Profile Owner provisioning interface", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("requires a chosen user and does not render a QR or a default user-0 command", () => {
    const markup = renderToStaticMarkup(createElement(ProfileOwnerSetup));
    expect(markup).toContain("Main-user Profile Owner via ADB");
    expect(markup).toContain("adb shell am get-current-user");
    expect(markup).toContain("Full Android user ID");
    expect(markup).toContain('value=""');
    expect(markup).not.toContain("set-profile-owner --user 0");
    expect(markup).not.toContain('data-testid="provisioning-qr"');
    expect(markup).not.toContain('data-testid="profile-owner-command"');
  });

  it.each(["", "/", "/webview-kiosk", "/webview-kiosk/"])(
    "links to setup correctly under base path %j",
    (basePath) => {
      vi.stubEnv("PUBLIC_DOCS_BASE_PATH", basePath);
      const markup = renderToStaticMarkup(createElement(ProfileOwnerSetup));
      const prefix = basePath.replace(/\/$/, "");
      expect(markup).toContain(
        `href="${prefix}/docs/security#profile-owner-mode"`,
      );
    },
  );

  it("makes all three provisioning modes explicit while keeping the default Device Owner form", async () => {
    const router = createRouter({
      routeTree: createRootRoute({ component: QRCodeForm }),
      history: createMemoryHistory({ initialEntries: ["/"] }),
    });
    await router.load();
    const markup = renderToStaticMarkup(
      createElement(RouterProvider, { router }),
    );
    expect(markup).toContain("Device Owner (QR provisioning)");
    expect(markup).toContain("Main-user Profile Owner (ADB)");
    expect(markup).toContain("Managed work profile (separate profile)");
    expect(markup).toContain('id="qr-installation-form"');
  });
});
