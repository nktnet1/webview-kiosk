import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import {
  TabsList as FumadocsTabsList,
  TabsTrigger as FumadocsTabsTrigger,
} from "fumadocs-ui/components/tabs";
import {
  Tabs as FumadocsTabs,
  TabsContent as FumadocsTabsContent,
} from "fumadocs-ui/components/ui/tabs";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronDown,
  ChevronsDown,
  ChevronsUp,
  CircleHelp,
  Info,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "#/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "#/components/ui/tooltip";
import { cn } from "#/lib/cn";
import { type MqttPublishProperties, reportedMqttSender } from "./properties";
import type { MqttQos } from "./validation";

export type LogEntry = {
  id: number;
  time: string;
  direction: "received" | "published";
  topic: string;
  payload: string;
  qos: MqttQos;
  retain: boolean;
  duplicate?: boolean;
  properties?: MqttPublishProperties;
  /** Local connection identity for debugger-originated messages. */
  sender?: { username?: string; clientId?: string };
};

export type MessageFilter = "all" | LogEntry["direction"];

export function appendMqttMessage(
  entries: LogEntry[],
  entry: LogEntry,
): LogEntry[] {
  // Maintain chronological order while bounding the browser's in-memory log.
  return [...entries, entry].slice(-100);
}

/** Match MQTT topic levels, including the single- and multi-level wildcards.
 * As required by MQTT, root wildcards never match $-prefixed system topics.
 */
export function matchesMqttTopicFilter(filter: string, topic: string): boolean {
  if (topic.startsWith("$") && !filter.startsWith("$")) return false;
  const filterLevels = filter.split("/");
  const topicLevels = topic.split("/");
  for (let i = 0; i < filterLevels.length; i++) {
    const level = filterLevels[i];
    if (level === "#") return i === filterLevels.length - 1;
    if (
      i >= topicLevels.length
      || (level !== "+" && level !== topicLevels[i])
    ) {
      return false;
    }
  }
  return filterLevels.length === topicLevels.length;
}

export function filterMqttMessages(
  entries: LogEntry[],
  filter: MessageFilter,
  topicFilter: string | null = null,
): LogEntry[] {
  if (filter === "all" && topicFilter === null) return entries;
  return entries.filter(
    (entry) =>
      (filter === "all" || entry.direction === filter)
      && (topicFilter === null
        || matchesMqttTopicFilter(topicFilter, entry.topic)),
  );
}

export function formatMqttMessagePayload(payload: string): {
  language: "json" | "text";
  code: string;
} {
  try {
    return {
      language: "json",
      code: JSON.stringify(JSON.parse(payload), null, 2),
    };
  } catch {
    return { language: "text", code: payload };
  }
}

/** Human-readable PUBLISH properties; preserve repeated MQTT user properties. */
export function mqttPublishPropertyRows(
  properties: MqttPublishProperties,
): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (properties.payloadFormatIndicator !== undefined)
    rows.push({
      label: "Payload format",
      value: properties.payloadFormatIndicator === 1 ? "UTF-8" : "Binary",
    });
  if (properties.contentType !== undefined)
    rows.push({ label: "Content type", value: properties.contentType });
  if (properties.messageExpiryInterval !== undefined)
    rows.push({
      label: "Message expiry",
      value: `${properties.messageExpiryInterval} seconds`,
    });
  if (properties.responseTopic !== undefined)
    rows.push({ label: "Response topic", value: properties.responseTopic });
  if (properties.correlationData !== undefined)
    rows.push({
      label: "Correlation data (hex)",
      value: `0x${properties.correlationData}`,
    });
  if (properties.topicAlias !== undefined)
    rows.push({ label: "Topic alias", value: String(properties.topicAlias) });
  for (const identifier of properties.subscriptionIdentifiers ?? [])
    rows.push({ label: "Subscription ID", value: String(identifier) });
  for (const { name, value } of properties.userProperties ?? [])
    rows.push({ label: `User property: ${name}`, value });
  return rows;
}

function MessagePropertiesTooltip({
  properties,
}: {
  properties: MqttPublishProperties;
}) {
  const rows = mqttPublishPropertyRows(properties);
  if (rows.length === 0) return null;

  const seen = new Map<string, number>();
  return (
    <TooltipProvider delayDuration={150}>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label="MQTT 5 properties"
            className="absolute top-3 right-3 z-10 inline-flex size-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-background/80 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Info aria-hidden="true" className="size-4" />
          </button>
        </TooltipTrigger>
        <TooltipContent
          side="top"
          align="end"
          className="max-h-[min(28rem,70vh)] w-max max-w-[min(26rem,calc(100vw-2rem))] overflow-y-auto p-3"
        >
          <p className="mb-2 font-semibold">MQTT 5 properties</p>
          <dl className="grid grid-cols-[minmax(0,8rem)_minmax(0,1fr)] gap-x-3 gap-y-1.5">
            {rows.map(({ label, value }) => {
              const identity = `${label}:${value}`;
              const occurrence = seen.get(identity) ?? 0;
              seen.set(identity, occurrence + 1);
              return (
                <div key={`${identity}:${occurrence}`} className="contents">
                  <dt className="min-w-0 break-words text-muted-foreground">
                    {label}
                  </dt>
                  <dd className="min-w-0 break-all font-mono">{value}</dd>
                </div>
              );
            })}
          </dl>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function MessageDetail({ label, value }: { label: string; value: string }) {
  return (
    <span className="flex max-w-full min-w-0 items-baseline gap-1.5 rounded-md border border-border/60 bg-background/60 px-2 py-1">
      <span className="shrink-0 text-muted-foreground">{label}</span>
      <span className="min-w-0 break-all font-medium text-foreground">
        {value}
      </span>
    </span>
  );
}

function MessageDetails({ entry }: { entry: LogEntry }) {
  const received = entry.direction === "received";
  const reported = received ? reportedMqttSender(entry.properties) : null;
  const username = received ? reported?.username : entry.sender?.username;
  const from = received
    ? (username ?? "Not reported")
    : username
      ? `You (${username})`
      : "You";
  const identifier = received
    ? reported?.appInstanceId
    : entry.sender?.clientId;

  return (
    <dl
      aria-label="Message details"
      className="flex flex-wrap gap-1.5 text-[11px] leading-4"
    >
      <MessageDetail label="QoS" value={String(entry.qos)} />
      <MessageDetail label="From" value={from} />
      {identifier && (
        <MessageDetail
          label={received ? "Instance" : "Client"}
          value={identifier}
        />
      )}
      {entry.retain && (
        <div className="rounded-md border border-border/60 bg-background/60 px-2 py-1 text-muted-foreground">
          <dt className="sr-only">Retain flag</dt>
          <dd>Retained</dd>
        </div>
      )}
      {entry.duplicate && (
        <div className="rounded-md border border-border/60 bg-background/60 px-2 py-1 text-muted-foreground">
          <dt className="sr-only">Duplicate flag</dt>
          <dd>Duplicate</dd>
        </div>
      )}
    </dl>
  );
}

function MessageEntry({
  entry,
  expanded,
  onToggle,
}: {
  entry: LogEntry;
  expanded: boolean;
  onToggle: () => void;
}) {
  const received = entry.direction === "received";
  const payloadId = `mqtt-message-payload-${entry.id}`;
  const formatted = expanded ? formatMqttMessagePayload(entry.payload) : null;

  return (
    <li
      className={cn("flex min-w-0", received ? "justify-start" : "justify-end")}
    >
      <article
        className={cn(
          "relative min-w-0 w-full rounded-xl border sm:max-w-[85%] xl:max-w-[76%]",
          received
            ? "border-border bg-muted/50"
            : "border-emerald-500/25 bg-emerald-500/8 dark:bg-emerald-500/10",
        )}
      >
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={payloadId}
          onClick={onToggle}
          className="w-full cursor-pointer rounded-xl p-3 pb-1.5 pr-11 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          <div className="flex min-w-0 items-start gap-2.5">
            <span
              className={cn(
                "inline-flex size-7 shrink-0 items-center justify-center rounded-full",
                received
                  ? "bg-sky-500/15 text-sky-700 dark:text-sky-400"
                  : "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400",
              )}
            >
              {received ? (
                <ArrowDownLeft aria-hidden="true" className="size-4" />
              ) : (
                <ArrowUpRight aria-hidden="true" className="size-4" />
              )}
            </span>
            <div className="min-w-0 flex-1 space-y-1">
              <div className="flex items-center justify-between gap-2">
                <span
                  className={cn(
                    "text-xs font-semibold",
                    received
                      ? "text-sky-700 dark:text-sky-400"
                      : "text-emerald-700 dark:text-emerald-400",
                  )}
                >
                  {received ? "Received" : "Published"}
                </span>
                <span className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
                  <time>{entry.time}</time>
                  <ChevronDown
                    aria-hidden="true"
                    className={cn(
                      "size-3.5 transition-transform",
                      expanded && "rotate-180",
                    )}
                  />
                </span>
              </div>
              <p className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs leading-5">
                <span className="shrink-0 text-muted-foreground">
                  {received ? "Topic" : "To"}
                </span>
                <span className="min-w-0 wrap-anywhere font-mono text-foreground">
                  {entry.topic}
                </span>
              </p>
            </div>
          </div>
        </button>
        <div className="px-3 pb-3">
          <MessageDetails entry={entry} />
        </div>
        {entry.properties && (
          <MessagePropertiesTooltip properties={entry.properties} />
        )}
        <div id={payloadId} hidden={!expanded} className="min-w-0 px-3 pb-3">
          {formatted
            && (entry.payload.length > 0 ? (
              <DynamicCodeBlock
                lang={formatted.language}
                code={formatted.code}
                codeblock={{
                  className: "my-0",
                  viewportProps: { className: "max-h-80" },
                }}
              />
            ) : (
              <p className="text-xs text-muted-foreground">Empty payload</p>
            ))}
        </div>
      </article>
    </li>
  );
}

const MESSAGE_FILTERS: { value: MessageFilter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "received", label: "Received" },
  { value: "published", label: "Published" },
];

/** Messages start expanded; only explicit user actions collapse them. */
export function expandedMqttMessageIds(
  entries: readonly LogEntry[],
  collapsedIds: ReadonlySet<number>,
): ReadonlySet<number> {
  return new Set(
    entries
      .filter((entry) => !collapsedIds.has(entry.id))
      .map((entry) => entry.id),
  );
}

/** Toggling a message never changes the expansion state of other messages. */
export function toggleMqttMessageExpansion(
  collapsedIds: ReadonlySet<number>,
  id: number,
): ReadonlySet<number> {
  const next = new Set(collapsedIds);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

/** Expand/collapse only the currently visible entries, preserving hidden ones. */
export function setMqttMessageExpansion(
  collapsedIds: ReadonlySet<number>,
  visibleEntries: readonly LogEntry[],
  expand: boolean,
): ReadonlySet<number> {
  const next = new Set(collapsedIds);
  for (const entry of visibleEntries) {
    if (expand) next.delete(entry.id);
    else next.add(entry.id);
  }
  return next;
}

/** Forget choices for messages dropped from the bounded history. */
export function pruneMqttMessageExpansion(
  collapsedIds: ReadonlySet<number>,
  entries: readonly LogEntry[],
): ReadonlySet<number> {
  if (collapsedIds.size === 0) return collapsedIds;
  const ids = new Set(entries.map((entry) => entry.id));
  if ([...collapsedIds].every((id) => ids.has(id))) return collapsedIds;
  return new Set([...collapsedIds].filter((id) => ids.has(id)));
}

export function MqttMessageLog({
  entries,
  topicFilter = null,
}: {
  entries: LogEntry[];
  topicFilter?: string | null;
}) {
  const [filter, setFilter] = useState<MessageFilter>("all");
  const [collapsedIds, setCollapsedIds] = useState<ReadonlySet<number>>(
    () => new Set(),
  );
  const scrollRef = useRef<HTMLDivElement>(null);
  const followLatest = useRef(true);
  const lastScrollKey = useRef<string | null>(null);
  const visibleEntries = filterMqttMessages(entries, filter, topicFilter);
  const latestId = visibleEntries.at(-1)?.id ?? null;
  const expandedIds = expandedMqttMessageIds(visibleEntries, collapsedIds);

  useEffect(() => {
    setCollapsedIds((current) => pruneMqttMessageExpansion(current, entries));
  }, [entries]);

  const scrollKey = JSON.stringify([latestId, filter, topicFilter]);
  useEffect(() => {
    if (lastScrollKey.current === scrollKey) return;
    lastScrollKey.current = scrollKey;
    const element = scrollRef.current;
    if (element && followLatest.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [scrollKey]);

  return (
    <FumadocsTabs
      value={filter}
      onValueChange={(value) => {
        followLatest.current = true;
        setFilter(value as MessageFilter);
      }}
      className="flex min-h-0 min-w-0 flex-1 flex-col gap-3"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FumadocsTabsList
          aria-label="Filter MQTT messages"
          className="border-b border-border px-1"
        >
          {MESSAGE_FILTERS.map(({ value, label }) => (
            <FumadocsTabsTrigger
              key={value}
              value={value}
              className="rounded-t-md px-4 data-[state=active]:bg-fd-accent/50 data-[state=active]:font-semibold"
            >
              {label}
            </FumadocsTabsTrigger>
          ))}
        </FumadocsTabsList>
        <fieldset
          aria-label="Message expansion"
          className="flex items-center gap-1"
        >
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={
              visibleEntries.length === 0
              || expandedIds.size === visibleEntries.length
            }
            onClick={() =>
              setCollapsedIds((current) =>
                setMqttMessageExpansion(current, visibleEntries, true),
              )
            }
          >
            <ChevronsDown aria-hidden="true" />
            Expand all
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={expandedIds.size === 0}
            onClick={() =>
              setCollapsedIds((current) =>
                setMqttMessageExpansion(current, visibleEntries, false),
              )
            }
          >
            <ChevronsUp aria-hidden="true" />
            Collapse all
          </Button>
        </fieldset>
      </div>
      {MESSAGE_FILTERS.map(({ value }) => (
        <FumadocsTabsContent
          key={value}
          value={value}
          className="min-h-0 min-w-0 flex-1 outline-none"
        >
          {filter === value && (
            <div
              ref={scrollRef}
              className="h-[74vh] min-h-[28rem] min-w-0 overflow-y-auto rounded-lg border bg-background sm:h-[78vh] lg:h-full lg:min-h-0"
              aria-label="MQTT messages"
              role="log"
              onScroll={(event) => {
                const element = event.currentTarget;
                followLatest.current =
                  element.scrollHeight
                    - element.scrollTop
                    - element.clientHeight
                  < 64;
              }}
            >
              {visibleEntries.length > 0 ? (
                <ol className="space-y-3 p-3 sm:p-4">
                  {visibleEntries.map((entry) => (
                    <MessageEntry
                      key={entry.id}
                      entry={entry}
                      expanded={expandedIds.has(entry.id)}
                      onToggle={() =>
                        setCollapsedIds((current) =>
                          toggleMqttMessageExpansion(current, entry.id),
                        )
                      }
                    />
                  ))}
                </ol>
              ) : (
                <div className="flex min-h-48 items-center justify-center gap-2 p-4 text-sm text-muted-foreground">
                  <CircleHelp aria-hidden="true" className="size-4" />
                  {entries.length === 0
                    ? "Messages appear here when published or received."
                    : topicFilter !== null
                      ? "No messages matching this topic and direction."
                      : `No ${filter} messages.`}
                </div>
              )}
            </div>
          )}
        </FumadocsTabsContent>
      ))}
    </FumadocsTabs>
  );
}
