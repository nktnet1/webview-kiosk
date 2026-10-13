"use client";

import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { useEffect, useState } from "react";
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";
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
type DebugFormValues = {
  credentials: PushCredentials;
  mode: DeliveryMode;
  ttl: string;
};
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
  const [loaded, setLoaded] = useState(false);
  const [storageWarning, setStorageWarning] = useState<string | null>(null);
  const [showSecrets, setShowSecrets] = useState(false);
  const [messageKind, setMessageKind] = useState<MessageKind>("command");
  const [payload, setPayload] = useState(() =>
    stringifyPayload(createRemotePayload("unifiedpush", "command")),
  );
  const [busy, setBusy] = useState(false);
  const [generatingKeys, setGeneratingKeys] = useState(false);
  const [prepared, setPrepared] = useState<PreparedPush | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  // Keep fields, validation and submit values in TanStack Form. Only the
  // credentials (not TTL or delivery mode) are saved to browser storage.
  const form = useForm({
    defaultValues: {
      credentials: { ...EMPTY_CREDENTIALS },
      mode: "encrypted",
      ttl: "60",
    } as DebugFormValues,
    onSubmit: async ({ value }) => handleSend(value),
  });

  useEffect(() => {
    try {
      form.setFieldValue(
        "credentials",
        parseStoredCredentials(window.localStorage.getItem(STORAGE_KEY)),
      );
    } catch {
      setStorageWarning(
        "Browser storage is unavailable; credentials will not persist.",
      );
    }
    setLoaded(true);
  }, [form]);

  function persistEditedCredentials(credentials: PushCredentials) {
    try {
      persistCredentials(window.localStorage, credentials);
      setStorageWarning(null);
    } catch {
      setStorageWarning(
        "Browser storage is unavailable; credentials will not persist.",
      );
    }
  }

  function invalidateRequest() {
    setPrepared(null);
    setFeedback(null);
  }

  function changePayload(value: string) {
    setPayload(value);
    invalidateRequest();
  }

  async function createKeys() {
    if (generatingKeys || busy) return;
    const credentials = form.state.values.credentials;
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
      const nextCredentials = {
        ...form.state.values.credentials,
        vapidPublicKey: keys.publicKey,
        vapidPrivateKey: keys.privateKey,
      };
      form.setFieldValue("credentials", nextCredentials);
      persistEditedCredentials(nextCredentials);
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

  async function createRequest(
    values: DebugFormValues,
  ): Promise<PreparedPush | null> {
    try {
      const request = await prepareWebPush(
        values.credentials,
        validateRemotePayload(payload, "unifiedpush", messageKind),
        values.ttl.trim() ? Number(values.ttl) : Number.NaN,
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
      const request = await createRequest(form.state.values);
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

  async function handleSend(values: DebugFormValues) {
    if (busy || generatingKeys) return;
    setBusy(true);
    setFeedback(null);
    setPrepared(null);

    try {
      if (values.mode === "encrypted") {
        const request = await createRequest(values);
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
        const endpoint = parsePushEndpoint(values.credentials.endpoint);
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
    form.setFieldValue("credentials", { ...EMPTY_CREDENTIALS });
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

      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
      >
        <form.Subscribe selector={(state) => state.values.mode}>
          {(mode) => (
            <fieldset
              disabled={!loaded || busy || generatingKeys}
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
                      <form.Field name="mode">
                        {(field) => (
                          <Select
                            value={field.state.value}
                            onValueChange={(value) => {
                              field.handleChange(value as DeliveryMode);
                              invalidateRequest();
                            }}
                          >
                            <SelectTrigger id="push-mode" className="w-full">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="encrypted">
                                Encrypted Web Push (recommended)
                              </SelectItem>
                              <SelectItem value="plaintext">
                                Plaintext (ntfy-compatible only)
                              </SelectItem>
                            </SelectContent>
                          </Select>
                        )}
                      </form.Field>
                    </div>
                    {mode === "plaintext" && (
                      <Callout title="Unencrypted delivery" type="warning">
                        This mode is not encrypted. Enable{" "}
                        <strong>Process Unencrypted Messages</strong> in the
                        app. The browser cannot read the server response in
                        no-CORS mode.
                      </Callout>
                    )}
                    {fields
                      .filter(
                        (field) => mode === "encrypted" || !field.encryptedOnly,
                      )
                      .map((entry) => (
                        <form.Field
                          key={entry.key}
                          name={`credentials.${entry.key}`}
                        >
                          {(field) => (
                            <div className="space-y-2">
                              <Label htmlFor={`push-${entry.key}`}>
                                {entry.label}
                              </Label>
                              <Input
                                id={`push-${entry.key}`}
                                name={field.name}
                                type={
                                  entry.secret && !showSecrets
                                    ? "password"
                                    : "text"
                                }
                                value={field.state.value}
                                placeholder={entry.placeholder}
                                autoComplete="off"
                                spellCheck={false}
                                onBlur={field.handleBlur}
                                onChange={(event) => {
                                  const next = event.target.value;
                                  field.handleChange(next);
                                  persistEditedCredentials({
                                    ...form.state.values.credentials,
                                    [entry.key]: next,
                                  });
                                  invalidateRequest();
                                }}
                              />
                              <p className="text-xs text-muted-foreground">
                                {entry.hint}
                              </p>
                            </div>
                          )}
                        </form.Field>
                      ))}
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        className="w-full sm:w-48 shrink-0"
                        onClick={() => setShowSecrets((value) => !value)}
                      >
                        {showSecrets
                          ? "Hide credentials"
                          : "Reveal credentials"}
                      </Button>
                      {mode === "encrypted" && (
                        <Button
                          type="button"
                          variant="outline"
                          className="w-full sm:w-48 shrink-0"
                          onClick={createKeys}
                        >
                          {generatingKeys
                            ? "Generating…"
                            : "Generate VAPID keys"}
                        </Button>
                      )}
                      <Button
                        type="button"
                        variant="destructive"
                        className="w-full sm:w-48 shrink-0"
                        onClick={clearCredentials}
                      >
                        Clear credentials
                      </Button>
                    </div>
                    {mode === "encrypted" && (
                      <p className="text-xs text-muted-foreground">
                        New VAPID keys require updating the public key in the
                        app and re-registering before sending a push
                        notification.
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
                      <form.Field
                        name="ttl"
                        validators={{
                          onBlur: ({ value }) => {
                            const seconds = Number(value);
                            return value.trim()
                              && Number.isInteger(seconds)
                              && seconds >= 0
                              && seconds <= MAX_PUSH_TTL
                              ? undefined
                              : `TTL must be a whole number between 0 and ${MAX_PUSH_TTL} seconds.`;
                          },
                        }}
                      >
                        {(field) => (
                          <div className="space-y-2">
                            <Label htmlFor="push-ttl">
                              Time to live (seconds)
                            </Label>
                            <Input
                              id="push-ttl"
                              name={field.name}
                              type="number"
                              min={0}
                              max={MAX_PUSH_TTL}
                              step={1}
                              value={field.state.value}
                              aria-invalid={!field.state.meta.isValid}
                              onBlur={field.handleBlur}
                              onChange={(event) => {
                                field.handleChange(event.target.value);
                                invalidateRequest();
                              }}
                            />
                            {!field.state.meta.isValid && (
                              <p
                                role="alert"
                                className="text-xs text-destructive"
                              >
                                {field.state.meta.errors.join(", ")}
                              </p>
                            )}
                          </div>
                        )}
                      </form.Field>
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
                        <h2 className="font-semibold">
                          Encrypted cURL fallback
                        </h2>
                        <p className="text-muted-foreground">
                          Download the encrypted binary, place it beside your
                          terminal and run the copied command. The command
                          contains a short-lived VAPID token. Do not share it or
                          the endpoint URL.
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
          )}
        </form.Subscribe>
      </form>
    </div>
  );
}
