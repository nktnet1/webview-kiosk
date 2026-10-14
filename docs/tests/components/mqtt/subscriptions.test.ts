import { describe, expect, it, vi } from "vitest";
import { replaceMqttSubscription } from "../../../src/components/mqtt/subscriptions";

const subscriptions = [
  { topic: "wk/event/#", qos: 0 as const },
  { topic: "wk/response/#", qos: 1 as const },
];

function fakeClient() {
  return {
    subscribe: vi.fn(async (_topic: string, _qos: number) => {}),
    unsubscribe: vi.fn(async (_topic: string) => {}),
  };
}

describe("editing MQTT subscriptions", () => {
  it("updates saved filters offline without network calls or mutating the source", async () => {
    expect(
      await replaceMqttSubscription(
        subscriptions,
        "wk/event/#",
        { topic: "wk/device/+", qos: 2 },
        null,
        false,
      ),
    ).toEqual([
      { topic: "wk/device/+", qos: 2 },
      { topic: "wk/response/#", qos: 1 },
    ]);
    expect(subscriptions[0]?.topic).toBe("wk/event/#");
  });

  it("subscribes to a replacement before unsubscribing from the previous filter", async () => {
    const calls: string[] = [];
    const client = {
      subscribe: vi.fn(async (topic: string, qos: number) => {
        calls.push(`subscribe ${topic} ${qos}`);
      }),
      unsubscribe: vi.fn(async (topic: string) => {
        calls.push(`unsubscribe ${topic}`);
      }),
    };
    const result = await replaceMqttSubscription(
      subscriptions,
      "wk/event/#",
      { topic: "wk/device/+", qos: 1 },
      client,
      true,
    );
    expect(calls).toEqual([
      "subscribe wk/device/+ 1",
      "unsubscribe wk/event/#",
    ]);
    expect(result[0]).toEqual({ topic: "wk/device/+", qos: 1 });
  });

  it("resubscribes at the new QoS without removing an unchanged topic", async () => {
    const client = fakeClient();
    const result = await replaceMqttSubscription(
      subscriptions,
      "wk/event/#",
      { topic: "wk/event/#", qos: 2 },
      client,
      true,
    );
    expect(client.subscribe).toHaveBeenCalledWith("wk/event/#", 2);
    expect(client.unsubscribe).not.toHaveBeenCalled();
    expect(result[0]?.qos).toBe(2);
  });

  it("subscribes the replacement if the original topic was inactive", async () => {
    const client = fakeClient();
    await replaceMqttSubscription(
      subscriptions,
      "wk/event/#",
      { topic: "wk/notice/#", qos: 0 },
      client,
      false,
    );
    expect(client.subscribe).toHaveBeenCalledWith("wk/notice/#", 0);
    expect(client.unsubscribe).not.toHaveBeenCalled();
  });

  it("rejects invalid, duplicate and missing subscriptions without network changes", async () => {
    const client = fakeClient();
    for (const [previous, next, error] of [
      ["wk/event/#", "wk/response/#", /already saved/],
      ["wk/event/#", "wk/#/oops", /topic filter/],
      ["unknown", "wk/notice/#", /no longer exists/],
    ] as const) {
      await expect(
        replaceMqttSubscription(
          subscriptions,
          previous,
          { topic: next, qos: 0 },
          client,
          true,
        ),
      ).rejects.toThrow(error);
    }
    expect(client.subscribe).not.toHaveBeenCalled();
    expect(client.unsubscribe).not.toHaveBeenCalled();
  });

  it("keeps original settings when subscribe fails", async () => {
    const client = fakeClient();
    client.subscribe.mockRejectedValueOnce(
      new Error("Broker rejected SUBSCRIBE"),
    );
    await expect(
      replaceMqttSubscription(
        subscriptions,
        "wk/event/#",
        { topic: "wk/device/#", qos: 0 },
        client,
        true,
      ),
    ).rejects.toThrow(/Broker rejected/);
    expect(client.unsubscribe).not.toHaveBeenCalled();
    expect(subscriptions[0]?.topic).toBe("wk/event/#");
  });

  it("rolls back a new subscription if unsubscribing from the old one fails", async () => {
    const client = fakeClient();
    client.unsubscribe.mockRejectedValueOnce(new Error("Cannot unsubscribe"));
    await expect(
      replaceMqttSubscription(
        subscriptions,
        "wk/event/#",
        { topic: "wk/device/#", qos: 0 },
        client,
        true,
      ),
    ).rejects.toThrow("Cannot unsubscribe");
    expect(client.unsubscribe.mock.calls.map(([topic]) => topic)).toEqual([
      "wk/event/#",
      "wk/device/#",
    ]);
  });
});
