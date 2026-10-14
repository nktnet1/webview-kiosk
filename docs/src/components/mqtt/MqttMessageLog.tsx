import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronDown,
  CircleHelp,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "#/components/ui/button";
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

export function previewMqttMessage(payload: string): string {
  if (payload.length === 0) return "Empty payload";
  return payload.length > 320 ? `${payload.slice(0, 320)}…` : payload;
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

function MessageEntry({ entry }: { entry: LogEntry }) {
  const [expanded, setExpanded] = useState(false);
  const formatted = expanded ? formatMqttMessagePayload(entry.payload) : null;
  const received = entry.direction === "received";

  return (
    <li
      className={cn("flex min-w-0", received ? "justify-start" : "justify-end")}
    >
      <details
        className={cn(
          "group min-w-0 w-full rounded-xl border sm:max-w-[85%] xl:max-w-[76%]",
          received
            ? "border-border bg-muted/50"
            : "border-emerald-500/25 bg-emerald-500/8 dark:bg-emerald-500/10",
        )}
        onToggle={(event) => setExpanded(event.currentTarget.open)}
      >
        <summary className="cursor-pointer list-none rounded-xl p-3 outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 [&::-webkit-details-marker]:hidden">
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
          <p className="mt-2 line-clamp-3 wrap-anywhere whitespace-pre-wrap font-mono text-xs leading-relaxed text-foreground/90 group-open:hidden">
            {previewMqttMessage(entry.payload)}
          </p>
          <div className="mt-2 flex items-center justify-between gap-3 text-xs text-muted-foreground">
            <span>
              QoS {entry.qos}
              {entry.retain ? " · Retained" : ""}
            </span>
            <span className="flex shrink-0 items-center gap-1.5">
              {entry.time}
              <ChevronDown
                aria-hidden="true"
                className="size-3.5 transition-transform group-open:rotate-180"
              />
            </span>
          </div>
        </summary>
        {expanded && formatted && (
          <div className="min-w-0 px-3 pb-3">
            {entry.payload.length > 0 ? (
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
            )}
          </div>
        )}
      </details>
    </li>
  );
}

const MESSAGE_FILTERS: { value: MessageFilter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "received", label: "Received" },
  { value: "published", label: "Published" },
];

export function MqttMessageLog({
  entries,
  topicFilter = null,
}: {
  entries: LogEntry[];
  topicFilter?: string | null;
}) {
  const [filter, setFilter] = useState<MessageFilter>("all");
  const scrollRef = useRef<HTMLDivElement>(null);
  const followLatest = useRef(true);
  const visibleEntries = filterMqttMessages(entries, filter, topicFilter);

  useEffect(() => {
    const element = scrollRef.current;
    if (element && followLatest.current) {
      element.scrollTop = element.scrollHeight;
    }
  });

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <fieldset className="inline-flex max-w-full min-w-0 flex-wrap items-center gap-1 rounded-lg bg-muted p-1">
        <legend className="sr-only">Filter MQTT messages</legend>
        {MESSAGE_FILTERS.map(({ value, label }) => (
          <Button
            key={value}
            type="button"
            size="sm"
            variant={filter === value ? "secondary" : "ghost"}
            aria-pressed={filter === value}
            onClick={() => {
              followLatest.current = true;
              setFilter(value);
            }}
          >
            {label}
          </Button>
        ))}
      </fieldset>
      <div
        ref={scrollRef}
        className="h-[24rem] min-h-48 min-w-0 overflow-y-auto rounded-lg border bg-background sm:h-[30rem]"
        aria-label="MQTT messages"
        role="log"
        onScroll={(event) => {
          const element = event.currentTarget;
          followLatest.current =
            element.scrollHeight - element.scrollTop - element.clientHeight
            < 64;
        }}
      >
        {visibleEntries.length > 0 ? (
          <ol className="space-y-3 p-3 sm:p-4">
            {visibleEntries.map((entry) => (
              <MessageEntry key={entry.id} entry={entry} />
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
    </div>
  );
}
