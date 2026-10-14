import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { ChevronDown, Eye, EyeOff, KeyRound, Send, Trash2 } from "lucide-react";
import { type ChangeEvent, useEffect, useRef, useState } from "react";
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
  appendPushDelivery,
  finishPushDelivery,
  type PushDeliveryEntry,
  type PushDeliveryOutcome,
} from "./delivery-log";
import {
  DEFAULT_VAPID_CONTACT,
  EMPTY_CREDENTIALS,
  initialPushCredentials,
  persistCredentials,
  STORAGE_KEY,
} from "./storage";
import { UnifiedPushDeliveryLog } from "./UnifiedPushDeliveryLog";
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
  const [connectionOpen, setConnectionOpen] = useState(true);
  const [log, setLog] = useState<PushDeliveryEntry[]>([]);
  const logSequence = useRef(0);

  // Keep fields, validation and submit values in TanStack Form. Only the
  // credentials (not TTL or delivery mode) are saved to browser storage.
  const form = useForm({
    defaultValues: {
      credentials: {
        ...EMPTY_CREDENTIALS,
        vapidSubject: DEFAULT_VAPID_CONTACT,
      },
      mode: "encrypted",
      ttl: "60",
    } as DebugFormValues,
    onSubmit: async ({ value }) => handleSend(value),
  });

  useEffect(() => {
    try {
      form.setFieldValue(
        "credentials",
        initialPushCredentials(window.localStorage.getItem(STORAGE_KEY)),
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

  function changePayload(value: string) {
    setPayload(value);
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
    const now = new Date();
    const attemptId = ++logSequence.current;
    setLog((entries) =>
      appendPushDelivery(entries, {
        id: attemptId,
        timestamp: now.toISOString(),
        time: now.toLocaleTimeString(),
        mode: values.mode,
        kind: messageKind,
        payload,
        outcome: "sending",
        detail: "Waiting for the push service response…",
      }),
    );

    function finish(
      outcome: PushDeliveryOutcome,
      detail: string,
      httpStatus?: number,
    ) {
      setLog((entries) =>
        finishPushDelivery(entries, attemptId, {
          outcome,
          detail,
          ...(httpStatus === undefined ? {} : { httpStatus }),
        }),
      );
    }

    try {
      const message = validateRemotePayload(
        payload,
        "unifiedpush",
        messageKind,
      );
      if (values.mode === "encrypted") {
        const request = await prepareWebPush(
          values.credentials,
          message,
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
          finish(
            "unconfirmed",
            "The browser could not complete the request (possibly due to CORS). Acceptance and delivery are unconfirmed.",
          );
          return;
        }
        finish(
          response.ok ? "accepted" : "rejected",
          response.ok
            ? `Push service accepted the request (HTTP ${response.status}). Device delivery is not confirmed.`
            : `Push service rejected the request (HTTP ${response.status}). Check the endpoint and keys.`,
          response.status,
        );
      } else {
        const endpoint = parsePushEndpoint(values.credentials.endpoint);
        if (new TextEncoder().encode(message).length > MAX_PUSH_PAYLOAD_BYTES) {
          throw new Error(
            `Payload must be at most ${MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes.`,
          );
        }
        // An opaque response does not prove that the request was accepted.
        const response = await fetch(endpoint, {
          method: "POST",
          mode: "no-cors",
          headers: { "Content-Type": "text/plain" },
          body: message,
          credentials: "omit",
          redirect: "error",
          signal: AbortSignal.timeout(15_000),
        });
        if (response.type === "opaque") {
          finish(
            "unconfirmed",
            "Browser sent a no-CORS request without a readable response. Acceptance and delivery cannot be verified.",
          );
        } else {
          finish(
            response.ok ? "accepted" : "rejected",
            `Push service returned HTTP ${response.status}. Device delivery is not confirmed.`,
            response.status,
          );
        }
      }
    } catch (error) {
      finish("failed", describeError(error));
    } finally {
      setBusy(false);
    }
  }

  function clearCredentials() {
    form.setFieldValue("credentials", { ...EMPTY_CREDENTIALS });
    setShowSecrets(false);
    setFieldVisibility({});
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
    toast.success("Credential fields imported");
  }

  return (
    <div className="min-w-0 w-full space-y-4">
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
      >
        <form.Subscribe selector={(state) => state.values.mode}>
          {(mode) => (
            <>
              <section aria-label="UnifiedPush connection">
                <Card className="gap-0">
                  <CardHeader className="pb-4">
                    <button
                      type="button"
                      className="flex w-full items-center justify-between gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md"
                      aria-expanded={connectionOpen}
                      aria-controls="push-connection-fields"
                      onClick={() => setConnectionOpen((open) => !open)}
                    >
                      <CardTitle>UnifiedPush Connection</CardTitle>
                      <ChevronDown
                        aria-hidden="true"
                        className={`size-4 shrink-0 transition-transform ${connectionOpen ? "rotate-180" : ""}`}
                      />
                    </button>
                  </CardHeader>
                  {connectionOpen && (
                    <CardContent
                      id="push-connection-fields"
                      className="space-y-5"
                    >
                      <Callout title="Privacy warning" type="warning">
                        Credentials, including private keys, are stored
                        unencrypted in this browser. Avoid shared devices.
                      </Callout>
                      {storageWarning && (
                        <p role="alert" className="text-sm text-destructive">
                          {storageWarning}
                        </p>
                      )}
                      <fieldset
                        disabled={!loaded || busy || generatingKeys}
                        className="min-w-0 space-y-5 disabled:opacity-70"
                      >
                        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                          <div className="space-y-2">
                            <div className="flex items-center gap-1">
                              <Label htmlFor="push-mode">Delivery mode</Label>
                              <InfoTooltip>
                                Encrypted Web Push uses the endpoint keys and
                                VAPID. Plaintext requires an ntfy-compatible
                                distributor.
                              </InfoTooltip>
                            </div>
                            <form.Field name="mode">
                              {(field) => (
                                <Select
                                  value={field.state.value}
                                  onValueChange={(value) =>
                                    field.handleChange(value as DeliveryMode)
                                  }
                                >
                                  <SelectTrigger
                                    id="push-mode"
                                    className="w-full"
                                  >
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
                          {fields
                            .filter(
                              (entry) =>
                                mode === "encrypted" || !entry.encryptedOnly,
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
                                    },
                                  };
                                  return (
                                    <div className="min-w-0 space-y-2">
                                      <div className="flex items-center gap-1">
                                        <Label htmlFor={`push-${entry.key}`}>
                                          {entry.label}
                                        </Label>
                                        <InfoTooltip>{entry.hint}</InfoTooltip>
                                      </div>
                                      {entry.secret ? (
                                        <SecretInput
                                          {...inputProps}
                                          revealLabel={
                                            entry.revealLabel ?? entry.key
                                          }
                                          revealed={
                                            fieldVisibility[entry.key]
                                            ?? showSecrets
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
                        </div>
                        {mode === "plaintext" && (
                          <Callout title="Unencrypted delivery" type="warning">
                            Enable <strong>Process Unencrypted Messages</strong>{" "}
                            in the app. Delivery cannot be verified in no-CORS
                            mode.
                          </Callout>
                        )}
                        <div className="space-y-4 border-t pt-4">
                          <div className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
                            Credential actions
                            <InfoTooltip>
                              Generating new VAPID keys requires registering the
                              device again with the new public key.
                            </InfoTooltip>
                          </div>
                          <div className="flex flex-wrap gap-2">
                            <Button
                              type="button"
                              variant="outline"
                              className="min-w-36"
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
                                className="min-w-36"
                                aria-label="Generate VAPID keys"
                                onClick={requestKeyGeneration}
                              >
                                <KeyRound aria-hidden="true" />
                                {generatingKeys
                                  ? "Generating…"
                                  : "Generate keys"}
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
                                    The device must be re-registered with the
                                    new public key before it can receive pushes
                                    signed by the new private key.
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
                                  className="min-w-28"
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
                                    This permanently clears the push endpoint,
                                    auth secret, and VAPID keys stored in this
                                    browser. This cannot be undone unless you
                                    have an exported backup. You may need to
                                    register the device again.
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
                          <div className="max-w-xs">
                            <CredentialJsonTransfer
                              filename="unifiedpush-credentials.json"
                              template={EMPTY_CREDENTIALS}
                              getCredentials={() =>
                                form.state.values.credentials
                              }
                              onImport={applyImportedCredentials}
                              warning="Exported JSON contains your private VAPID key, auth secret and endpoint. Importing replaces all six credential fields, including missing values."
                            />
                          </div>
                        </div>
                      </fieldset>
                    </CardContent>
                  )}
                </Card>
              </section>
              {mode === "encrypted" && (
                <section aria-label="VAPID contact">
                  <Card className="gap-0">
                    <CardContent className="py-4">
                      <fieldset
                        disabled={!loaded || busy || generatingKeys}
                        className="disabled:opacity-70"
                      >
                        <form.Field name="credentials.vapidSubject">
                          {(field) => (
                            <div className="max-w-lg space-y-2">
                              <div className="flex items-center gap-1">
                                <Label htmlFor="push-vapidSubject">
                                  VAPID contact (optional)
                                </Label>
                                <InfoTooltip>
                                  Enter your email address or an HTTPS URL.
                                  Email addresses are sent with mailto:
                                  automatically. Replace the example contact
                                  before sending.
                                </InfoTooltip>
                              </div>
                              <Input
                                id="push-vapidSubject"
                                name={field.name}
                                type="text"
                                autoComplete="off"
                                spellCheck={false}
                                placeholder={DEFAULT_VAPID_CONTACT}
                                value={field.state.value}
                                onBlur={field.handleBlur}
                                onChange={(event) => {
                                  const next = event.target.value;
                                  field.handleChange(next);
                                  persistEditedCredentials({
                                    ...form.state.values.credentials,
                                    vapidSubject: next,
                                  });
                                }}
                              />
                            </div>
                          )}
                        </form.Field>
                      </fieldset>
                    </CardContent>
                  </Card>
                </section>
              )}
              <div className="grid min-w-0 items-stretch gap-4 lg:grid-cols-[minmax(21rem,32%)_minmax(0,1fr)]">
                <section
                  aria-label="Send UnifiedPush message"
                  className="min-w-0"
                >
                  <Card className="h-full min-w-0">
                    <CardHeader>
                      <CardTitle className="flex items-center gap-1">
                        Send
                        <InfoTooltip>
                          Maximum {MAX_PUSH_PAYLOAD_BYTES} UTF-8 bytes after
                          JSON minification.
                        </InfoTooltip>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-5">
                      <fieldset
                        disabled={!loaded || busy || generatingKeys}
                        className="min-w-0 space-y-5 disabled:opacity-70"
                      >
                        <RemoteMessageEditor
                          transport="unifiedpush"
                          kind={messageKind}
                          value={payload}
                          onChange={changePayload}
                          onKindChange={setMessageKind}
                        />
                        {mode === "encrypted" && (
                          <form.Field
                            name="ttl"
                            validators={{
                              onBlur: ({ value }) =>
                                isValidPushTtlInput(value)
                                  ? undefined
                                  : `TTL must be a whole number between 0 and ${MAX_PUSH_TTL} seconds.`,
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
                                  onChange={(event) =>
                                    field.handleChange(event.target.value)
                                  }
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
                        <div className="border-t pt-4">
                          <Button
                            type="submit"
                            className="w-full"
                            disabled={!loaded || busy || generatingKeys}
                          >
                            <Send aria-hidden="true" />
                            {busy ? "Sending…" : "Send test push"}
                          </Button>
                        </div>
                      </fieldset>
                    </CardContent>
                  </Card>
                </section>
                <section
                  aria-label="UnifiedPush delivery history"
                  className="min-w-0"
                >
                  <UnifiedPushDeliveryLog
                    entries={log}
                    onClear={() => setLog([])}
                  />
                </section>
              </div>
            </>
          )}
        </form.Subscribe>
      </form>
    </div>
  );
}
