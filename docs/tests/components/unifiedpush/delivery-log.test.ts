import { describe, expect, it } from "vitest";
import {
  appendPushDelivery,
  describePushNetworkError,
  describePushServiceError,
  finishPushDelivery,
  formatPushDeliveryPayload,
  MAX_PUSH_LOG_ENTRIES,
  type PushDeliveryEntry,
} from "../../../src/components/unifiedpush/delivery-log";

function entry(id: number): PushDeliveryEntry {
  return {
    id,
    timestamp: "2026-10-14T05:00:00.000Z",
    time: "4:00:00 pm",
    mode: "encrypted",
    kind: "command",
    payload: '{"type":"command"}',
    outcome: "sending",
    detail: "Waiting for the push service response…",
  };
}

describe("UnifiedPush delivery history", () => {
  it("keeps only the latest 100 attempts in chronological order", () => {
    let entries: PushDeliveryEntry[] = [];
    for (let id = 1; id <= MAX_PUSH_LOG_ENTRIES + 2; id++) {
      entries = appendPushDelivery(entries, entry(id));
    }
    expect(entries).toHaveLength(MAX_PUSH_LOG_ENTRIES);
    expect(entries[0]?.id).toBe(3);
    expect(entries.at(-1)?.id).toBe(MAX_PUSH_LOG_ENTRIES + 2);
  });

  it("does not add explanatory text to successful HTTP responses", () => {
    const result = finishPushDelivery([entry(1)], 1, {
      outcome: "accepted",
      detail: "",
      httpStatus: 201,
    });
    expect(result[0]).toMatchObject({
      outcome: "accepted",
      detail: "",
      httpStatus: 201,
    });
  });

  it("resolves only the matching attempt and preserves the HTTP status", () => {
    const entries = [entry(1), entry(2)];
    const finished = finishPushDelivery(entries, 2, {
      outcome: "accepted",
      detail: "",
      httpStatus: 201,
    });
    expect(finished[0]).toEqual(entry(1));
    expect(finished[1]).toMatchObject({
      id: 2,
      outcome: "accepted",
      httpStatus: 201,
    });
    expect(entries[1]?.outcome).toBe("sending");
  });

  it("does not revive attempts cleared while a request was pending", () => {
    expect(
      finishPushDelivery([], 42, { outcome: "failed", detail: "Timed out" }),
    ).toEqual([]);
  });

  it("includes HTTP errors and the push service's useful response", () => {
    expect(
      describePushServiceError(
        { status: 403, statusText: "Forbidden" },
        '{"message":"Invalid VAPID token","errno":109}',
      ),
    ).toBe("HTTP 403 Forbidden: Invalid VAPID token");
    expect(describePushServiceError({ status: 404, statusText: "" }, "")).toBe(
      "HTTP 404. The endpoint may be invalid or expired. Register again if needed.",
    );
    expect(
      describePushServiceError(
        { status: 400, statusText: "Bad Request" },
        "missing header",
      ),
    ).toBe("HTTP 400 Bad Request: missing header");
    expect(describePushServiceError({ status: 429, statusText: "" }, "")).toBe(
      "HTTP 429. Rate limited. Try again later.",
    );
    expect(
      describePushServiceError(
        { status: 500, statusText: "" },
        "x".repeat(2000),
      ),
    ).toBe(`HTTP 500: ${"x".repeat(1000)}…`);
  });

  it("gives actionable feedback when the browser cannot read a response", () => {
    expect(
      describePushNetworkError(new DOMException("", "TimeoutError")),
    ).toMatch(/timed out/);
    expect(describePushNetworkError(new TypeError("Failed to fetch"))).toMatch(
      /CORS/,
    );
  });

  it("formats JSON and preserves invalid JSON as text", () => {
    expect(formatPushDeliveryPayload('{"type":"command"}')).toEqual({
      language: "json",
      code: '{\n  "type": "command"\n}',
    });
    expect(formatPushDeliveryPayload("unexpected payload")).toEqual({
      language: "text",
      code: "unexpected payload",
    });
  });
});
