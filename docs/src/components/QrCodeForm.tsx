"use client";

/* eslint-disable react/no-children-prop */
import { useForm } from "@tanstack/react-form";
import { useState } from "react";
import QRCode from "react-qr-code";
import * as v from "valibot";

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

const LATEST_VERSION = {
  code: 114,
  tag: "v0.26.0",
  checksum: "56c455c3b0fb69ff344e8a2278d865ba80f0e4dad3d1d1e4b777ae637eace769",
} as const;

const DownloadMethod = v.picklist(["GitHub", "F-Droid", "IzzyOnDroid"]);

const FormSchema = v.object({
  downloadMethod: DownloadMethod,
  enterpriseName: v.pipe(v.string(), v.minLength(1, "Required")),
});

type FormValues = v.InferInput<typeof FormSchema>;

export default function QRCodeForm() {
  const [qrValue, setQrValue] = useState<string | null>(null);

  const form = useForm({
    defaultValues: {
      downloadMethod: "GitHub",
      enterpriseName: "Webview Kiosk",
    } as FormValues,
    validators: {
      onChange: FormSchema,
    },
    onSubmit: ({ value }) => {
      let downloadLocation = "";
      if (value.downloadMethod === "GitHub") {
        downloadLocation = `https://github.com/nktnet1/webview-kiosk/releases/download/${LATEST_VERSION.tag}/webview-kiosk.apk`;
      } else if (value.downloadMethod === "F-Droid") {
        downloadLocation = `https://f-droid.org/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      } else if (value.downloadMethod === "IzzyOnDroid") {
        downloadLocation = `https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_${LATEST_VERSION.code}.apk`;
      }

      const payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
          "uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
          downloadLocation,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM":
          LATEST_VERSION.checksum,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
        "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
          enterpriseName: value.enterpriseName,
        },
      };

      setQrValue(JSON.stringify(payload));
    },
  });

  return (
    <div className="bg-fd-muted rounded-2xl p-6 md:p-16 w-full max-w-xl">
      <h1 className="mb-8 text-4xl font-bold tracking-tight">
        Generate QR Code
      </h1>

      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
      >
        <form.Field
          name="downloadMethod"
          children={(field) => (
            <div className="text-left">
              <Label htmlFor={field.name}>Download Method</Label>
              <Select
                value={field.state.value}
                onValueChange={(value) =>
                  field.handleChange(v.parse(DownloadMethod, value))
                }
              >
                <SelectTrigger className="mt-2 w-full">
                  <SelectValue placeholder="Select method" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="GitHub">GitHub</SelectItem>
                    <SelectItem value="F-Droid">F-Droid</SelectItem>
                    <SelectItem value="IzzyOnDroid">IzzyOnDroid</SelectItem>
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

        <form.Subscribe
          selector={(s) => [s.canSubmit, s.isSubmitting]}
          children={([canSubmit]) => (
            <Button type="submit" disabled={!canSubmit} className="h-12 mt-2">
              Generate QR for {LATEST_VERSION.tag} ({LATEST_VERSION.code})
            </Button>
          )}
        />
      </form>

      {qrValue && (
        <div className="mt-10 flex flex-col items-center gap-4">
          <QRCode value={qrValue} size={256} />
          <p className="text-sm opacity-70 break-all">
            Scan during device setup
          </p>
        </div>
      )}
    </div>
  );
}
