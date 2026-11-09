export const PROFILE_OWNER_COMPONENTS = {
  "Webview Kiosk (GitHub / F-Droid / IzzyOnDroid)":
    "uk.nktnet.webviewkiosk/.WebviewKioskAdminReceiver",
  "Webview Kiosk (Google Play)":
    "com.nktnet.webview_kiosk/uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver",
  Dhizuku: "com.rosan.dhizuku/.server.DhizukuDAReceiver",
} as const;

export type ProfileOwnerTarget = keyof typeof PROFILE_OWNER_COMPONENTS;

/** User IDs come from the device; neither user 0 nor the system user is assumed. */
export function buildProfileOwnerCommand(
  userId: string,
  target: ProfileOwnerTarget,
): string | null {
  if (!/^(0|[1-9]\d*)$/.test(userId) || userId.trim() !== userId) return null;
  if (!Number.isSafeInteger(Number(userId)) || Number(userId) > 2147483647)
    return null;
  const component = PROFILE_OWNER_COMPONENTS[target];
  if (!component) return null;
  return `adb shell dpm set-profile-owner --user ${userId} ${component}`;
}
