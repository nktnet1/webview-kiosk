import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { PlugZap, Send, Trash2, WifiOff } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
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
import { Checkbox } from "#/components/ui/checkbox";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "#/components/ui/select";
import { Textarea } from "#/components/ui/textarea";
import { type LogEntry, MqttMessageLog } from "./MqttMessageLog";
import { type MqttConnectionStatus, MqttWebSocketClient } from "./protocol";
import { RequestEditor } from "./RequestEditor";
import { createRequestPayload, parseRequestPayload } from "./request";
import {
  loadMqttCredentials,
  loadMqttPreferences,
  MQTT_CREDENTIALS_KEY,
  MQTT_PREFERENCES_KEY,
  saveMqttCredentials,
  saveMqttPreferences,
} from "./storage";
import {
  DEFAULT_PREFERENCES,
  EMPTY_MQTT_CREDENTIALS,
  type MqttCredentials,
  type MqttPreferences,
  type MqttQos,
  normalizeMqttCredentials,
  validateBrokerDomain,
  validateBrokerPath,
  validateBrokerPort,
  validateBrokerUrl,
  validateKeepAlive,
  validateTopic,
} from "./validation";

type BrokerForm = MqttCredentials & { keepAlive: string };
type Mode = "control" | "request" | "custom";
function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function QosSelect({
  value,
  onChange,
  id,
}: {
  value: MqttQos;
  onChange: (value: MqttQos) => void;
  id: string;
}) {
  return (
    <Select
      value={String(value)}
      onValueChange={(next) => onChange(Number(next) as MqttQos)}
    >
      <SelectTrigger id={id} className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="0">QoS 0</SelectItem>
        <SelectItem value="1">QoS 1</SelectItem>
        <SelectItem value="2">QoS 2</SelectItem>
      </SelectContent>
    </Select>
  );
}

export default function MqttDebugger() {
  const [loaded, setLoaded] = useState(false);
  const [storageWarning, setStorageWarning] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [status, setStatus] = useState<MqttConnectionStatus>("disconnected");
  const [error, setError] = useState<string | null>(null);
  const [preferences, setPreferences] =
    useState<MqttPreferences>(DEFAULT_PREFERENCES);
  const preferencesRef = useRef<MqttPreferences>(DEFAULT_PREFERENCES);
  const [activeTopics, setActiveTopics] = useState<string[]>([]);
  const [subscribeTopic, setSubscribeTopic] = useState("");
  const [subscribeQos, setSubscribeQos] = useState<MqttQos>(0);
  const [subscriptionBusy, setSubscriptionBusy] = useState(false);
  const [publishBusy, setPublishBusy] = useState(false);
  const [mode, setMode] = useState<Mode>("control");
  const [kind, setKind] = useState<MessageKind>("command");
  const [controlPayload, setControlPayload] = useState(() =>
    stringifyPayload(createRemotePayload("mqtt", "command")),
  );
  const [requestPayload, setRequestPayload] = useState(() =>
    stringifyPayload(createRequestPayload()),
  );
  const [customPayload, setCustomPayload] = useState(
    '{\n  "message": "Hello"\n}',
  );
  const [responseTopic, setResponseTopic] = useState("");
  const [correlationData, setCorrelationData] = useState("");
  const [publishQos, setPublishQos] = useState<MqttQos>(0);
  const [retain, setRetain] = useState(false);
  const [log, setLog] = useState<LogEntry[]>([]);
  const clientRef = useRef<MqttWebSocketClient | null>(null);
  const logSequence = useRef(0);

  const addLog = useCallback((entry: Omit<LogEntry, "id" | "time">) => {
    setLog((items) =>
      [
        {
          ...entry,
          id: ++logSequence.current,
          time: new Date().toLocaleTimeString(),
        },
        ...items,
      ].slice(0, 100),
    );
  }, []);

  const form = useForm({
    defaultValues: { ...EMPTY_MQTT_CREDENTIALS, keepAlive: "60" } as BrokerForm,
    onSubmit: async ({ value }) => connect(value),
  });

  useEffect(() => {
    try {
      const credentials = loadMqttCredentials(
        window.localStorage.getItem(MQTT_CREDENTIALS_KEY),
      );
      for (const key of Object.keys(
        EMPTY_MQTT_CREDENTIALS,
      ) as (keyof MqttCredentials)[]) {
        form.setFieldValue(key, credentials[key]);
      }
      const next = loadMqttPreferences(
        window.localStorage.getItem(MQTT_PREFERENCES_KEY),
      );
      preferencesRef.current = next;
      setPreferences(next);
    } catch {
      setStorageWarning(true);
    }
    setLoaded(true);
    return () => {
      clientRef.current?.disconnect();
      clientRef.current = null;
    };
  }, [form]);

  function currentCredentials(): MqttCredentials {
    const { brokerUrl, protocol, port, path, clientId, username, password } =
      form.state.values;
    return { brokerUrl, protocol, port, path, clientId, username, password };
  }

  function persistCredentials(next: MqttCredentials) {
    try {
      saveMqttCredentials(window.localStorage, next);
      setStorageWarning(false);
    } catch {
      setStorageWarning(true);
    }
  }

  function editCredential(key: keyof MqttCredentials, value: string) {
    switch (key) {
      case "brokerUrl":
        form.setFieldValue("brokerUrl", value);
        break;
      case "protocol":
        if (value !== "ws" && value !== "wss") return;
        form.setFieldValue("protocol", value);
        break;
      case "port":
        form.setFieldValue("port", value);
        break;
      case "path":
        form.setFieldValue("path", value);
        break;
      case "clientId":
        form.setFieldValue("clientId", value);
        break;
      case "username":
        form.setFieldValue("username", value);
        break;
      case "password":
        form.setFieldValue("password", value);
        break;
    }
    persistCredentials({ ...currentCredentials(), [key]: value });
    setError(null);
  }

  function updatePreferences(next: MqttPreferences) {
    preferencesRef.current = next;
    setPreferences(next);
    try {
      saveMqttPreferences(window.localStorage, next);
      setStorageWarning(false);
    } catch {
      setStorageWarning(true);
    }
  }

  function disconnect() {
    clientRef.current?.disconnect();
    clientRef.current = null;
    setStatus("disconnected");
    setActiveTopics([]);
  }

  function importCredentials(imported: MqttCredentials) {
    const next = normalizeMqttCredentials(imported);
    disconnect();
    form.reset({ ...next, keepAlive: form.state.values.keepAlive });
    persistCredentials(next);
    setPasswordVisible(false);
    setError(null);
    toast.success("MQTT credentials imported");
  }

  function clearCredentials() {
    disconnect();
    form.reset();
    persistCredentials({ ...EMPTY_MQTT_CREDENTIALS });
    setPasswordVisible(false);
    setError(null);
    toast.success("MQTT credentials cleared");
  }

  async function connect(values: BrokerForm) {
    if (clientRef.current) return;
    try {
      const brokerUrl = validateBrokerUrl(values.brokerUrl, values);
      if (!values.keepAlive.trim())
        throw new Error("Enter a keep-alive interval.");
      const keepAlive = validateKeepAlive(Number(values.keepAlive));
      if (values.password && !values.username)
        throw new Error("Enter a username when using a password.");
      const client = new MqttWebSocketClient(
        {
          brokerUrl,
          username: values.username,
          password: values.password,
          clientId:
            values.clientId || `wk-docs-${crypto.randomUUID().slice(0, 12)}`,
          keepAlive,
        },
        (next, detail) => {
          if (clientRef.current !== client) return;
          setStatus(next);
          if (next === "disconnected") {
            clientRef.current = null;
            setActiveTopics([]);
          }
          if (detail) setError(detail);
        },
        (received) => addLog({ direction: "received", ...received }),
      );
      clientRef.current = client;
      setStatus("connecting");
      setError(null);
      await client.connect();
      toast.success("Connected to MQTT broker");
      for (const sub of preferencesRef.current.subscriptions) {
        if (clientRef.current !== client) break;
        try {
          await client.subscribe(sub.topic, sub.qos);
          if (clientRef.current === client)
            setActiveTopics((topics) => [...new Set([...topics, sub.topic])]);
        } catch (cause) {
          toast.error(`Subscribe ${sub.topic}: ${message(cause)}`);
        }
      }
    } catch (cause) {
      setError(message(cause));
      toast.error(message(cause));
      if (clientRef.current && status !== "connected") disconnect();
    }
  }

  async function subscribe() {
    if (!clientRef.current || status !== "connected") return;
    setSubscriptionBusy(true);
    try {
      const topic = validateTopic(subscribeTopic.trim(), true);
      if (preferences.subscriptions.some((item) => item.topic === topic))
        throw new Error("Topic filter is already saved.");
      if (preferences.subscriptions.length >= 30)
        throw new Error("Maximum 30 subscriptions.");
      await clientRef.current.subscribe(topic, subscribeQos);
      updatePreferences({
        ...preferences,
        subscriptions: [
          ...preferences.subscriptions,
          { topic, qos: subscribeQos },
        ],
      });
      setActiveTopics((topics) => [...new Set([...topics, topic])]);
      setSubscribeTopic("");
      toast.success(`Subscribed to ${topic}`);
    } catch (cause) {
      toast.error(message(cause));
    } finally {
      setSubscriptionBusy(false);
    }
  }

  async function unsubscribe(topic: string) {
    setSubscriptionBusy(true);
    try {
      if (
        clientRef.current
        && status === "connected"
        && activeTopics.includes(topic)
      ) {
        await clientRef.current.unsubscribe(topic);
      }
      updatePreferences({
        ...preferences,
        subscriptions: preferences.subscriptions.filter(
          (item) => item.topic !== topic,
        ),
      });
      setActiveTopics((topics) => topics.filter((item) => item !== topic));
      toast.success(`Removed ${topic}`);
    } catch (cause) {
      toast.error(message(cause));
    } finally {
      setSubscriptionBusy(false);
    }
  }

  const topicField =
    mode === "request"
      ? "requestTopic"
      : mode === "custom"
        ? "customTopic"
        : kind === "command"
          ? "commandTopic"
          : "settingsTopic";
  const publishTopic = preferences[topicField];

  async function publish() {
    if (!clientRef.current || status !== "connected") return;
    setPublishBusy(true);
    setError(null);
    try {
      const topic = validateTopic(publishTopic.trim(), false);
      const payload =
        mode === "control"
          ? validateRemotePayload(controlPayload, "mqtt", kind)
          : mode === "request"
            ? JSON.stringify(parseRequestPayload(requestPayload))
            : customPayload;
      await clientRef.current.publish(topic, payload, {
        qos: publishQos,
        retain,
        ...(mode === "request"
          ? { responseTopic: responseTopic.trim(), correlationData }
          : {}),
      });
      addLog({
        topic,
        payload,
        qos: publishQos,
        retain,
        direction: "published",
      });
      toast.success(
        publishQos === 0
          ? "Published (QoS 0, no broker acknowledgement)"
          : `Published (QoS ${publishQos} acknowledged)`,
      );
    } catch (cause) {
      setError(message(cause));
      toast.error(message(cause));
    } finally {
      setPublishBusy(false);
    }
  }

  return (
    <div className="w-full max-w-6xl space-y-6 pb-12">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">MQTT Debugger</h1>
        <p className="text-sm text-muted-foreground">
          Connect, publish and inspect MQTT 5 messages over WebSockets.
        </p>
      </div>
      <Callout title="Credentials" type="warning">
        Credentials are stored unencrypted in this browser. Avoid shared
        devices.
      </Callout>
      {storageWarning && (
        <p role="alert" className="text-sm text-destructive">
          Browser storage is unavailable; settings will not persist.
        </p>
      )}
      {error && (
        <p
          role="alert"
          className="rounded-md border border-destructive px-3 py-2 text-sm text-destructive"
        >
          {error}
        </p>
      )}
      <section aria-label="Broker connection">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between gap-2">
              Connection
              <span
                role="status"
                className="flex items-center gap-1.5 text-xs font-normal text-muted-foreground"
              >
                <span
                  className={`size-2 rounded-full ${status === "connected" ? "bg-green-500" : status === "connecting" ? "bg-yellow-500" : "bg-muted-foreground"}`}
                />
                {status === "connected"
                  ? "Connected"
                  : status === "connecting"
                    ? "Connecting…"
                    : "Disconnected"}
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <form
              onSubmit={(event) => {
                event.preventDefault();
                event.stopPropagation();
                void form.handleSubmit();
              }}
              className="space-y-4"
            >
              <fieldset
                disabled={!loaded || status !== "disconnected"}
                className="space-y-4 disabled:opacity-70"
              >
                <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
                  <form.Field
                    name="brokerUrl"
                    validators={{
                      onBlur: ({ value }) => {
                        try {
                          validateBrokerDomain(value);
                        } catch (cause) {
                          return message(cause);
                        }
                      },
                    }}
                  >
                    {(field) => (
                      <div className="space-y-2">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="mqtt-broker-url">Broker domain</Label>
                          <InfoTooltip>
                            Hostname or IP address only. Configure the protocol,
                            port and path separately.
                          </InfoTooltip>
                        </div>
                        <Input
                          id="mqtt-broker-url"
                          name={field.name}
                          type="text"
                          required
                          spellCheck={false}
                          placeholder="broker.example.com"
                          aria-invalid={!field.state.meta.isValid}
                          value={field.state.value}
                          onBlur={field.handleBlur}
                          onChange={(event) =>
                            editCredential("brokerUrl", event.target.value)
                          }
                        />
                        {!field.state.meta.isValid && (
                          <p role="alert" className="text-xs text-destructive">
                            {field.state.meta.errors.join(", ")}
                          </p>
                        )}
                      </div>
                    )}
                  </form.Field>
                  <div className="grid gap-4 sm:grid-cols-3">
                    <form.Field name="protocol">
                      {(field) => (
                        <div className="space-y-2">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="mqtt-protocol">Protocol</Label>
                            <InfoTooltip>
                              WSS uses TLS; WS is available on HTTP pages.
                            </InfoTooltip>
                          </div>
                          <Select
                            required
                            value={field.state.value}
                            onValueChange={(value) =>
                              editCredential("protocol", value)
                            }
                          >
                            <SelectTrigger
                              id="mqtt-protocol"
                              className="w-full"
                            >
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="wss">Secure (WSS)</SelectItem>
                              <SelectItem value="ws">Insecure (WS)</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                      )}
                    </form.Field>
                    <form.Field
                      name="port"
                      validators={{
                        onBlur: ({ value }) => {
                          try {
                            validateBrokerPort(value);
                          } catch (cause) {
                            return message(cause);
                          }
                        },
                      }}
                    >
                      {(field) => (
                        <div className="space-y-2">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="mqtt-port">Port</Label>
                            <InfoTooltip>WebSocket port (1–65535).</InfoTooltip>
                          </div>
                          <Input
                            id="mqtt-port"
                            name={field.name}
                            type="text"
                            inputMode="numeric"
                            required
                            aria-invalid={!field.state.meta.isValid}
                            value={field.state.value}
                            onBlur={field.handleBlur}
                            onChange={(event) =>
                              editCredential("port", event.target.value)
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
                    <form.Field
                      name="path"
                      validators={{
                        onBlur: ({ value }) => {
                          try {
                            validateBrokerPath(value);
                          } catch (cause) {
                            return message(cause);
                          }
                        },
                      }}
                    >
                      {(field) => (
                        <div className="space-y-2">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="mqtt-path">Path</Label>
                            <InfoTooltip>
                              WebSocket endpoint path, beginning with /.
                            </InfoTooltip>
                          </div>
                          <Input
                            id="mqtt-path"
                            name={field.name}
                            spellCheck={false}
                            required
                            aria-invalid={!field.state.meta.isValid}
                            value={field.state.value}
                            onBlur={field.handleBlur}
                            onChange={(event) =>
                              editCredential("path", event.target.value)
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
                  </div>
                </div>
                <form.Subscribe
                  selector={(state) =>
                    [
                      state.values.brokerUrl,
                      state.values.protocol,
                      state.values.port,
                      state.values.path,
                    ] as const
                  }
                >
                  {([brokerUrl, protocol, port, path]) => {
                    let resolved: string;
                    try {
                      resolved = validateBrokerUrl(brokerUrl, {
                        protocol,
                        port,
                        path,
                      });
                    } catch {
                      return null;
                    }
                    return (
                      <p className="break-all text-xs text-muted-foreground">
                        <span className="sr-only">
                          Resolved WebSocket URL:{" "}
                        </span>
                        <code>{resolved}</code>
                      </p>
                    );
                  }}
                </form.Subscribe>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <form.Field name="username">
                    {(field) => (
                      <div className="space-y-2">
                        <Label htmlFor="mqtt-user">Username</Label>
                        <Input
                          id="mqtt-user"
                          name={field.name}
                          autoComplete="off"
                          value={field.state.value}
                          onBlur={field.handleBlur}
                          onChange={(event) =>
                            editCredential("username", event.target.value)
                          }
                        />
                      </div>
                    )}
                  </form.Field>
                  <form.Field name="clientId">
                    {(field) => (
                      <div className="space-y-2">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="mqtt-client-id">Client ID</Label>
                          <InfoTooltip>
                            Leave blank for a temporary ID. Never reuse your
                            kiosk's own client ID.
                          </InfoTooltip>
                        </div>
                        <Input
                          id="mqtt-client-id"
                          name={field.name}
                          placeholder="Auto-generated"
                          autoComplete="off"
                          value={field.state.value}
                          onBlur={field.handleBlur}
                          onChange={(event) =>
                            editCredential("clientId", event.target.value)
                          }
                        />
                      </div>
                    )}
                  </form.Field>
                  <form.Field name="password">
                    {(field) => (
                      <div className="space-y-2">
                        <Label htmlFor="mqtt-password">Password</Label>
                        <SecretInput
                          id="mqtt-password"
                          name={field.name}
                          value={field.state.value}
                          autoComplete="off"
                          revealed={passwordVisible}
                          onRevealChange={setPasswordVisible}
                          revealLabel="MQTT password"
                          onBlur={field.handleBlur}
                          onChange={(event) =>
                            editCredential("password", event.target.value)
                          }
                        />
                      </div>
                    )}
                  </form.Field>
                </div>
                <details className="rounded-md border p-3">
                  <summary className="cursor-pointer text-sm font-medium">
                    Connection options
                  </summary>
                  <div className="pt-3">
                    <form.Field
                      name="keepAlive"
                      validators={{
                        onBlur: ({ value }) => {
                          try {
                            validateKeepAlive(Number(value));
                          } catch (cause) {
                            return message(cause);
                          }
                        },
                      }}
                    >
                      {(field) => (
                        <div className="space-y-2">
                          <Label htmlFor="mqtt-keepalive">
                            Keep alive (seconds)
                          </Label>
                          <Input
                            id="mqtt-keepalive"
                            type="number"
                            min={0}
                            max={65535}
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
                  </div>
                </details>
              </fieldset>
              <div className="flex flex-wrap justify-end gap-2 border-t pt-4">
                <Button
                  type="submit"
                  className="w-full sm:w-36"
                  disabled={!loaded || status !== "disconnected"}
                >
                  <PlugZap aria-hidden="true" />
                  {status === "connecting" ? "Connecting…" : "Connect"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:w-36"
                  disabled={status === "disconnected"}
                  onClick={disconnect}
                >
                  <WifiOff aria-hidden="true" />
                  Disconnect
                </Button>
              </div>
            </form>
            <details className="rounded-md border p-3">
              <summary className="cursor-pointer text-sm font-medium">
                Manage credentials
              </summary>
              <div className="space-y-3 pt-3">
                <CredentialJsonTransfer
                  filename="mqtt-credentials.json"
                  template={EMPTY_MQTT_CREDENTIALS}
                  getCredentials={currentCredentials}
                  onImport={importCredentials}
                  warning="Backups include the broker password and connection options. Importing replaces all saved broker fields."
                />
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full"
                      disabled={!loaded}
                    >
                      <Trash2 aria-hidden="true" />
                      Clear credentials
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>
                        Clear MQTT credentials?
                      </AlertDialogTitle>
                      <AlertDialogDescription>
                        This removes the saved broker address, connection
                        options, username, password and client ID. A backup is
                        required to restore them.
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
            </details>
          </CardContent>
        </Card>
      </section>
      <section aria-label="Publish messages">
        <Card>
          <CardHeader>
            <CardTitle>Publish</CardTitle>
          </CardHeader>
          <CardContent className="mx-auto w-full max-w-4xl space-y-5">
            <div className="space-y-2">
              <Label htmlFor="mqtt-publish-mode">Message</Label>
              <Select
                value={mode}
                onValueChange={(value) => setMode(value as Mode)}
              >
                <SelectTrigger id="mqtt-publish-mode" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="control">Command / settings</SelectItem>
                  <SelectItem value="request">Request</SelectItem>
                  <SelectItem value="custom">Custom payload</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {mode === "control" ? (
              <RemoteMessageEditor
                transport="mqtt"
                kind={kind}
                value={controlPayload}
                onChange={setControlPayload}
                onKindChange={setKind}
              />
            ) : mode === "request" ? (
              <RequestEditor
                value={requestPayload}
                onChange={setRequestPayload}
                responseTopic={responseTopic}
                onResponseTopicChange={setResponseTopic}
                correlationData={correlationData}
                onCorrelationDataChange={setCorrelationData}
              />
            ) : (
              <div className="space-y-2">
                <Label htmlFor="mqtt-custom-message">Payload (UTF-8)</Label>
                <Textarea
                  id="mqtt-custom-message"
                  className="min-h-56 font-mono text-sm"
                  rows={10}
                  value={customPayload}
                  spellCheck={false}
                  onChange={(event) => setCustomPayload(event.target.value)}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="mqtt-publish-topic">Publish topic</Label>
              <Input
                id="mqtt-publish-topic"
                spellCheck={false}
                value={publishTopic}
                onChange={(event) =>
                  updatePreferences({
                    ...preferences,
                    [topicField]: event.target.value,
                  })
                }
              />
            </div>
            <div className="grid grid-cols-2 items-end gap-4">
              <div className="space-y-2">
                <Label htmlFor="mqtt-publish-qos">Quality of service</Label>
                <QosSelect
                  id="mqtt-publish-qos"
                  value={publishQos}
                  onChange={setPublishQos}
                />
              </div>
              <div className="flex h-9 items-center gap-2">
                <Checkbox
                  id="mqtt-retain"
                  checked={retain}
                  onCheckedChange={(value) => setRetain(value === true)}
                />
                <Label htmlFor="mqtt-retain">Retain</Label>
                <InfoTooltip>
                  Retained commands may run again when the kiosk reconnects.
                  Leave off unless intentional.
                </InfoTooltip>
              </div>
            </div>
            <div className="flex justify-end border-t pt-4">
              <Button
                type="button"
                className="w-full sm:w-48"
                onClick={() => void publish()}
                disabled={status !== "connected" || publishBusy}
              >
                <Send aria-hidden="true" />
                {publishBusy ? "Publishing…" : "Publish"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </section>
      <section aria-label="MQTT activity">
        <Card>
          <CardHeader>
            <CardTitle>Activity</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-3">
              <h3 className="text-sm font-semibold">
                Subscriptions ({preferences.subscriptions.length})
              </h3>
              <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_6rem_auto] sm:items-end">
                <div className="space-y-2">
                  <Label htmlFor="mqtt-subscription-topic">Topic filter</Label>
                  <Input
                    id="mqtt-subscription-topic"
                    placeholder="wk/event/#"
                    value={subscribeTopic}
                    spellCheck={false}
                    onChange={(event) => setSubscribeTopic(event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="mqtt-subscription-qos">QoS</Label>
                  <QosSelect
                    id="mqtt-subscription-qos"
                    value={subscribeQos}
                    onChange={setSubscribeQos}
                  />
                </div>
                <Button
                  type="button"
                  className="w-full sm:w-auto"
                  disabled={status !== "connected" || subscriptionBusy}
                  onClick={() => void subscribe()}
                >
                  Subscribe
                </Button>
              </div>
              <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                {preferences.subscriptions.map((sub) => (
                  <div
                    key={sub.topic}
                    className="flex min-w-0 items-center justify-between gap-3 rounded-md border px-3 py-2.5"
                  >
                    <div className="min-w-0">
                      <div className="break-all font-mono text-xs">
                        {sub.topic}
                      </div>
                      <span className="text-xs text-muted-foreground">
                        QoS {sub.qos}
                        {activeTopics.includes(sub.topic) ? " · active" : ""}
                      </span>
                    </div>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      aria-label={`Unsubscribe ${sub.topic}`}
                      disabled={subscriptionBusy}
                      onClick={() => void unsubscribe(sub.topic)}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </div>
                ))}
                {preferences.subscriptions.length === 0 && (
                  <p className="py-3 text-sm text-muted-foreground">
                    No subscriptions.
                  </p>
                )}
              </div>
            </div>
            <div className="space-y-3 border-t pt-6">
              <div className="flex items-center justify-between gap-3">
                <h3 className="text-sm font-semibold">
                  Messages{" "}
                  <span className="font-normal text-muted-foreground">
                    ({log.length}/100)
                  </span>
                </h3>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={log.length === 0}
                  onClick={() => setLog([])}
                >
                  <Trash2 aria-hidden="true" />
                  Clear
                </Button>
              </div>
              <MqttMessageLog entries={log} />
            </div>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
