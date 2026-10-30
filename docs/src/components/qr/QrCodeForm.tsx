import { useForm } from "@tanstack/react-form";
import { ChevronDown, QrCode, SlidersHorizontal, Wifi } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import QrCheckboxField from "#/components/qr/fields/QrCheckboxField";
import QrSelectField from "#/components/qr/fields/QrSelectField";
import QrTextField from "#/components/qr/fields/QrTextField";
import { buildQrProvisioningPayload } from "#/components/qr/payload";
import QrCodePreview from "#/components/qr/QrCodePreview";
import {
  DownloadSource,
  FormSchema,
  type FormValues,
  type QrData,
  WifiSecurityType,
} from "#/components/qr/schema";
import {
  QR_INSTALLATION_VERSIONS,
  type QrInstallationSource,
} from "#/components/qr/version";
import { Button } from "#/components/ui/button";

const androidDocs =
  "https://developer.android.com/reference/android/app/admin/DevicePolicyManager#EXTRA_PROVISIONING_";

const deviceOptions = [
  {
    name: "leaveAllSystemAppsEnabled",
    label: "Leave all system apps enabled",
    docsKey: "LEAVE_ALL_SYSTEM_APPS_ENABLED",
  },
  {
    name: "skipEncryption",
    label: "Skip encryption",
    docsKey: "SKIP_ENCRYPTION",
  },
  {
    name: "useMobileData",
    label: "Use mobile data",
    docsKey: "USE_MOBILE_DATA",
  },
  { name: "allowOffline", label: "Allow offline", docsKey: "ALLOW_OFFLINE" },
  {
    name: "keepScreenOn",
    label: "Keep screen on",
    docsKey: "KEEP_SCREEN_ON",
  },
] as const;

const advancedTextGroups = [
  {
    title: "Language and time",
    fields: [
      {
        name: "locale",
        label: "Locale",
        placeholder: "e.g. en-US",
        docsKey: "LOCALE",
      },
      {
        name: "timeZone",
        label: "Time zone",
        placeholder: "e.g. Australia/Sydney",
        docsKey: "TIME_ZONE",
      },
      {
        name: "localTime",
        label: "Local time (ISO)",
        placeholder: "e.g. 2026-02-18T09:00:00Z",
        docsKey: "LOCAL_TIME",
      },
    ],
  },
  {
    title: "Proxy and download",
    fields: [
      {
        name: "proxyHost",
        label: "Proxy host",
        placeholder: "e.g. proxy.example.com",
        docsKey: "WIFI_PROXY_HOST",
      },
      {
        name: "proxyPort",
        label: "Proxy port",
        placeholder: "e.g. 8080",
        docsKey: "WIFI_PROXY_PORT",
      },
      {
        name: "proxyBypass",
        label: "Proxy bypass",
        placeholder: "e.g. localhost,*.example.com",
        docsKey: "WIFI_PROXY_BYPASS",
      },
      {
        name: "pacUrl",
        label: "PAC URL",
        placeholder: "https://example.com/proxy.pac",
        docsKey: "WIFI_PAC_URL",
      },
      {
        name: "packageDownloadCookieHeader",
        label: "Package download cookie header",
        placeholder: "Optional",
        docsKey: "DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER",
      },
    ],
  },
] as const;

export default function QRCodeForm() {
  const [result, setResult] = useState<{
    payload: QrData;
    downloadSource: QrInstallationSource;
    formValues: string;
  } | null>(null);
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
      setResult({
        payload,
        downloadSource: value.downloadSource,
        formValues: JSON.stringify(value),
      });
    },
  });

  return (
    <div className="mx-auto w-full max-w-6xl text-left">
      <header className="mb-8 max-w-2xl space-y-3">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          QR installation
        </h1>
        <p className="leading-relaxed text-muted-foreground">
          Set up Webview Kiosk as the device owner on a new or factory-reset
          Android device. Choose your settings, generate a code, and scan it
          during device setup.
        </p>
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]">
        <form
          id="qr-installation-form"
          aria-label="Installation settings"
          className="min-w-0 overflow-hidden rounded-2xl border bg-card text-card-foreground"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            form.handleSubmit();
          }}
        >
          <div className="space-y-5 p-5 sm:p-6">
            <div>
              <h2 className="font-semibold">Installation settings</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Choose where your device downloads the app.
              </p>
            </div>
            <form.Field
              name="downloadSource"
              children={(field) => (
                <QrSelectField
                  field={field}
                  label="Download source"
                  options={DownloadSource.options}
                  docsLink={`${androidDocs}DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`}
                />
              )}
            />
            <form.Subscribe
              selector={(s) => s.values.downloadSource}
              children={(downloadSource) => {
                const version = QR_INSTALLATION_VERSIONS[downloadSource];
                return (
                  <p className="text-xs text-muted-foreground">
                    Version {version.tag} · Build {version.code}
                  </p>
                );
              }}
            />
          </div>

          <fieldset className="min-w-0 border-t p-5 sm:p-6">
            <legend className="sr-only">Wi-Fi settings (optional)</legend>
            <div className="mb-5">
              <h3 className="flex items-center gap-2 text-sm font-semibold">
                <Wifi aria-hidden="true" className="size-4" />
                Wi-Fi
                <span className="ml-auto text-xs font-normal text-muted-foreground">
                  Optional
                </span>
              </h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Pre-fill a network, or connect on the device during setup.
              </p>
            </div>
            <div className="space-y-4">
              <form.Field
                name="wifiSSID"
                children={(field) => (
                  <QrTextField
                    field={field}
                    label="Network name (SSID)"
                    placeholder="Enter your Wi-Fi network name"
                    docsLink={`${androidDocs}WIFI_SSID`}
                  />
                )}
              />
              <div className="grid gap-4 sm:grid-cols-2">
                <form.Field
                  name="wifiPassword"
                  children={(field) => (
                    <QrTextField
                      field={field}
                      label="Wi-Fi password"
                      placeholder="Enter network password"
                      docsLink={`${androidDocs}WIFI_PASSWORD`}
                      secret
                    />
                  )}
                />
                <form.Field
                  name="wifiSecurityType"
                  children={(field) => (
                    <QrSelectField
                      field={field}
                      label="Security type"
                      options={WifiSecurityType.options}
                      docsLink={`${androidDocs}WIFI_SECURITY_TYPE`}
                    />
                  )}
                />
              </div>
              <form.Field
                name="wifiHidden"
                children={(field) => (
                  <QrCheckboxField
                    field={field}
                    label="Hidden network"
                    docsLink={`${androidDocs}WIFI_HIDDEN`}
                  />
                )}
              />
            </div>
          </fieldset>

          <div className="border-t">
            <Button
              type="button"
              variant="ghost"
              aria-expanded={showAdvanced}
              aria-controls="qr-advanced-options"
              onClick={() => setShowAdvanced(!showAdvanced)}
              className="h-auto min-h-16 w-full justify-start gap-3 rounded-none px-5 py-4 sm:px-6"
            >
              <SlidersHorizontal aria-hidden="true" className="size-4" />
              Advanced options
              <ChevronDown
                aria-hidden="true"
                className={`ml-auto size-4 transition-transform ${showAdvanced ? "rotate-180" : ""}`}
              />
            </Button>
            <div
              id="qr-advanced-options"
              hidden={!showAdvanced}
              className="space-y-6 px-5 pb-6 sm:px-6"
            >
              <fieldset className="min-w-0">
                <legend className="mb-3 text-sm font-semibold">
                  Device behaviour
                </legend>
                <div className="space-y-1">
                  {deviceOptions.map(({ name, label, docsKey }) => (
                    <form.Field
                      key={name}
                      name={name}
                      children={(field) => (
                        <QrCheckboxField
                          field={field}
                          label={label}
                          docsLink={`${androidDocs}${docsKey}`}
                        />
                      )}
                    />
                  ))}
                </div>
              </fieldset>

              {advancedTextGroups.map(({ title, fields }) => (
                <fieldset key={title} className="min-w-0 border-t pt-5">
                  <legend className="pr-3 text-sm font-semibold">
                    {title}
                  </legend>
                  <div className="grid gap-4 sm:grid-cols-2">
                    {fields.map(({ name, label, placeholder, docsKey }) => (
                      <form.Field
                        key={name}
                        name={name}
                        children={(field) => (
                          <QrTextField
                            field={field}
                            label={label}
                            placeholder={placeholder}
                            docsLink={`${androidDocs}${docsKey}`}
                          />
                        )}
                      />
                    ))}
                  </div>
                </fieldset>
              ))}

              <div className="border-t pt-5">
                <form.Field
                  name="adminExtras"
                  children={(field) => (
                    <QrTextField
                      field={field}
                      label="Admin extras (JSON)"
                      placeholder={'{"key": "value"}'}
                      docsLink={`${androidDocs}ADMIN_EXTRAS_BUNDLE`}
                      multiline
                    />
                  )}
                />
              </div>
            </div>
          </div>

          <div className="border-t bg-muted/30 p-5 sm:p-6">
            <form.Subscribe
              selector={(s) => s.canSubmit}
              children={(canSubmit) => (
                <Button
                  type="submit"
                  disabled={!canSubmit}
                  className="min-h-11 w-full whitespace-normal"
                >
                  <QrCode aria-hidden="true" className="size-4" />
                  Generate QR code
                </Button>
              )}
            />
            <p className="mt-3 text-center text-xs leading-relaxed text-muted-foreground">
              The code includes your Wi-Fi password if provided. Share it only
              with people setting up your device.
            </p>
          </div>
        </form>

        <form.Subscribe
          selector={(s) => ({
            formValues: JSON.stringify(s.values),
            canSubmit: s.canSubmit,
          })}
          children={({ formValues, canSubmit }) => (
            <QrCodePreview
              result={result}
              hasChanges={result !== null && result.formValues !== formValues}
              canSubmit={canSubmit}
            />
          )}
        />
      </div>
    </div>
  );
}
