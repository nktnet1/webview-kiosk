import { Ellipsis, ListTree, Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Button } from "#/components/ui/button";
import { Card } from "#/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "#/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "#/components/ui/dropdown-menu";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import { cn } from "#/lib/cn";
import { type LogEntry, matchesMqttTopicFilter } from "./MqttMessageLog";
import { MqttQosSelect } from "./MqttQosSelect";
import type { MqttQos, MqttSubscription } from "./validation";

type Props = {
  subscriptions: MqttSubscription[];
  activeTopics: string[];
  entries: LogEntry[];
  selectedTopic: string | null;
  onSelectTopic: (topic: string | null) => void;
  subscribeTopic: string;
  onSubscribeTopicChange: (topic: string) => void;
  subscribeQos: MqttQos;
  onSubscribeQosChange: (qos: MqttQos) => void;
  subscriptionBusy: boolean;
  connected: boolean;
  onSubscribe: () => void;
  onUnsubscribe: (topic: string) => void;
  onUpdateSubscription: (
    previousTopic: string,
    replacement: MqttSubscription,
  ) => Promise<boolean>;
};

export function MqttTopicSidebar({
  subscriptions,
  activeTopics,
  entries,
  selectedTopic,
  onSelectTopic,
  subscribeTopic,
  onSubscribeTopicChange,
  subscribeQos,
  onSubscribeQosChange,
  subscriptionBusy,
  connected,
  onSubscribe,
  onUnsubscribe,
  onUpdateSubscription,
}: Props) {
  const [editingTopic, setEditingTopic] = useState<string | null>(null);
  const [draftTopic, setDraftTopic] = useState("");
  const [draftQos, setDraftQos] = useState<MqttQos>(0);

  function beginEdit(subscription: MqttSubscription) {
    setEditingTopic(subscription.topic);
    setDraftTopic(subscription.topic);
    setDraftQos(subscription.qos);
  }

  return (
    <aside aria-label="MQTT topics" className="min-w-0">
      <Card className="gap-0 overflow-hidden py-0">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <h2 className="text-sm font-semibold">Topics</h2>
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums text-muted-foreground">
            {subscriptions.length}
          </span>
        </div>
        <nav aria-label="Filter messages by topic" className="space-y-1 p-2">
          <Button
            type="button"
            variant={selectedTopic === null ? "secondary" : "ghost"}
            className="w-full justify-between gap-2 px-3"
            aria-pressed={selectedTopic === null}
            onClick={() => onSelectTopic(null)}
          >
            <span className="flex min-w-0 items-center gap-2">
              <ListTree aria-hidden="true" className="size-4 shrink-0" />
              All messages
            </span>
            <span className="text-xs tabular-nums text-muted-foreground">
              {entries.length}
            </span>
          </Button>
          <section
            aria-label="Subscriptions"
            className="max-h-[11rem] space-y-1 overflow-y-auto overscroll-contain"
          >
            {subscriptions.map((subscription) => {
              const active = activeTopics.includes(subscription.topic);
              const selected = selectedTopic === subscription.topic;
              const count = entries.filter((entry) =>
                matchesMqttTopicFilter(subscription.topic, entry.topic),
              ).length;
              return (
                <div
                  key={subscription.topic}
                  className="flex h-14 min-w-0 items-center gap-1"
                >
                  <Button
                    type="button"
                    variant={selected ? "secondary" : "ghost"}
                    className="h-full min-w-0 flex-1 justify-start gap-2 px-3 py-1 text-left"
                    aria-pressed={selected}
                    onClick={() => onSelectTopic(subscription.topic)}
                  >
                    <span
                      role="img"
                      aria-label={
                        active ? "Subscription active" : "Subscription inactive"
                      }
                      className={cn(
                        "size-2 shrink-0 rounded-full",
                        active ? "bg-emerald-500" : "bg-muted-foreground/40",
                      )}
                    />
                    <span className="min-w-0 flex-1">
                      <span
                        className="block truncate font-mono text-xs leading-5"
                        title={subscription.topic}
                      >
                        {subscription.topic}
                      </span>
                      <span className="block text-xs font-normal text-muted-foreground">
                        QoS {subscription.qos} · {count} messages
                      </span>
                    </span>
                  </Button>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        className="shrink-0"
                        aria-label={`Actions for ${subscription.topic}`}
                        disabled={subscriptionBusy}
                      >
                        <Ellipsis aria-hidden="true" className="size-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        onSelect={() => beginEdit(subscription)}
                      >
                        <Pencil aria-hidden="true" />
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        className="text-destructive focus:text-destructive"
                        onSelect={() => onUnsubscribe(subscription.topic)}
                      >
                        <Trash2 aria-hidden="true" />
                        Remove
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              );
            })}
            {subscriptions.length === 0 && (
              <p className="px-3 py-4 text-sm text-muted-foreground">
                No subscribed topics.
              </p>
            )}
          </section>
        </nav>
        <form
          className="space-y-3 border-t p-3"
          onSubmit={(event) => {
            event.preventDefault();
            onSubscribe();
          }}
        >
          <h3 className="text-xs font-semibold text-muted-foreground">
            Subscribe to topic
          </h3>
          <Label className="sr-only" htmlFor="mqtt-subscription-topic">
            Topic filter
          </Label>
          <Input
            id="mqtt-subscription-topic"
            placeholder="wk/event/#"
            spellCheck={false}
            value={subscribeTopic}
            onChange={(event) => onSubscribeTopicChange(event.target.value)}
          />
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              <Label className="sr-only" htmlFor="mqtt-subscription-qos">
                Subscription QoS
              </Label>
              <MqttQosSelect
                id="mqtt-subscription-qos"
                value={subscribeQos}
                onChange={onSubscribeQosChange}
              />
            </div>
            <Button type="submit" disabled={!connected || subscriptionBusy}>
              <Plus aria-hidden="true" className="size-4" />
              Subscribe
            </Button>
          </div>
        </form>
      </Card>
      <Dialog
        open={editingTopic !== null}
        onOpenChange={(open) => {
          if (!open) setEditingTopic(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit subscription</DialogTitle>
            <DialogDescription>
              Change the topic filter or QoS. Connected subscriptions update
              immediately.
            </DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (editingTopic === null || subscriptionBusy) return;
              void onUpdateSubscription(editingTopic, {
                topic: draftTopic,
                qos: draftQos,
              }).then((updated) => {
                if (updated) setEditingTopic(null);
              });
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="mqtt-edit-topic">Topic filter</Label>
              <Input
                id="mqtt-edit-topic"
                required
                spellCheck={false}
                value={draftTopic}
                onChange={(event) => setDraftTopic(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mqtt-edit-qos">QoS</Label>
              <MqttQosSelect
                id="mqtt-edit-qos"
                value={draftQos}
                onChange={setDraftQos}
              />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingTopic(null)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={subscriptionBusy}>
                {subscriptionBusy ? "Saving…" : "Save changes"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </aside>
  );
}
