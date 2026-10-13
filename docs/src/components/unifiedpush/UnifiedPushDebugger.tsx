"use client";

import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CredentialJsonTransfer } from "#/components/remote-control/CredentialJsonTransfer";
import {
  createRemotePayload,
  type MessageKind,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "#/components/ui/alert-dialog";
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
  generateVapidKeys,
  MAX_PUSH_PAYLOAD_BYTES,
  MAX_PUSH_TTL,
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

  async function handleSend(values: DebugFormValues) {
    if (busy || generatingKeys) return;
    setBusy(true);
    setFeedback(null);

    try {
      if (values.mode === "encrypted") {
        const request = await prepareWebPush(
          values.credentials,
          validateRemotePayload(payload, "unifiedpush", messageKind),
          values.ttl.trim() ? Number(values.ttl) : Number.NaN,
        );

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
              "The browser could not complete the request (often because the push server blocks CORS). No delivery is confirmed. Use an endpoint that allows browser-origin requests.",
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
                message: `Push server rejected the request (HTTP ${response.status}). Check the endpoint and keys.`,
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
    setShowSecrets(false);
    invalidateRequest();
    try {
      window.localStorage.removeItem(STORAGE_KEY);
      setStorageWarning(null);
      toast.success("Saved credentials cleared");
    } catch {
      setStorageWarning(
        "Browser storage is unavailable; credentials may still be saved.",
      );
      toast.warning(
        "Fields cleared, but browser storage could not be accessed.",
      );
    }
  }

  function applyImportedCredentials(credentials: PushCredentials) {
    form.setFieldValue("credentials", credentials);
    persistEditedCredentials(credentials);
    setShowSecrets(false);
    invalidateRequest();
    toast.success("Credential fields imported");
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
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button
                            type="button"
                            variant="destructive"
                            className="w-full sm:w-48 shrink-0"
                          >
                            Clear credentials
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>
                              Clear saved credentials?
                            </AlertDialogTitle>
                            <AlertDialogDescription>
                              This permanently clears the push endpoint, auth
                              secret, and VAPID keys stored in this browser.
                              This cannot be undone unless you have an exported
                              backup. You may need to register the device again.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel asChild>
                              <Button type="button" variant="outline">
                                Cancel
                              </Button>
                            </AlertDialogCancel>
                            <AlertDialogAction asChild>
                              <Button
                                type="button"
                                variant="destructive"
                                onClick={clearCredentials}
                              >
                                Clear credentials
                              </Button>
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                    <CredentialJsonTransfer
                      filename="unifiedpush-credentials.json"
                      template={EMPTY_CREDENTIALS}
                      getCredentials={() => form.state.values.credentials}
                      onImport={applyImportedCredentials}
                      warning="JSON exports include your private VAPID key, auth secret, and endpoint. Store them securely. Importing replaces all six credential fields; missing fields are cleared."
                    />
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
