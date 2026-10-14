import type { MqttWebSocketClient } from "./protocol";
import { type MqttSubscription, validateTopic } from "./validation";

export type SubscriptionClient = Pick<
  MqttWebSocketClient,
  "subscribe" | "unsubscribe"
>;

/** Subscribe the replacement first, so a failed subscribe never drops the old topic.
 * Preference changes are returned only after the broker has acknowledged the update.
 */
export async function replaceMqttSubscription(
  subscriptions: MqttSubscription[],
  previousTopic: string,
  replacement: MqttSubscription,
  client: SubscriptionClient | null,
  previousActive: boolean,
): Promise<MqttSubscription[]> {
  if (!subscriptions.some((item) => item.topic === previousTopic)) {
    throw new Error("Subscription no longer exists.");
  }
  const topic = validateTopic(replacement.topic.trim(), true);
  if (
    topic !== previousTopic
    && subscriptions.some((item) => item.topic === topic)
  ) {
    throw new Error("Topic filter is already saved.");
  }

  if (client) {
    await client.subscribe(topic, replacement.qos);
    if (previousActive && previousTopic !== topic) {
      try {
        await client.unsubscribe(previousTopic);
      } catch (error) {
        // Best effort rollback: retain the old saved subscription if removal fails.
        try {
          await client.unsubscribe(topic);
        } catch {
          // Surface the initial unsubscribe failure to the caller.
        }
        throw error;
      }
    }
  }
  return subscriptions.map((item) =>
    item.topic === previousTopic ? { topic, qos: replacement.qos } : item,
  );
}
