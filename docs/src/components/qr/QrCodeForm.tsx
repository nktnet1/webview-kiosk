"use client";

/* eslint-disable react/no-children-prop */

import { useForm } from "@tanstack/react-form";
import { useState } from "react";
import QRCode from "react-qr-code";
import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { Separator } from "@/components/ui/separator";
import {
  QrData,
  FormValues,
  FormSchema,
  DownloadSource,
  WifiSecurityType,
} from "@/components/qr/schema";
import QrSelectField from "@/components/qr/fields/QrSelectField";
import QrCheckboxField from "@/components/qr/fields/QrCheckboxField";
import QrTextField from "@/components/qr/fields/QrTextField";

const LATEST_VERSION = {
  code: 115,
  tag: "v0.26.1",
  adminSignatureChecksum: "L-EN4OxwoH84OoeJLKRWZyFOoTxO7qSjJU86Mxp6axU=",
} as const;

export default function QRCodeForm() {
  const [qrValue, setQrValue] = useState<QrData | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const form = useForm({
    defaultValues: {
      downloadSource: "GitHub",
      organizationName: "Webview Kiosk",
      locale: Intl.DateTimeFormat().resolvedOptions().locale,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
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
      leaveAllSystemAppsEnabled: false,
      adminExtras: null,
    } as FormValues,
    validators: { onChange: FormSchema },
    onSubmit: ({ value }) => {
      let downloadLocation = "";
      if (value.downloadSource === "GitHub") {
        downloadLocation = `https://github.com/nktnet1/webview-kiosk/releases/download/${LATEST_VERSION.tag}/WebviewKiosk_${LATEST_VERSION.tag}.apk`;
      } else if (value.downloadSource === "F-Droid") {
        downloadLocation = `https://f-droid.org/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      } else if (value.downloadSource === "IzzyOnDroid") {
        downloadLocation = `https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      }

      const payload: QrData = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
          "uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver",
        "android.app.extra.PROVISIONING_ALLOWED_PROVISIONING_MODE": [2],
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
          downloadLocation,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":
          LATEST_VERSION.adminSignatureChecksum,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED":
          value.leaveAllSystemAppsEnabled,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": value.skipEncryption,
        "android.app.extra.PROVISIONING_WIFI_HIDDEN": value.wifiHidden,
        "android.app.extra.PROVISIONING_USE_MOBILE_DATA": value.useMobileData,
        "android.app.extra.PROVISIONING_ALLOW_OFFLINE": value.allowOffline,
        "android.app.extra.PROVISIONING_KEEP_SCREEN_ON": value.keepScreenOn,
        "android.app.extra.PROVISIONING_ORGANIZATION_NAME":
          value.organizationName,
      };

      if (value.locale)
        payload["android.app.extra.PROVISIONING_LOCALE"] = value.locale;
      if (value.timeZone)
        payload["android.app.extra.PROVISIONING_TIMEZONE"] = value.timeZone;
      if (value.wifiSSID)
        payload["android.app.extra.PROVISIONING_WIFI_SSID"] = value.wifiSSID;
      if (value.wifiPassword)
        payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] =
          value.wifiPassword;
      if (value.wifiSecurityType)
        payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] =
          value.wifiSecurityType;
      if (value.proxyHost)
        payload["android.app.extra.PROVISIONING_WIFI_PROXY_HOST"] =
          value.proxyHost;
      if (value.proxyPort)
        payload["android.app.extra.PROVISIONING_WIFI_PROXY_PORT"] =
          value.proxyPort;
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
          toast.warning("Invalid admin extras", { duration: 3000 });
        }
      }

      setQrValue(payload);
    },
  });

  return (
    <div className="bg-fd-muted rounded-2xl p-6 md:p-10 w-full max-w-7xl flex flex-col items-center justify-center">
      <h1 className="text-4xl wrap-break-word font-bold tracking-tight">
        Generate QR Code
      </h1>

      <form
        className="flex flex-col mt-8 gap-4 w-full max-w-xl"
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
      >
        <form.Field
          name="downloadSource"
          children={(field) => (
            <QrSelectField
              field={field}
              label="Download Source"
              options={DownloadSource.options}
              docsLink="https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
            />
          )}
        />

        <div className="border-y border-dashed py-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="mt-2 min-h-12 whitespace-normal wrap-break-words"
          >
            {showAdvanced ? "Hide Advanced Options" : "Show Advanced Options"}
          </Button>

          {showAdvanced && (
            <div className="flex flex-col gap-4 mt-4">
              {(
                [
                  {
                    key: "leaveAllSystemAppsEnabled",
                    label: "Leave All System Apps Enabled",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED",
                  },
                  {
                    key: "skipEncryption",
                    label: "Skip Encryption",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_SKIP_ENCRYPTION",
                  },
                  {
                    key: "wifiHidden",
                    label: "Wi-Fi Hidden",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_HIDDEN",
                  },
                  {
                    key: "useMobileData",
                    label: "Use Mobile Data",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_USE_MOBILE_DATA",
                  },
                  {
                    key: "allowOffline",
                    label: "Allow Offline",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_ALLOW_OFFLINE",
                  },
                  {
                    key: "keepScreenOn",
                    label: "Keep Screen On",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_KEEP_SCREEN_ON",
                  },
                ] as const
              ).map(({ key, label, docsLink }) => (
                <form.Field
                  key={key}
                  name={key}
                  children={(field) => (
                    <QrCheckboxField
                      field={field}
                      label={label}
                      docsLink={docsLink}
                    />
                  )}
                />
              ))}

              <Separator className="my-2" />

              {(
                [
                  {
                    name: "organizationName",
                    label: "Organization Name",
                    placeholder: "e.g. Webview Kiosk",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_ORGANIZATION_NAME",
                  },
                  {
                    name: "locale",
                    label: "Locale",
                    placeholder: "e.g. en-US",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_LOCALE",
                  },
                  {
                    name: "timeZone",
                    label: "Time Zone",
                    placeholder: "e.g. America/New_York",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_TIMEZONE",
                  },
                  {
                    name: "wifiSSID",
                    label: "Wi-Fi SSID",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_SSID",
                  },
                  {
                    name: "wifiPassword",
                    label: "Wi-Fi Password",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_PASSWORD",
                  },
                  {
                    name: "proxyHost",
                    label: "Proxy Host",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_PROXY_HOST",
                  },
                  {
                    name: "proxyPort",
                    label: "Proxy Port",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_PROXY_PORT",
                  },
                  {
                    name: "proxyBypass",
                    label: "Proxy Bypass",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_PROXY_BYPASS",
                  },
                  {
                    name: "pacUrl",
                    label: "PAC URL",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_PAC_URL",
                  },
                  {
                    name: "localTime",
                    label: "Local Time (ISO)",
                    placeholder: "Optional, e.g. 2026-02-18T09:00:00Z",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_LOCAL_TIME",
                  },
                  {
                    name: "packageDownloadCookieHeader",
                    label: "Package Download Cookie Header",
                    placeholder: "Optional",
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER",
                  },
                  {
                    name: "adminExtras",
                    label: "Admin Extras (JSON)",
                    placeholder: '{"key":"value"}',
                    docsLink:
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE",
                  },
                ] as const
              ).map(({ name, label, placeholder, docsLink }) => (
                <form.Field
                  key={name}
                  name={name}
                  children={(field) => (
                    <QrTextField
                      field={field}
                      label={label}
                      placeholder={placeholder}
                      docsLink={docsLink}
                    />
                  )}
                />
              ))}

              <form.Field
                name="wifiSecurityType"
                children={(field) => (
                  <QrSelectField
                    field={field}
                    label="Wi-Fi Security Type"
                    options={WifiSecurityType.options}
                    docsLink={
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_WIFI_SECURITY_TYPE"
                    }
                  />
                )}
              />
            </div>
          )}
        </div>

        <form.Subscribe
          selector={(s) => [s.canSubmit, s.isSubmitting]}
          children={([canSubmit]) => (
            <Button
              type="submit"
              disabled={!canSubmit}
              className="mt-2 min-h-12 whitespace-normal wrap-break-word"
            >
              Generate QR code for {LATEST_VERSION.tag} ({LATEST_VERSION.code})
            </Button>
          )}
        />
      </form>

      {qrValue && (
        <div className="flex flex-col w-full">
          <div className="mt-10 flex flex-col items-center gap-4">
            <div className="border-2 border-white">
              <QRCode className="max-w-full" value={JSON.stringify(qrValue)} />
            </div>
            <p className="text-sm opacity-70 break-all">
              Scan during device setup
            </p>
          </div>

          <div className="flex justify-center mt-5 gap-x-3">
            <Checkbox
              id="show-json-checkbox"
              checked={showJson}
              onCheckedChange={(state) => setShowJson(state === true)}
            />
            <Label htmlFor="show-json-checkbox">Show JSON</Label>
          </div>

          {showJson && (
            <div className="text-left mt-3">
              <DynamicCodeBlock
                lang="json"
                code={JSON.stringify(qrValue, null, 2)}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
