import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import { ArrowDownLeft, ArrowUpRight, CircleHelp } from "lucide-react";
import { useState } from "react";
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
    <details
      className="group min-w-0 px-4 py-3 open:bg-muted/20"
      onToggle={(event) => setExpanded(event.currentTarget.open)}
    >
      <summary className="flex cursor-pointer list-none items-start gap-3 text-sm [&::-webkit-details-marker]:hidden">
        <span className="inline-flex min-w-24 shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
          {received ? (
            <ArrowDownLeft aria-hidden="true" className="size-4" />
          ) : (
            <ArrowUpRight aria-hidden="true" className="size-4" />
          )}
          {received ? "Received" : "Published"}
        </span>
        <span className="min-w-0 flex-1 break-all font-mono text-xs leading-5">
          {entry.topic}
        </span>
        <span className="shrink-0 text-xs text-muted-foreground">
          {entry.time}
        </span>
      </summary>
      {expanded && formatted && (
        <div className="mt-3 min-w-0 space-y-2">
          <p className="text-xs text-muted-foreground">
            QoS {entry.qos}
            {entry.retain ? " · Retained" : ""}
          </p>
          <DynamicCodeBlock
            lang={formatted.language}
            code={formatted.code}
            codeblock={{
              className: "my-0",
              viewportProps: { className: "max-h-80" },
            }}
          />
        </div>
      )}
    </details>
  );
}

export function MqttMessageLog({ entries }: { entries: LogEntry[] }) {
  return (
    <div
      className="max-h-[42rem] min-w-0 divide-y overflow-y-auto rounded-md border"
      aria-label="MQTT messages"
      role="log"
    >
      {entries.map((entry) => (
        <MessageEntry key={entry.id} entry={entry} />
      ))}
      {entries.length === 0 && (
        <div className="flex items-center gap-2 p-4 text-sm text-muted-foreground">
          <CircleHelp aria-hidden="true" className="size-4" />
          Messages appear here when published or received.
        </div>
      )}
    </div>
  );
}
