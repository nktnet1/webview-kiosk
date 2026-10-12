import { describe, expect, it } from "vitest";
import { buildQrProvisioningPayload } from "#/components/qr/payload";
import type { FormValues } from "#/components/qr/schema";
import type {
  QrInstallationSource,
  QrInstallationVersion,
} from "#/components/qr/version";

const versions: Record<QrInstallationSource, QrInstallationVersion> = {
  GitHub: {
    code: 901,
    tag: "v9.1.0",
    downloadUrl: "https://example.test/github.apk",
    adminSignatureChecksum: "github-signature",
  },
  "F-Droid": {
    code: 900,
    tag: "v9.0.0",
    downloadUrl: "https://example.test/fdroid.apk",
    adminSignatureChecksum: "fdroid-signature",
  },
  IzzyOnDroid: {
    code: 899,
    tag: "v8.9.9",
    downloadUrl: "https://example.test/izzy.apk",
    adminSignatureChecksum: "izzy-signature",
  },
};

const baseValues: FormValues = {
  downloadSource: "GitHub",
  locale: "en-AU",
  timeZone: "Australia/Sydney",
  leaveAllSystemAppsEnabled: false,
  skipEncryption: false,
  wifiHidden: false,
  useMobileData: false,
  allowOffline: false,
  keepScreenOn: false,
  wifiSSID: null,
  wifiPassword: null,
  wifiSecurityType: "WPA",
  proxyHost: null,
  proxyPort: null,
  proxyBypass: null,
  pacUrl: null,
  localTime: null,
  packageDownloadCookieHeader: null,
  adminExtras: null,
};

describe("buildQrProvisioningPayload", () => {
  it.each(["GitHub", "F-Droid", "IzzyOnDroid"] as const)(
    "uses the selected %s release URL and matching signature",
    (downloadSource) => {
      const { payload, invalidAdminExtras } = buildQrProvisioningPayload(
        { ...baseValues, downloadSource },
        versions,
      );

      expect(invalidAdminExtras).toBe(false);
      expect(
        payload[
          "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
        ],
      ).toBe(versions[downloadSource].downloadUrl);
      expect(
        payload[
          "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"
        ],
      ).toBe(versions[downloadSource].adminSignatureChecksum);
      expect(
        payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"],
      ).toBe("uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver");
    },
  );

  it("keeps optional Wi-Fi, policy, time and cookie data in the submitted payload", () => {
    const localTime = "2026-10-09T10:00:00.000Z";
    const { payload } = buildQrProvisioningPayload(
      {
        ...baseValues,
        wifiSSID: "Guest",
        wifiPassword: "example-password",
        wifiSecurityType: "WEP",
        skipEncryption: true,
        keepScreenOn: true,
        localTime,
        packageDownloadCookieHeader: "session=token",
      },
      versions,
    );

    expect(payload).toMatchObject({
      "android.app.extra.PROVISIONING_WIFI_SSID": "Guest",
      "android.app.extra.PROVISIONING_WIFI_PASSWORD": "example-password",
      "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WEP",
      "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
      "android.app.extra.PROVISIONING_KEEP_SCREEN_ON": true,
      "android.app.extra.PROVISIONING_LOCAL_TIME": Date.parse(localTime),
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER":
        "session=token",
    });
  });

  it("preserves valid admin extras", () => {
    const { payload, invalidAdminExtras } = buildQrProvisioningPayload(
      { ...baseValues, adminExtras: '{"fleet":"west"}' },
      versions,
    );

    expect(invalidAdminExtras).toBe(false);
    expect(
      payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"],
    ).toEqual({
      fleet: "west",
    });
  });

  it("reports invalid admin extras without discarding the installation URL", () => {
    const { payload, invalidAdminExtras } = buildQrProvisioningPayload(
      { ...baseValues, downloadSource: "F-Droid", adminExtras: "not json" },
      versions,
    );

    expect(invalidAdminExtras).toBe(true);
    expect(
      payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"],
    ).toEqual({
      error: "Invalid JSON",
    });
    expect(
      payload[
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
      ],
    ).toBe(versions["F-Droid"].downloadUrl);
  });
});
