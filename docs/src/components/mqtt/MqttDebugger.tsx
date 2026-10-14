import { useForm } from "@tanstack/react-form";
import { Callout } from "fumadocs-ui/components/callout";
import { ChevronDown, KeyRound, PlugZap, Trash2, WifiOff } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { CredentialJsonTransfer } from "#/components/remote-control/CredentialJsonTransfer";
import { InfoTooltip } from "#/components/remote-control/InfoTooltip";
import {
  createRemotePayload,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
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
import { Card, CardContent } from "#/components/ui/card";
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
  appendMqttMessage,
  filterMqttMessages,
  type LogEntry,
  MqttMessageLog,
} from "./MqttMessageLog";
import { MqttPublishComposer, type PublishMode } from "./MqttPublishComposer";
import { MqttTopicSidebar } from "./MqttTopicSidebar";
import { mqttHex } from "./properties";
import { type MqttConnectionStatus, MqttWebSocketClient } from "./protocol";
import { createRequestPayload, parseRequestPayload } from "./request";
import {
  loadMqttCredentials,
  loadMqttPreferences,
  MQTT_CREDENTIALS_KEY,
  MQTT_PREFERENCES_KEY,
  saveMqttCredentials,
  saveMqttPreferences,
} from "./storage";
import { replaceMqttSubscription } from "./subscriptions";
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
function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

export default function MqttDebugger() {
  const [loaded, setLoaded] = useState(false);
  const [storageWarning, setStorageWarning] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [status, setStatus] = useState<MqttConnectionStatus>("disconnected");
  const [connectionOpen, setConnectionOpen] = useState(true);
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [preferences, setPreferences] =
    useState<MqttPreferences>(DEFAULT_PREFERENCES);
  const preferencesRef = useRef<MqttPreferences>(DEFAULT_PREFERENCES);
  const [activeTopics, setActiveTopics] = useState<string[]>([]);
  const [subscribeTopic, setSubscribeTopic] = useState("");
  const [subscribeQos, setSubscribeQos] = useState<MqttQos>(0);
  const [subscriptionBusy, setSubscriptionBusy] = useState(false);
  const [publishBusy, setPublishBusy] = useState(false);
  const [mode, setMode] = useState<PublishMode>("request");
  const [commandPayload, setCommandPayload] = useState(() =>
    stringifyPayload(createRemotePayload("mqtt", "command")),
  );
  const [settingsPayload, setSettingsPayload] = useState(() =>
    stringifyPayload(createRemotePayload("mqtt", "settings")),
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
  const publisherRef = useRef<{ username?: string; clientId: string } | null>(
    null,
  );
  const connectionToggleRef = useRef<HTMLButtonElement>(null);
  const logSequence = useRef(0);

  const addLog = useCallback((entry: Omit<LogEntry, "id" | "time">) => {
    setLog((items) =>
      appendMqttMessage(items, {
        ...entry,
        id: ++logSequence.current,
        time: new Date().toLocaleTimeString(),
      }),
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
    publisherRef.current = null;
    setStatus("disconnected");
    setActiveTopics([]);
    setConnectionOpen(true);
  }

  function importCredentials(imported: MqttCredentials) {
    const next = normalizeMqttCredentials(imported);
    disconnect();
    setSelectedTopic(null);
    form.reset({ ...next, keepAlive: form.state.values.keepAlive });
    persistCredentials(next);
    setPasswordVisible(false);
    setError(null);
    toast.success("MQTT credentials imported");
  }

  function clearCredentials() {
    disconnect();
    setSelectedTopic(null);
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
      const clientId =
        values.clientId || `wk-docs-${crypto.randomUUID().slice(0, 12)}`;
      publisherRef.current = {
        username: values.username || undefined,
        clientId,
      };
      const client = new MqttWebSocketClient(
        {
          brokerUrl,
          username: values.username,
          password: values.password,
          clientId,
          keepAlive,
        },
        (next, detail) => {
          if (clientRef.current !== client) return;
          setStatus(next);
          if (next === "disconnected") {
            clientRef.current = null;
            publisherRef.current = null;
            setActiveTopics([]);
            setConnectionOpen(true);
          }
          if (detail) setError(detail);
        },
        (received) => addLog({ direction: "received", ...received }),
      );
      clientRef.current = client;
      setStatus("connecting");
      setError(null);
      await client.connect();
      if (clientRef.current !== client) return;
      connectionToggleRef.current?.focus();
      setConnectionOpen(false);
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
      setSelectedTopic(topic);
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
      if (selectedTopic === topic) setSelectedTopic(null);
      setActiveTopics((topics) => topics.filter((item) => item !== topic));
      toast.success(`Removed ${topic}`);
    } catch (cause) {
      toast.error(message(cause));
    } finally {
      setSubscriptionBusy(false);
    }
  }

  async function editSubscription(
    previousTopic: string,
    replacement: MqttPreferences["subscriptions"][number],
  ): Promise<boolean> {
    if (subscriptionBusy) return false;
    setSubscriptionBusy(true);
    try {
      const client = status === "connected" ? clientRef.current : null;
      const nextSubscriptions = await replaceMqttSubscription(
        preferences.subscriptions,
        previousTopic,
        replacement,
        client,
        activeTopics.includes(previousTopic),
      );
      updatePreferences({ ...preferences, subscriptions: nextSubscriptions });
      const nextTopic = nextSubscriptions.find(
        (item) => item.topic === replacement.topic.trim(),
      )?.topic;
      if (nextTopic && selectedTopic === previousTopic)
        setSelectedTopic(nextTopic);
      setActiveTopics((topics) => {
        const remaining = topics.filter((topic) => topic !== previousTopic);
        return client && clientRef.current === client && nextTopic
          ? [...new Set([...remaining, nextTopic])]
          : remaining;
      });
      toast.success("Subscription updated");
      return true;
    } catch (cause) {
      toast.error(message(cause));
      return false;
    } finally {
      setSubscriptionBusy(false);
    }
  }

  const topicField =
    mode === "command"
      ? "commandTopic"
      : mode === "settings"
        ? "settingsTopic"
        : mode === "request"
          ? "requestTopic"
          : "customTopic";
  const publishTopic = preferences[topicField];

  async function publish() {
    if (!clientRef.current || status !== "connected") return;
    setPublishBusy(true);
    setError(null);
    try {
      const topic = validateTopic(publishTopic.trim(), false);
      const payload =
        mode === "command"
          ? validateRemotePayload(commandPayload, "mqtt", "command")
          : mode === "settings"
            ? validateRemotePayload(settingsPayload, "mqtt", "settings")
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
        sender: publisherRef.current ?? undefined,
        properties: {
          payloadFormatIndicator: 1,
          ...(mode === "request" && responseTopic.trim()
            ? { responseTopic: responseTopic.trim() }
            : {}),
          ...(mode === "request" && correlationData
            ? {
                correlationData: mqttHex(
                  new TextEncoder().encode(correlationData),
                ),
              }
            : {}),
        },
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
    <div className="min-w-0 w-full space-y-4">
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
        <Card className="gap-0 py-0">
          <div className="flex min-w-0 flex-wrap items-center gap-2 px-3 py-2 sm:px-5">
            <button
              ref={connectionToggleRef}
              type="button"
              aria-expanded={connectionOpen}
              aria-controls="mqtt-connection-fields"
              onClick={() => setConnectionOpen((open) => !open)}
              className="flex min-w-48 flex-1 items-center gap-3 rounded-md px-2 py-1.5 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <PlugZap
                aria-hidden="true"
                className="size-5 shrink-0 text-muted-foreground"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold">
                  MQTT Broker Connection
                </span>
                <form.Subscribe selector={(state) => state.values.brokerUrl}>
                  {(domain) => (
                    <span className="block truncate text-xs text-muted-foreground">
                      {domain.trim() || "Configure your MQTT broker"}
                    </span>
                  )}
                </form.Subscribe>
              </span>
              <span
                role="status"
                className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground"
              >
                <span
                  className={`size-2 rounded-full ${status === "connected" ? "bg-emerald-500" : status === "connecting" ? "bg-amber-500" : "bg-red-500"}`}
                />
                {status === "connected"
                  ? "Connected"
                  : status === "connecting"
                    ? "Connecting..."
                    : "Disconnected"}
              </span>
              <ChevronDown
                aria-hidden="true"
                className={`size-4 shrink-0 text-muted-foreground transition-transform ${connectionOpen ? "rotate-180" : ""}`}
              />
            </button>
            {status !== "disconnected" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={disconnect}
              >
                <WifiOff aria-hidden="true" />
                <span className="hidden sm:inline">Disconnect</span>
                <span className="sr-only sm:hidden">Disconnect</span>
              </Button>
            )}
          </div>
          <CardContent
            id="mqtt-connection-fields"
            hidden={!connectionOpen}
            className="space-y-5 border-t py-5"
          >
            <Callout title="Credentials" type="warning">
              Credentials are stored unencrypted in this browser. Avoid shared
              devices.
            </Callout>
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
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
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
                        <div className="flex items-center gap-1">
                          <Label htmlFor="mqtt-keepalive">
                            Keep alive (seconds)
                          </Label>
                          <InfoTooltip>
                            Interval between MQTT keep-alive packets. Set to 0
                            to disable keep-alive checks.
                          </InfoTooltip>
                        </div>
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
                          <p role="alert" className="text-xs text-destructive">
                            {field.state.meta.errors.join(", ")}
                          </p>
                        )}
                      </div>
                    )}
                  </form.Field>
                </div>
              </fieldset>
              <div className="flex items-center justify-end gap-2 border-t pt-4">
                <details className="group relative z-20 min-w-0 flex-1 sm:flex-none">
                  <summary className="flex h-9 w-full cursor-pointer list-none items-center justify-center gap-1.5 rounded-md border bg-background px-2.5 text-sm font-medium shadow-xs hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
                    <KeyRound aria-hidden="true" className="size-4" />
                    <span className="truncate">Manage credentials</span>
                    <ChevronDown
                      aria-hidden="true"
                      className="size-3.5 transition-transform group-open:rotate-180"
                    />
                  </summary>
                  <div className="absolute bottom-[calc(100%+0.5rem)] right-0 z-30 w-72 max-w-[calc(100vw-2rem)] space-y-3 rounded-lg border bg-popover p-3 text-popover-foreground shadow-lg">
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
                            options, username, password and client ID. A backup
                            is required to restore them.
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
                <Button
                  type="submit"
                  className="w-24 shrink-0 sm:w-36"
                  disabled={!loaded || status !== "disconnected"}
                >
                  <PlugZap aria-hidden="true" />
                  {status === "connecting" ? "Connecting…" : "Connect"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </section>
      <div className="grid w-full min-w-0 gap-4 lg:grid-cols-[minmax(16rem,28%)_minmax(0,1fr)]">
        <div className="min-w-0 space-y-4">
          <MqttTopicSidebar
            subscriptions={preferences.subscriptions}
            activeTopics={activeTopics}
            entries={log}
            selectedTopic={selectedTopic}
            onSelectTopic={setSelectedTopic}
            subscribeTopic={subscribeTopic}
            onSubscribeTopicChange={setSubscribeTopic}
            subscribeQos={subscribeQos}
            onSubscribeQosChange={setSubscribeQos}
            subscriptionBusy={subscriptionBusy}
            connected={status === "connected"}
            onSubscribe={() => void subscribe()}
            onUnsubscribe={(topic) => void unsubscribe(topic)}
            onUpdateSubscription={editSubscription}
          />
          <MqttPublishComposer
            mode={mode}
            onModeChange={setMode}
            commandPayload={commandPayload}
            onCommandPayloadChange={setCommandPayload}
            settingsPayload={settingsPayload}
            onSettingsPayloadChange={setSettingsPayload}
            requestPayload={requestPayload}
            onRequestPayloadChange={setRequestPayload}
            customPayload={customPayload}
            onCustomPayloadChange={setCustomPayload}
            responseTopic={responseTopic}
            onResponseTopicChange={setResponseTopic}
            correlationData={correlationData}
            onCorrelationDataChange={setCorrelationData}
            topic={publishTopic}
            onTopicChange={(value) =>
              updatePreferences({ ...preferences, [topicField]: value })
            }
            qos={publishQos}
            onQosChange={setPublishQos}
            retain={retain}
            onRetainChange={setRetain}
            connected={status === "connected"}
            busy={publishBusy}
            onPublish={() => void publish()}
          />
        </div>
        <section
          aria-label="MQTT workspace"
          className="min-w-0 lg:relative lg:min-h-0"
        >
          {/* Let the left column set the row height; messages fill it without making the grid grow. */}
          <Card className="flex min-h-0 flex-col gap-0 overflow-hidden py-0 lg:absolute lg:inset-0">
            <div className="flex min-w-0 flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
              <div className="min-w-0">
                <h2 className="text-sm font-semibold">
                  Messages{" "}
                  <span className="font-normal text-muted-foreground">
                    ({filterMqttMessages(log, "all", selectedTopic).length}/
                    {log.length})
                  </span>
                </h2>
                {selectedTopic && (
                  <p className="break-all font-mono text-xs text-muted-foreground">
                    {selectedTopic}
                  </p>
                )}
              </div>
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
            <div className="flex min-h-0 min-w-0 flex-1 flex-col p-3 sm:p-4">
              <MqttMessageLog
                key={selectedTopic ?? "all"}
                entries={log}
                topicFilter={selectedTopic}
              />
            </div>
          </Card>
        </section>
      </div>
    </div>
  );
}
