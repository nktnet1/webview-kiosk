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
  CircleHelp,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cn } from "#/lib/cn";
import type { MqttQos } from "./validation";

export type LogEntry = {
  id: number;
  time: string;
  direction: "received" | "published";
  topic: string;
  payload: string;
  qos: MqttQos;
  retain: boolean;
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
          "min-w-0 w-full rounded-xl border sm:max-w-[85%] xl:max-w-[76%]",
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
          className="w-full cursor-pointer rounded-xl p-3 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
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
              <p className="wrap-anywhere font-mono text-xs leading-5 text-foreground">
                {entry.topic}
              </p>
            </div>
          </div>
          <div className="mt-2 flex items-center justify-between gap-3 text-xs text-muted-foreground">
            <span>
              QoS {entry.qos}
              {entry.retain ? " · Retained" : ""}
            </span>
            <span className="flex shrink-0 items-center gap-1.5">
              {entry.time}
              <ChevronDown
                aria-hidden="true"
                className={cn(
                  "size-3.5 transition-transform",
                  expanded && "rotate-180",
                )}
              />
            </span>
          </div>
        </button>
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

/** An explicit expansion choice remains until the visible stream or filter changes. */
type ExpansionChoice = {
  id: number | null;
  latestId: number | null;
  filter: MessageFilter;
  topicFilter: string | null;
};

export function selectedMqttMessageId(
  entries: LogEntry[],
  filter: MessageFilter,
  topicFilter: string | null,
  choice: ExpansionChoice | null,
): number | null {
  const latestId = entries.at(-1)?.id ?? null;
  return choice?.latestId === latestId
    && choice?.filter === filter
    && choice?.topicFilter === topicFilter
    ? choice.id
    : latestId;
}

export function MqttMessageLog({
  entries,
  topicFilter = null,
}: {
  entries: LogEntry[];
  topicFilter?: string | null;
}) {
  const [filter, setFilter] = useState<MessageFilter>("all");
  const [choice, setChoice] = useState<ExpansionChoice | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const followLatest = useRef(true);
  const lastScrollKey = useRef<string | null>(null);
  const visibleEntries = filterMqttMessages(entries, filter, topicFilter);
  const latestId = visibleEntries.at(-1)?.id ?? null;
  const expandedId = selectedMqttMessageId(
    visibleEntries,
    filter,
    topicFilter,
    choice,
  );

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
      className="flex min-w-0 flex-col gap-3"
    >
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
      {MESSAGE_FILTERS.map(({ value }) => (
        <FumadocsTabsContent
          key={value}
          value={value}
          className="min-w-0 outline-none"
        >
          {filter === value && (
            <div
              ref={scrollRef}
              className="h-[24rem] min-h-48 min-w-0 overflow-y-auto rounded-lg border bg-background sm:h-[30rem]"
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
                      expanded={expandedId === entry.id}
                      onToggle={() =>
                        setChoice({
                          id: expandedId === entry.id ? null : entry.id,
                          latestId,
                          filter,
                          topicFilter,
                        })
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
