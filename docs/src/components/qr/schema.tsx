"use client";
import * as v from "valibot";

export const DownloadSource = v.picklist(["GitHub", "F-Droid", "IzzyOnDroid"]);
export const WifiSecurityType = v.picklist(["NONE", "WPA", "WEP", "EAP"]);

export const FormSchema = v.object({
  downloadSource: DownloadSource,
  organizationName: v.pipe(v.string(), v.minLength(1, "Required")),
  locale: v.string(),
  timeZone: v.string(),
  leaveAllSystemAppsEnabled: v.boolean(),
  skipEncryption: v.boolean(),
  wifiHidden: v.boolean(),
  useMobileData: v.boolean(),
  allowOffline: v.boolean(),
  keepScreenOn: v.boolean(),
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

export type FormValues = v.InferInput<typeof FormSchema>;

export type QrData = Record<
  string,
  string | boolean | number | Record<string, string> | Array<number>
>;
