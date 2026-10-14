import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { Eye, EyeOff, KeyRound, Send, Trash2 } from "lucide-react";
import { type ChangeEvent, useEffect, useState } from "react";
import { toast } from "sonner";
import { CredentialJsonTransfer } from "#/components/remote-control/CredentialJsonTransfer";
import { InfoTooltip } from "#/components/remote-control/InfoTooltip";
import {
  createRemotePayload,
  type MessageKind,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import { SecretInput } from "#/components/remote-control/SecretInput";
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
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card";
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
  isValidPushTtlInput,
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
  revealLabel?: string;
  encryptedOnly?: boolean;
}[] = [
  {
    key: "endpoint",
    label: "Push endpoint URL",
    placeholder: "https://ntfy.sh/your-endpoint?up=1",
    hint: "The capability URL supplied by the UnifiedPush distributor.",
    secret: true,
    revealLabel: "endpoint",
  },
  {
    key: "p256dh",
    label: "Endpoint public key (p256dh)",
    placeholder: "Base64url (87 characters)",
    hint: "The endpoint public key from the app, not the VAPID key.",
    secret: true,
    revealLabel: "p256dh key",
    encryptedOnly: true,
  },
  {
    key: "auth",
    label: "Endpoint auth secret",
    placeholder: "Base64url (22 characters)",
    hint: "The 16-byte encryption secret from the app.",
    secret: true,
    revealLabel: "auth secret",
    encryptedOnly: true,
  },
  {
    key: "vapidPublicKey",
    label: "VAPID public key",
    placeholder: "Base64url (87 characters)",
    hint: "Must be the same public key used when registering the device.",
    secret: true,
    revealLabel: "VAPID verification key",
    encryptedOnly: true,
  },
  {
    key: "vapidPrivateKey",
    label: "VAPID private key",
    placeholder: "Base64url (43 characters)",
    hint: "Keep private; only used for local signing.",
    secret: true,
    revealLabel: "VAPID signing key",
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
  const [fieldVisibility, setFieldVisibility] = useState<
    Partial<Record<keyof PushCredentials, boolean>>
  >({});
  const [confirmReplaceKeys, setConfirmReplaceKeys] = useState(false);
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

  function requestKeyGeneration() {
    if (generatingKeys || busy) return;
    const { vapidPublicKey, vapidPrivateKey } = form.state.values.credentials;
    if (vapidPublicKey || vapidPrivateKey) {
      setConfirmReplaceKeys(true);
      return;
    }
    void createKeys();
  }

  async function createKeys() {
    if (generatingKeys || busy) return;
    setConfirmReplaceKeys(false);
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
    setFieldVisibility({});
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
    setFieldVisibility({});
    invalidateRequest();
    toast.success("Credential fields imported");
  }

  return (
    <div className="bg-fd-muted rounded-2xl p-5 md:p-10 w-full max-w-6xl space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
          UnifiedPush Debugger
        </h1>
      </div>

      <Callout title="Privacy warning" type="warning">
        Credentials, including private keys, are stored unencrypted in this
        browser. Avoid shared devices.
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
                  </CardHeader>
                  <CardContent className="space-y-5">
                    <div className="space-y-2">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="push-mode">Delivery mode</Label>
                        <InfoTooltip>
                          Encrypted Web Push uses the endpoint keys and VAPID.
                          Plaintext requires an ntfy-compatible distributor.
                        </InfoTooltip>
                      </div>
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
                        Enable <strong>Process Unencrypted Messages</strong> in
                        the app. Delivery cannot be verified in no-CORS mode.
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
                          {(field) => {
                            const inputProps = {
                              id: `push-${entry.key}`,
                              name: field.name,
                              value: field.state.value,
                              placeholder: entry.placeholder,
                              autoComplete: "off" as const,
                              spellCheck: false,
                              onBlur: field.handleBlur,
                              onChange: (
                                event: ChangeEvent<HTMLInputElement>,
                              ) => {
                                const next = event.target.value;
                                field.handleChange(next);
                                persistEditedCredentials({
                                  ...form.state.values.credentials,
                                  [entry.key]: next,
                                });
                                invalidateRequest();
                              },
                            };

                            return (
                              <div className="space-y-2">
                                <div className="flex items-center gap-1">
                                  <Label htmlFor={`push-${entry.key}`}>
                                    {entry.label}
                                  </Label>
                                  <InfoTooltip>{entry.hint}</InfoTooltip>
                                </div>
                                {entry.secret ? (
                                  <SecretInput
                                    {...inputProps}
                                    revealLabel={entry.revealLabel ?? entry.key}
                                    revealed={
                                      fieldVisibility[entry.key] ?? showSecrets
                                    }
                                    onRevealChange={(revealed) =>
                                      setFieldVisibility((current) => ({
                                        ...current,
                                        [entry.key]: revealed,
                                      }))
                                    }
                                  />
                                ) : (
                                  <Input {...inputProps} type="text" />
                                )}
                              </div>
                            );
                          }}
                        </form.Field>
                      ))}
                    <div className="space-y-4 border-t pt-4">
                      <div className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
                        Credential actions
                        <InfoTooltip>
                          Generating new VAPID keys requires registering the
                          device again with the new public key.
                        </InfoTooltip>
                      </div>
                      <div
                        className={`grid gap-2 ${mode === "encrypted" ? "sm:grid-cols-3" : "sm:grid-cols-2"}`}
                      >
                        <Button
                          type="button"
                          variant="outline"
                          className="w-full"
                          aria-label={
                            showSecrets
                              ? "Hide credentials"
                              : "Reveal credentials"
                          }
                          onClick={() => {
                            setFieldVisibility({});
                            setShowSecrets((value) => !value);
                          }}
                        >
                          {showSecrets ? (
                            <EyeOff aria-hidden="true" />
                          ) : (
                            <Eye aria-hidden="true" />
                          )}
                          {showSecrets ? "Hide secrets" : "Show secrets"}
                        </Button>
                        {mode === "encrypted" && (
                          <Button
                            type="button"
                            variant="outline"
                            className="w-full"
                            aria-label="Generate VAPID keys"
                            onClick={requestKeyGeneration}
                          >
                            <KeyRound aria-hidden="true" />
                            {generatingKeys ? "Generating…" : "Generate keys"}
                          </Button>
                        )}
                        <AlertDialog
                          open={confirmReplaceKeys}
                          onOpenChange={setConfirmReplaceKeys}
                        >
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>
                                Replace existing VAPID keys?
                              </AlertDialogTitle>
                              <AlertDialogDescription>
                                The device must be re-registered with the new
                                public key before it can receive pushes signed
                                by the new private key.
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
                                  onClick={() => void createKeys()}
                                >
                                  Replace keys
                                </Button>
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <Button
                              type="button"
                              variant="destructive"
                              className="w-full"
                              aria-label="Clear credentials"
                            >
                              <Trash2 aria-hidden="true" /> Clear
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
                                This cannot be undone unless you have an
                                exported backup. You may need to register the
                                device again.
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
                        warning="Exported JSON contains your private VAPID key, auth secret and endpoint. Importing replaces all six credential fields, including missing values."
                      />
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-1">
                      Message
                      <InfoTooltip>
                        Maximum {MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes after JSON
                        minification.
                      </InfoTooltip>
                    </CardTitle>
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
                    {mode === "encrypted" && (
                      <form.Field
                        name="ttl"
                        validators={{
                          onBlur: ({ value }) => {
                            return isValidPushTtlInput(value)
                              ? undefined
                              : `TTL must be a whole number between 0 and ${MAX_PUSH_TTL} seconds.`;
                          },
                        }}
                      >
                        {(field) => (
                          <div className="space-y-2">
                            <div className="flex items-center gap-1">
                              <Label htmlFor="push-ttl">
                                Time to live (seconds)
                              </Label>
                              <InfoTooltip>
                                How long the push server may retain an
                                undelivered notification (0–{MAX_PUSH_TTL}{" "}
                                seconds).
                              </InfoTooltip>
                            </div>
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
                    <div className="flex justify-end border-t pt-4">
                      <Button
                        type="submit"
                        className="w-full sm:w-48"
                        disabled={!loaded || busy || generatingKeys}
                      >
                        <Send aria-hidden="true" />
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
