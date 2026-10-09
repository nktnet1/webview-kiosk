"use client";

/* eslint-disable react/no-children-prop */

import { useForm } from "@tanstack/react-form";
import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import { useState } from "react";
import QRCode from "react-qr-code";
import { toast } from "sonner";
import QrCheckboxField from "#/components/qr/fields/QrCheckboxField";
import QrSelectField from "#/components/qr/fields/QrSelectField";
import QrTextField from "#/components/qr/fields/QrTextField";
import { buildQrProvisioningPayload } from "#/components/qr/payload";
import {
  DownloadSource,
  FormSchema,
  type FormValues,
  type QrData,
  WifiSecurityType,
} from "#/components/qr/schema";
import { QR_INSTALLATION_VERSIONS } from "#/components/qr/version";
import { Button } from "#/components/ui/button";
import { Checkbox } from "#/components/ui/checkbox";
import { Label } from "#/components/ui/label";
import { Separator } from "#/components/ui/separator";

export default function QRCodeForm() {
  const [qrValue, setQrValue] = useState<QrData | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const form = useForm({
    defaultValues: {
      downloadSource: "GitHub",
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
      const { payload, invalidAdminExtras } = buildQrProvisioningPayload(value);
      if (invalidAdminExtras) {
        toast.warning("Invalid admin extras", { duration: 3000 });
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
            className="my-2 min-h-12 whitespace-normal wrap-break-words"
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
                      "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_TIME_ZONE",
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
          selector={(s) => ({
            canSubmit: s.canSubmit,
            downloadSource: s.values.downloadSource,
          })}
          children={({ canSubmit, downloadSource }) => {
            const installationVersion =
              QR_INSTALLATION_VERSIONS[downloadSource];

            return (
              <Button
                type="submit"
                disabled={!canSubmit}
                className="mt-2 min-h-12 whitespace-normal wrap-break-word"
              >
                {`Generate QR code for ${installationVersion.tag} (${installationVersion.code})`}
              </Button>
            );
          }}
        />
      </form>

      {qrValue && (
        <div className="flex flex-col w-full">
          <div className="mt-10 flex flex-col items-center gap-4">
            <div className="border-10 border-white">
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
