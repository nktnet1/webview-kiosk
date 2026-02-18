"use client";

/* eslint-disable react/no-children-prop */

import { useForm } from "@tanstack/react-form";
import { useState } from "react";
import QRCode from "react-qr-code";
import * as v from "valibot";
import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectGroup,
  SelectItem,
} from "@/components/ui/select";
import FormFieldInfo from "@/components/common/FormFieldInfo";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";

const LATEST_VERSION = {
  code: 114,
  tag: "v0.26.0",
  checksum: "56c455c3b0fb69ff344e8a2278d865ba80f0e4dad3d1d1e4b777ae637eace769",
} as const;

const DownloadSource = v.picklist(["GitHub", "F-Droid", "IzzyOnDroid"]);
const WifiSecurityType = v.picklist(["NONE", "WPA", "WEP", "EAP"]);

const FormSchema = v.object({
  downloadSource: DownloadSource,
  enterpriseName: v.pipe(v.string(), v.minLength(1, "Required")),
  locale: v.string(),
  timeZone: v.string(),
  leaveAllSystemAppsEnabled: v.boolean(),
  skipEncryption: v.boolean(),
  wifiHidden: v.boolean(),
  wifiSSID: v.nullable(v.string()),
  wifiPassword: v.nullable(v.string()),
  wifiSecurityType: WifiSecurityType,
  proxyHost: v.nullable(v.string()),
  proxyPort: v.nullable(v.string()),
  proxyBypass: v.nullable(v.string()),
  pacUrl: v.nullable(v.string()),
  localTime: v.nullable(v.string()),
  packageDownloadCookieHeader: v.nullable(v.string()),
  adminExtras: v.nullable(v.string()),
});

type FormValues = v.InferInput<typeof FormSchema>;

export default function QRCodeForm() {
  const [qrValue, setQrValue] = useState<string | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const form = useForm({
    defaultValues: {
      downloadSource: "GitHub",
      enterpriseName: "Webview Kiosk",
      locale: Intl.DateTimeFormat().resolvedOptions().locale,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      skipEncryption: false,
      wifiHidden: false,
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
        downloadLocation = `https://github.com/nktnet1/webview-kiosk/releases/download/${LATEST_VERSION.tag}/webview-kiosk.apk`;
      } else if (value.downloadSource === "F-Droid") {
        downloadLocation = `https://f-droid.org/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      } else if (value.downloadSource === "IzzyOnDroid") {
        downloadLocation = `https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      }

      const payload: Record<string, string | boolean | Record<string, string>> =
        {
          "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
            "uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver",
          "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
            downloadLocation,
          "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM":
            LATEST_VERSION.checksum,
          "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED":
            value.leaveAllSystemAppsEnabled,
          "android.app.extra.PROVISIONING_SKIP_ENCRYPTION":
            value.skipEncryption,
          "android.app.extra.PROVISIONING_WIFI_HIDDEN": value.wifiHidden,
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
        payload["android.app.extra.PROVISIONING_LOCAL_TIME"] = value.localTime;
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

      setQrValue(JSON.stringify(payload, null, 2));
    },
  });

  return (
    <div className="bg-fd-muted rounded-2xl p-6 md:p-16 w-full max-w-7xl flex flex-col items-center justify-center">
      <h1 className="mb-8 text-4xl font-bold tracking-tight">
        Generate QR Code
      </h1>

      <form
        className="flex flex-col gap-4 w-full max-w-xl"
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
      >
        <form.Field
          name="downloadSource"
          children={(field) => (
            <div className="text-left">
              <Label htmlFor={field.name}>Download Method</Label>
              <Select
                value={field.state.value}
                onValueChange={(value) =>
                  field.handleChange(v.parse(DownloadSource, value))
                }
              >
                <SelectTrigger className="mt-2 w-full">
                  <SelectValue placeholder="Select method" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {DownloadSource.options.map((o) => (
                      <SelectItem key={o} value={o}>
                        {o}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FormFieldInfo field={field} />
            </div>
          )}
        />

        <form.Field
          name="enterpriseName"
          children={(field) => (
            <div className="text-left">
              <Label htmlFor={field.name}>Enterprise Name</Label>
              <Input
                id={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                placeholder="e.g. Webview Kiosk"
                className="mt-2"
              />
              <FormFieldInfo field={field} />
            </div>
          )}
        />

        <div className="border-y border-dashed py-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? "Hide Advanced Options" : "Show Advanced Options"}
          </Button>
          {showAdvanced && (
            <div className="flex flex-col gap-4 mt-4">
              <form.Field
                name="leaveAllSystemAppsEnabled"
                children={(field) => (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={field.name}
                      checked={field.state.value}
                      onCheckedChange={(state) =>
                        field.handleChange(state === true)
                      }
                    />
                    <Label htmlFor={field.name}>
                      Leave All System Apps Enabled
                    </Label>
                  </div>
                )}
              />

              <form.Field
                name="skipEncryption"
                children={(field) => (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={field.name}
                      checked={field.state.value}
                      onCheckedChange={(state) =>
                        field.handleChange(state === true)
                      }
                    />
                    <Label htmlFor={field.name}>Skip Encryption</Label>
                  </div>
                )}
              />

              <form.Field
                name="wifiHidden"
                children={(field) => (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={field.name}
                      checked={field.state.value}
                      onCheckedChange={(state) =>
                        field.handleChange(state === true)
                      }
                    />
                    <Label htmlFor={field.name}>Wi-Fi Hidden</Label>
                  </div>
                )}
              />

              <form.Field
                name="locale"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Locale</Label>
                    <Input
                      id={field.name}
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      placeholder="e.g. en-US"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="timeZone"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Time Zone</Label>
                    <Input
                      id={field.name}
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      placeholder="e.g. America/New_York"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="wifiSSID"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Wi-Fi SSID</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="wifiPassword"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Wi-Fi Password</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="wifiSecurityType"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Wi-Fi Security Type</Label>
                    <Select
                      value={field.state.value}
                      onValueChange={(value) =>
                        field.handleChange(v.parse(WifiSecurityType, value))
                      }
                    >
                      <SelectTrigger className="mt-2 w-full">
                        <SelectValue placeholder="Select type" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="NONE">NONE</SelectItem>
                          <SelectItem value="WPA">WPA</SelectItem>
                          <SelectItem value="WEP">WEP</SelectItem>
                          <SelectItem value="EAP">EAP</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </div>
                )}
              />

              <form.Field
                name="proxyHost"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Proxy Host</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="proxyPort"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Proxy Port</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="proxyBypass"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Proxy Bypass</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="pacUrl"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>PAC URL</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="localTime"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Local Time (ISO)</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional, e.g. 2026-02-18T09:00:00Z"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="packageDownloadCookieHeader"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>
                      Package Download Cookie Header
                    </Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder="Optional"
                      className="mt-2"
                    />
                  </div>
                )}
              />

              <form.Field
                name="adminExtras"
                children={(field) => (
                  <div className="text-left">
                    <Label htmlFor={field.name}>Admin Extras (JSON)</Label>
                    <Input
                      id={field.name}
                      value={field.state.value ?? ""}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value || null)
                      }
                      placeholder='{"key":"value"}'
                      className="mt-2"
                    />
                  </div>
                )}
              />
            </div>
          )}
        </div>

        <form.Subscribe
          selector={(s) => [s.canSubmit, s.isSubmitting]}
          children={([canSubmit]) => (
            <Button type="submit" disabled={!canSubmit} className="h-12 mt-2">
              Generate QR code for {LATEST_VERSION.tag} ({LATEST_VERSION.code})
            </Button>
          )}
        />
      </form>

      {qrValue && (
        <div className="flex flex-col w-full">
          <div className="mt-10 flex flex-col items-center gap-4">
            <QRCode value={qrValue} size={400} />
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
              <DynamicCodeBlock lang="json" code={qrValue} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
