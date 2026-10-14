import { ListTree, Plus, Trash2 } from "lucide-react";
import { Button } from "#/components/ui/button";
import { Card } from "#/components/ui/card";
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
}: Props) {
  return (
    <aside aria-label="MQTT topics" className="min-w-0 lg:self-start">
      <Card className="gap-0 overflow-hidden py-0 lg:sticky lg:top-5">
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
          <div className="max-h-52 space-y-1 overflow-y-auto lg:max-h-[28rem]">
            {subscriptions.map((subscription) => {
              const active = activeTopics.includes(subscription.topic);
              const selected = selectedTopic === subscription.topic;
              const count = entries.filter((entry) =>
                matchesMqttTopicFilter(subscription.topic, entry.topic),
              ).length;
              return (
                <div
                  key={subscription.topic}
                  className="flex min-w-0 items-start gap-1"
                >
                  <Button
                    type="button"
                    variant={selected ? "secondary" : "ghost"}
                    className="h-auto min-w-0 flex-1 justify-start gap-2 whitespace-normal px-3 py-2 text-left"
                    aria-pressed={selected}
                    onClick={() => onSelectTopic(subscription.topic)}
                  >
                    <span
                      role="img"
                      aria-label={
                        active ? "Subscription active" : "Subscription inactive"
                      }
                      className={cn(
                        "mt-1 size-2 shrink-0 rounded-full",
                        active ? "bg-emerald-500" : "bg-muted-foreground/40",
                      )}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block break-all font-mono text-xs leading-5">
                        {subscription.topic}
                      </span>
                      <span className="block text-xs font-normal text-muted-foreground">
                        QoS {subscription.qos} · {count} messages
                      </span>
                    </span>
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    className="mt-1 shrink-0"
                    aria-label={`Unsubscribe ${subscription.topic}`}
                    disabled={subscriptionBusy}
                    onClick={() => onUnsubscribe(subscription.topic)}
                  >
                    <Trash2 aria-hidden="true" className="size-4" />
                  </Button>
                </div>
              );
            })}
            {subscriptions.length === 0 && (
              <p className="px-3 py-4 text-sm text-muted-foreground">
                No subscribed topics.
              </p>
            )}
          </div>
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
    </aside>
  );
}
