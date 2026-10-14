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

/** Surface the push service's response, without logging a lengthy success message. */
export function describePushServiceError(
  response: Pick<Response, "status" | "statusText">,
  body: string,
): string {
  const status = `HTTP ${response.status}${response.statusText.trim() ? ` ${response.statusText.trim()}` : ""}`;
  let detail = body.trim();
  if (detail) {
    try {
      const parsed: unknown = JSON.parse(detail);
      if (typeof parsed === "string") {
        detail = parsed;
      } else if (
        parsed
        && typeof parsed === "object"
        && !Array.isArray(parsed)
      ) {
        const record = parsed as Record<string, unknown>;
        const text = [
          record.message,
          record.error_description,
          record.detail,
          record.error,
        ].find((value) => typeof value === "string" && value.trim());
        if (typeof text === "string") detail = text;
      }
    } catch {
      // Non-JSON responses are also useful when debugging a rejected push.
    }
  }
  if (!detail) {
    const hint =
      response.status === 401 || response.status === 403
        ? "Check the VAPID credentials."
        : response.status === 404 || response.status === 410
          ? "The endpoint may be invalid or expired. Register again if needed."
          : response.status === 413
            ? "The payload is too large."
            : response.status === 429
              ? "Rate limited. Try again later."
              : response.status >= 500
                ? "Push service error. Try again later."
                : "Check the endpoint and request payload.";
    return `${status}. ${hint}`;
  }
  const normalized = detail.replace(/\s+/g, " ").trim();
  return `${status}: ${normalized.slice(0, 1000)}${normalized.length > 1000 ? "…" : ""}`;
}

/** Fetch failures don't expose a reliable HTTP result (e.g. CORS or timeout). */
export function describePushNetworkError(error: unknown): string {
  if (
    error instanceof Error
    && (error.name === "TimeoutError" || error.name === "AbortError")
  ) {
    return "Request timed out. Check the push service and try again.";
  }
  return "No response from the push service. Check the network, endpoint and browser CORS settings.";
}
