import type { FormValues, QrData } from "#/components/qr/schema";
import {
  QR_INSTALLATION_VERSIONS,
  type QrInstallationSource,
  type QrInstallationVersion,
} from "#/components/qr/version";

/** Build the exact data submitted to the QR renderer from the selected source. */
export function buildQrProvisioningPayload(
  value: FormValues,
  versions: Record<
    QrInstallationSource,
    QrInstallationVersion
  > = QR_INSTALLATION_VERSIONS,
): { payload: QrData; invalidAdminExtras: boolean } {
  const installationVersion = versions[value.downloadSource];
  let invalidAdminExtras = false;

  const payload: QrData = {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
      "uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
      installationVersion.downloadUrl,
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":
      installationVersion.adminSignatureChecksum,
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED":
      value.leaveAllSystemAppsEnabled,
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": value.skipEncryption,
    "android.app.extra.PROVISIONING_WIFI_HIDDEN": value.wifiHidden,
    "android.app.extra.PROVISIONING_USE_MOBILE_DATA": value.useMobileData,
    "android.app.extra.PROVISIONING_ALLOW_OFFLINE": value.allowOffline,
    "android.app.extra.PROVISIONING_KEEP_SCREEN_ON": value.keepScreenOn,
  };

  if (value.locale)
    payload["android.app.extra.PROVISIONING_LOCALE"] = value.locale;
  if (value.timeZone)
    payload["android.app.extra.PROVISIONING_TIME_ZONE"] = value.timeZone;
  if (value.wifiSSID)
    payload["android.app.extra.PROVISIONING_WIFI_SSID"] = value.wifiSSID;
  if (value.wifiPassword)
    payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] =
      value.wifiPassword;
  if (value.wifiSecurityType)
    payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] =
      value.wifiSecurityType;
  if (value.proxyHost)
    payload["android.app.extra.PROVISIONING_WIFI_PROXY_HOST"] = value.proxyHost;
  if (value.proxyPort)
    payload["android.app.extra.PROVISIONING_WIFI_PROXY_PORT"] = value.proxyPort;
  if (value.proxyBypass)
    payload["android.app.extra.PROVISIONING_WIFI_PROXY_BYPASS"] =
      value.proxyBypass;
  if (value.pacUrl)
    payload["android.app.extra.PROVISIONING_WIFI_PAC_URL"] = value.pacUrl;
  if (value.localTime)
    payload["android.app.extra.PROVISIONING_LOCAL_TIME"] = new Date(
      value.localTime,
    ).getTime();
  if (value.packageDownloadCookieHeader)
    payload[
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER"
    ] = value.packageDownloadCookieHeader;

  if (value.adminExtras) {
    try {
      payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] =
        JSON.parse(value.adminExtras);
    } catch {
      payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = {
        error: "Invalid JSON",
      };
      invalidAdminExtras = true;
    }
  }

  return { payload, invalidAdminExtras };
}
