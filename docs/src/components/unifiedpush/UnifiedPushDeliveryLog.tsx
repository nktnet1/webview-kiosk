import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import {
  CheckCircle2,
  CircleAlert,
  CircleX,
  LoaderCircle,
  Send,
  Trash2,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "#/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card";
import {
  formatPushDeliveryPayload,
  type PushDeliveryEntry,
  type PushDeliveryOutcome,
} from "./delivery-log";

const outcomeDisplay = {
  sending: {
    label: "Sending",
    Icon: LoaderCircle,
    className: "text-sky-700 dark:text-sky-400 bg-sky-500/10",
  },
  accepted: {
    label: "Accepted",
    Icon: CheckCircle2,
    className: "text-emerald-700 dark:text-emerald-400 bg-emerald-500/10",
  },
  rejected: {
    label: "Rejected",
    Icon: CircleX,
    className: "text-destructive bg-destructive/10",
  },
  unconfirmed: {
    label: "Unconfirmed",
    Icon: CircleAlert,
    className: "text-amber-700 dark:text-amber-400 bg-amber-500/10",
  },
  failed: {
    label: "Failed",
    Icon: CircleX,
    className: "text-destructive bg-destructive/10",
  },
} satisfies Record<
  PushDeliveryOutcome,
  {
    label: string;
    Icon: typeof Send;
    className: string;
  }
>;

function DeliveryEntry({ entry }: { entry: PushDeliveryEntry }) {
  const display = outcomeDisplay[entry.outcome];
  const formatted = formatPushDeliveryPayload(entry.payload);

  return (
    <li className="min-w-0">
      <article className="min-w-0 rounded-xl border bg-muted/30 p-3 space-y-3">
        <div className="flex min-w-0 items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            <span
              className={`inline-flex size-7 shrink-0 items-center justify-center rounded-full ${display.className}`}
            >
              <display.Icon
                aria-hidden="true"
                className={`size-4 ${entry.outcome === "sending" ? "animate-spin" : ""}`}
              />
            </span>
            <div className="min-w-0">
              <p className="text-xs font-semibold">{display.label}</p>
              <p className="text-xs text-muted-foreground">Outgoing push</p>
            </div>
          </div>
          <time
            className="shrink-0 text-xs text-muted-foreground"
            dateTime={entry.timestamp}
          >
            {entry.time}
          </time>
        </div>
        <dl className="flex flex-wrap gap-1.5 text-xs">
          <div className="flex gap-1.5 rounded-md border bg-background/60 px-2 py-1">
            <dt className="text-muted-foreground">Type</dt>
            <dd className="font-medium capitalize">{entry.kind}</dd>
          </div>
          <div className="flex gap-1.5 rounded-md border bg-background/60 px-2 py-1">
            <dt className="text-muted-foreground">Mode</dt>
            <dd className="font-medium">
              {entry.mode === "encrypted" ? "Encrypted" : "Plaintext"}
            </dd>
          </div>
          {entry.httpStatus !== undefined && (
            <div className="flex gap-1.5 rounded-md border bg-background/60 px-2 py-1">
              <dt className="text-muted-foreground">HTTP</dt>
              <dd className="font-medium">{entry.httpStatus}</dd>
            </div>
          )}
        </dl>
        {entry.detail && (
          <p
            role="status"
            className="wrap-anywhere text-xs leading-relaxed text-muted-foreground"
          >
            {entry.detail}
          </p>
        )}
        <div className="min-w-0">
          <DynamicCodeBlock
            lang={formatted.language}
            code={formatted.code}
            codeblock={{
              title: formatted.language === "json" ? "JSON payload" : "Payload",
              className: "my-0",
              viewportProps: { className: "max-h-72" },
            }}
          />
        </div>
      </article>
    </li>
  );
}

export function UnifiedPushDeliveryLog({
  entries,
  onClear,
}: {
  entries: readonly PushDeliveryEntry[];
  onClear: () => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const followLatest = useRef(true);
  useEffect(() => {
    const element = scrollRef.current;
    if (entries.length === 0) followLatest.current = true;
    if (element && followLatest.current)
      element.scrollTop = element.scrollHeight;
  }, [entries]);

  return (
    <Card className="h-full min-h-[26rem] min-w-0 gap-0 overflow-hidden">
      <CardHeader className="flex flex-row items-center justify-between gap-3 border-b pb-4">
        <div className="min-w-0 space-y-1">
          <CardTitle>Delivery log</CardTitle>
          <p className="text-xs text-muted-foreground">
            Push service responses, not device delivery receipts
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={entries.length === 0}
          onClick={onClear}
        >
          <Trash2 aria-hidden="true" /> Clear
        </Button>
      </CardHeader>
      <CardContent className="relative min-h-[26rem] flex-1 px-0">
        <div
          ref={scrollRef}
          role="log"
          aria-label="UnifiedPush delivery attempts"
          className="absolute inset-0 overflow-y-auto p-4 sm:p-5"
          onScroll={(event) => {
            const element = event.currentTarget;
            followLatest.current =
              element.scrollHeight - element.scrollTop - element.clientHeight
              < 48;
          }}
        >
          {entries.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 px-4 text-center text-sm text-muted-foreground">
              <Send aria-hidden="true" className="size-7 opacity-50" />
              <p>
                Send a test push to inspect its request and HTTP result here.
              </p>
            </div>
          ) : (
            <ol className="space-y-3">
              {entries.map((entry) => (
                <DeliveryEntry key={entry.id} entry={entry} />
              ))}
            </ol>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
