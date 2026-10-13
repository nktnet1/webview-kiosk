import { sanitizeStoredCredentials } from "../remote-control/credential-json";
import type { PushCredentials } from "./web-push";

export const STORAGE_KEY = "webview-kiosk:unifiedpush-debug:v1";

export const EMPTY_CREDENTIALS: PushCredentials = {
  endpoint: "",
  p256dh: "",
  auth: "",
  vapidPublicKey: "",
  vapidPrivateKey: "",
  vapidSubject: "",
};

export function parseStoredCredentials(raw: string | null): PushCredentials {
  if (!raw) return { ...EMPTY_CREDENTIALS };
  try {
    return sanitizeStoredCredentials(JSON.parse(raw), EMPTY_CREDENTIALS);
  } catch {
    return { ...EMPTY_CREDENTIALS };
  }
}

export function persistCredentials(
  storage: Pick<Storage, "setItem" | "removeItem">,
  credentials: PushCredentials,
): void {
  if (Object.values(credentials).every((value) => value === "")) {
    storage.removeItem(STORAGE_KEY);
  } else {
    storage.setItem(STORAGE_KEY, JSON.stringify(credentials));
  }
}
