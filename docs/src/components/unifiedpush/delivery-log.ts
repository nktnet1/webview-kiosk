/** An in-memory history of HTTP push attempts, not device delivery receipts. */
export type PushDeliveryOutcome =
  | "sending"
  | "accepted"
  | "rejected"
  | "unconfirmed"
  | "failed";

export type PushDeliveryEntry = {
  id: number;
  timestamp: string;
  time: string;
  mode: "encrypted" | "plaintext";
  kind: "command" | "settings";
  payload: string;
  outcome: PushDeliveryOutcome;
  detail: string;
  httpStatus?: number;
};

export const MAX_PUSH_LOG_ENTRIES = 100;

export function appendPushDelivery(
  entries: readonly PushDeliveryEntry[],
  entry: PushDeliveryEntry,
): PushDeliveryEntry[] {
  return [...entries, entry].slice(-MAX_PUSH_LOG_ENTRIES);
}

/** Completing an attempt must not resurrect entries removed with Clear log. */
export function finishPushDelivery(
  entries: readonly PushDeliveryEntry[],
  id: number,
  update: Pick<PushDeliveryEntry, "outcome" | "detail"> & {
    httpStatus?: number;
  },
): PushDeliveryEntry[] {
  return entries.map((entry) =>
    entry.id === id ? { ...entry, ...update } : entry,
  );
}

export function formatPushDeliveryPayload(payload: string): {
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
