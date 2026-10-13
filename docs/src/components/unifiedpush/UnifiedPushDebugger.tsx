"use client";

import { Callout } from "fumadocs-ui/components/callout";
import { type FormEvent, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  createRemotePayload,
  type MessageKind,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import { Button } from "#/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "#/components/ui/card";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import {
  EMPTY_CREDENTIALS,
  parseStoredCredentials,
  persistCredentials,
  STORAGE_KEY,
} from "./storage";
import {
  createCurlCommand,
  generateVapidKeys,
  MAX_PUSH_PAYLOAD_BYTES,
  MAX_PUSH_TTL,
  type PreparedPush,
  type PushCredentials,
  parsePushEndpoint,
  prepareWebPush,
} from "./web-push";

type DeliveryMode = "encrypted" | "plaintext";
type Feedback = { kind: "success" | "error" | "warning"; message: string };

const fields: {
  key: keyof PushCredentials;
  label: string;
  placeholder: string;
  hint: string;
  secret?: boolean;
  encryptedOnly?: boolean;
}[] = [
  {
    key: "endpoint",
    label: "Push endpoint URL",
    placeholder: "https://ntfy.sh/your-endpoint?up=1",
    hint: "The capability URL supplied by the UnifiedPush distributor.",
    secret: true,
  },
  {
    key: "p256dh",
    label: "Endpoint public key (p256dh)",
    placeholder: "Base64url (87 characters)",
    hint: "The endpoint public key from the app, not the VAPID key.",
    encryptedOnly: true,
  },
  {
    key: "auth",
    label: "Endpoint auth secret",
    placeholder: "Base64url (22 characters)",
    hint: "The 16-byte encryption secret from the app.",
    secret: true,
    encryptedOnly: true,
  },
  {
    key: "vapidPublicKey",
    label: "VAPID public key",
    placeholder: "Base64url (87 characters)",
    hint: "Must be the same public key used when registering the device.",
    encryptedOnly: true,
  },
  {
    key: "vapidPrivateKey",
    label: "VAPID private key",
    placeholder: "Base64url (43 characters)",
    hint: "Keep private; only used for local signing.",
    secret: true,
    encryptedOnly: true,
  },
  {
    key: "vapidSubject",
    label: "VAPID contact (optional)",
    placeholder: "mailto:you@example.com",
    hint: "A mailto: address or HTTPS URL.",
    encryptedOnly: true,
  },
];

function describeError(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function saveEncryptedBody(push: PreparedPush) {
  const url = URL.createObjectURL(
    new Blob([push.body], { type: "application/octet-stream" }),
  );
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "unifiedpush-message.bin";
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export default function UnifiedPushDebugger() {
  const [credentials, setCredentials] = useState<PushCredentials>({
    ...EMPTY_CREDENTIALS,
  });
  const [loaded, setLoaded] = useState(false);
  const [storageWarning, setStorageWarning] = useState<string | null>(null);
  const [showSecrets, setShowSecrets] = useState(false);
  const [mode, setMode] = useState<DeliveryMode>("encrypted");
  const [messageKind, setMessageKind] = useState<MessageKind>("command");
  const [payload, setPayload] = useState(() =>
    stringifyPayload(createRemotePayload("unifiedpush", "command")),
  );
  const [ttl, setTtl] = useState("60");
  const [busy, setBusy] = useState(false);
  const [generatingKeys, setGeneratingKeys] = useState(false);
  const [prepared, setPrepared] = useState<PreparedPush | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  useEffect(() => {
    try {
      setCredentials(
        parseStoredCredentials(window.localStorage.getItem(STORAGE_KEY)),
      );
    } catch {
      setStorageWarning(
        "Browser storage is unavailable; credentials will not persist.",
      );
    }
    setLoaded(true);
  }, []);

  useEffect(() => {
    if (!loaded) return;
    try {
      persistCredentials(window.localStorage, credentials);
      setStorageWarning(null);
    } catch {
      setStorageWarning(
        "Browser storage is unavailable; credentials will not persist.",
      );
    }
  }, [credentials, loaded]);

  function invalidateRequest() {
    setPrepared(null);
    setFeedback(null);
  }

  function changeCredential(key: keyof PushCredentials, value: string) {
    setCredentials((current) => ({ ...current, [key]: value }));
    invalidateRequest();
  }

  function changePayload(value: string) {
    setPayload(value);
    invalidateRequest();
  }

  async function createKeys() {
    if (generatingKeys || busy) return;
    if (
      (credentials.vapidPublicKey || credentials.vapidPrivateKey)
      && !window.confirm(
        "Replace the saved VAPID keys? You will need to re-register the device using the new public key.",
      )
    ) {
      return;
    }
    setGeneratingKeys(true);
    try {
      const keys = await generateVapidKeys();
      setCredentials((current) => ({
        ...current,
        vapidPublicKey: keys.publicKey,
        vapidPrivateKey: keys.privateKey,
      }));
      invalidateRequest();
      toast.success(
        "VAPID key pair generated. Register the device with this public key.",
      );
    } catch (error) {
      toast.error(describeError(error));
    } finally {
      setGeneratingKeys(false);
    }
  }

  async function createRequest(): Promise<PreparedPush | null> {
    try {
      const request = await prepareWebPush(
        credentials,
        validateRemotePayload(payload, "unifiedpush", messageKind),
        ttl.trim() ? Number(ttl) : Number.NaN,
      );
      setPrepared(request);
      return request;
    } catch (error) {
      setPrepared(null);
      setFeedback({ kind: "error", message: describeError(error) });
      return null;
    }
  }

  async function handlePrepare() {
    if (busy || generatingKeys) return;
    setBusy(true);
    setFeedback(null);
    setPrepared(null);
    try {
      const request = await createRequest();
      if (request) {
        setFeedback({
          kind: "success",
          message:
            "Encrypted request prepared locally. Download the binary body and copy the cURL command below.",
        });
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleSend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || generatingKeys) return;
    setBusy(true);
    setFeedback(null);
    setPrepared(null);

    try {
      if (mode === "encrypted") {
        const request = await createRequest();
        if (!request) return;

        let response: Response;
        try {
          response = await fetch(request.endpoint, {
            method: "POST",
            headers: request.headers,
            body: request.body,
            credentials: "omit",
            redirect: "error",
            cache: "no-store",
            signal: AbortSignal.timeout(15_000),
          });
        } catch {
          setFeedback({
            kind: "warning",
            message:
              "The browser could not complete the request (often because the push server blocks CORS). No delivery is confirmed. Use the encrypted cURL fallback below.",
          });
          return;
        }

        setFeedback(
          response.ok
            ? {
                kind: "success",
                message: `Push server accepted the request (HTTP ${response.status}). Check the device's UnifiedPush debug logs for delivery.`,
              }
            : {
                kind: "error",
                message: `Push server rejected the request (HTTP ${response.status}). Check the endpoint and keys; you can also retry using cURL.`,
              },
        );
      } else {
        const endpoint = parsePushEndpoint(credentials.endpoint);
        const message = validateRemotePayload(
          payload,
          "unifiedpush",
          messageKind,
        );
        if (new TextEncoder().encode(message).length > MAX_PUSH_PAYLOAD_BYTES) {
          throw new Error(
            `Payload must be at most ${MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes.`,
          );
        }

        // Only ntfy-compatible unencrypted distributors can process this mode.
        // A simple request avoids CORS preflight, but an opaque response does
        // NOT indicate whether the server accepted or delivered the message.
        const response = await fetch(endpoint, {
          method: "POST",
          mode: "no-cors",
          headers: { "Content-Type": "text/plain" },
          body: message,
          credentials: "omit",
          redirect: "error",
          signal: AbortSignal.timeout(15_000),
        });
        setFeedback(
          response.type === "opaque"
            ? {
                kind: "warning",
                message:
                  "Request sent without a readable response. Acceptance and delivery cannot be verified in no-CORS mode; check the device debug logs.",
              }
            : {
                kind: response.ok ? "success" : "error",
                message: `Push server returned HTTP ${response.status}. Check the device debug logs.`,
              },
        );
      }
    } catch (error) {
      setFeedback({ kind: "error", message: describeError(error) });
    } finally {
      setBusy(false);
    }
  }

  function clearCredentials() {
    setCredentials({ ...EMPTY_CREDENTIALS });
    invalidateRequest();
    try {
      window.localStorage.removeItem(STORAGE_KEY);
      toast.success("Saved credentials cleared");
    } catch {
      toast.warning(
        "Fields cleared, but browser storage could not be accessed.",
      );
    }
  }

  async function copyCurl() {
    if (!prepared) return;
    try {
      await navigator.clipboard.writeText(createCurlCommand(prepared));
      toast.success("cURL command copied");
    } catch {
      toast.error("Unable to copy. Check the browser clipboard permissions.");
    }
  }

  return (
    <div className="bg-fd-muted rounded-2xl p-5 md:p-10 w-full max-w-6xl space-y-6">
      <div className="text-center space-y-3">
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight">
          UnifiedPush Debugger
        </h1>
        <p className="mx-auto max-w-3xl text-sm md:text-base text-muted-foreground">
          Send test commands and settings to Webview Kiosk. Copy the
          registration credentials from{" "}
          <a className="underline" href="/docs/settings/unifiedpush">
            Settings → UnifiedPush
          </a>
          . Encryption and VAPID signing are performed on this device.
        </p>
      </div>

      <Callout title="Privacy warning" type="warning">
        Credentials are automatically saved, unencrypted, in this browser's
        local storage. They are never uploaded to the docs site. Web Push
        requests go directly to your selected endpoint. Avoid using this
        debugger on a shared device and clear credentials when finished.
      </Callout>
      {storageWarning && (
        <p role="alert" className="text-sm text-destructive">
          {storageWarning}
        </p>
      )}

      <form onSubmit={handleSend}>
        <fieldset
          disabled={busy || generatingKeys}
          className="space-y-6 disabled:opacity-70"
        >
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
            <Card>
              <CardHeader>
                <CardTitle>Connection &amp; credentials</CardTitle>
                <CardDescription>
                  Paste the values from the device registration screen.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="push-mode">Delivery mode</Label>
                  <select
                    id="push-mode"
                    className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm"
                    value={mode}
                    onChange={(event) => {
                      setMode(event.target.value as DeliveryMode);
                      invalidateRequest();
                    }}
                  >
                    <option value="encrypted">
                      Encrypted Web Push (recommended)
                    </option>
                    <option value="plaintext">
                      Plaintext (ntfy-compatible only)
                    </option>
                  </select>
                </div>
                {mode === "plaintext" && (
                  <Callout title="Unencrypted delivery" type="warning">
                    This mode is not encrypted. Enable{" "}
                    <strong>Process Unencrypted Messages</strong> in the app.
                    The browser cannot read the server response in no-CORS mode.
                  </Callout>
                )}
                {fields
                  .filter(
                    (field) => mode === "encrypted" || !field.encryptedOnly,
                  )
                  .map((field) => (
                    <div className="space-y-2" key={field.key}>
                      <Label htmlFor={`push-${field.key}`}>{field.label}</Label>
                      <Input
                        id={`push-${field.key}`}
                        type={
                          field.secret && !showSecrets ? "password" : "text"
                        }
                        value={credentials[field.key]}
                        placeholder={field.placeholder}
                        autoComplete="off"
                        spellCheck={false}
                        onChange={(event) =>
                          changeCredential(field.key, event.target.value)
                        }
                      />
                      <p className="text-xs text-muted-foreground">
                        {field.hint}
                      </p>
                    </div>
                  ))}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowSecrets((value) => !value)}
                  >
                    {showSecrets ? "Hide credentials" : "Reveal credentials"}
                  </Button>
                  {mode === "encrypted" && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={createKeys}
                    >
                      {generatingKeys ? "Generating…" : "Generate VAPID keys"}
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={clearCredentials}
                  >
                    Clear credentials
                  </Button>
                </div>
                {mode === "encrypted" && (
                  <p className="text-xs text-muted-foreground">
                    New VAPID keys require updating the public key in the app
                    and re-registering before sending a push notification.
                  </p>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Message</CardTitle>
                <CardDescription>
                  Configure a command or settings update, or edit raw JSON.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <RemoteMessageEditor
                  transport="unifiedpush"
                  kind={messageKind}
                  value={payload}
                  onChange={changePayload}
                  onKindChange={(kind) => {
                    setMessageKind(kind);
                    invalidateRequest();
                  }}
                />
                <p className="text-xs text-muted-foreground">
                  Maximum {MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes after JSON
                  minification. See{" "}
                  <a
                    className="underline"
                    href="/docs/settings/unifiedpush/messages"
                  >
                    message formats
                  </a>
                  .
                </p>
                {mode === "encrypted" && (
                  <div className="space-y-2">
                    <Label htmlFor="push-ttl">Time to live (seconds)</Label>
                    <Input
                      id="push-ttl"
                      type="number"
                      min="0"
                      max={MAX_PUSH_TTL}
                      step="1"
                      value={ttl}
                      onChange={(event) => {
                        setTtl(event.target.value);
                        invalidateRequest();
                      }}
                    />
                  </div>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="submit"
                    className="min-h-10"
                    disabled={!loaded || busy || generatingKeys}
                  >
                    {busy ? "Sending…" : "Send test push"}
                  </Button>
                  {mode === "encrypted" && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={handlePrepare}
                    >
                      Prepare cURL fallback
                    </Button>
                  )}
                </div>
                {feedback
                  && (feedback.kind === "warning" ? (
                    <div role="status">
                      <Callout title="Delivery warning" type="warning">
                        {feedback.message}
                      </Callout>
                    </div>
                  ) : (
                    <div
                      role="status"
                      className={`rounded-md border p-3 text-sm ${feedback.kind === "error" ? "border-destructive text-destructive" : "border-border"}`}
                    >
                      {feedback.message}
                    </div>
                  ))}
                {mode === "encrypted" && prepared && (
                  <div className="space-y-3 rounded-lg border border-border p-3 text-sm">
                    <h2 className="font-semibold">Encrypted cURL fallback</h2>
                    <p className="text-muted-foreground">
                      Download the encrypted binary, place it beside your
                      terminal and run the copied command. The command contains
                      a short-lived VAPID token. Do not share it or the endpoint
                      URL.
                    </p>
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => saveEncryptedBody(prepared)}
                      >
                        Download .bin
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={copyCurl}
                      >
                        Copy cURL command
                      </Button>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </fieldset>
      </form>
    </div>
  );
}
