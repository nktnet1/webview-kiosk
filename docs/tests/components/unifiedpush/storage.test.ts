import { describe, expect, it, vi } from "vitest";
import {
  DEFAULT_VAPID_CONTACT,
  EMPTY_CREDENTIALS,
  initialPushCredentials,
  parseStoredCredentials,
  persistCredentials,
  STORAGE_KEY,
} from "#/components/unifiedpush/storage";

describe("UnifiedPush browser storage", () => {
  it("prefills the contact for new users without changing saved credentials", () => {
    expect(initialPushCredentials(null)).toEqual({
      ...EMPTY_CREDENTIALS,
      vapidSubject: DEFAULT_VAPID_CONTACT,
    });
    expect(
      initialPushCredentials('{"vapidSubject":"mailto:existing@example.com"}'),
    ).toMatchObject({ vapidSubject: "mailto:existing@example.com" });
    expect(
      initialPushCredentials('{"endpoint":"https://push.example.org"}'),
    ).toEqual({
      ...EMPTY_CREDENTIALS,
      endpoint: "https://push.example.org",
      vapidSubject: DEFAULT_VAPID_CONTACT,
    });
    expect(
      initialPushCredentials('{"vapidSubject":"https://example.com/contact"}'),
    ).toMatchObject({ vapidSubject: "https://example.com/contact" });
  });
  it("ignores malformed, unexpected and non-string credentials", () => {
    expect(parseStoredCredentials(null)).toEqual(EMPTY_CREDENTIALS);
    expect(parseStoredCredentials("invalid json")).toEqual(EMPTY_CREDENTIALS);
    expect(parseStoredCredentials("[]")).toEqual(EMPTY_CREDENTIALS);
    expect(
      parseStoredCredentials(
        '{"endpoint":"https://push.test","auth":9,"secret":"ignore"}',
      ),
    ).toEqual({ ...EMPTY_CREDENTIALS, endpoint: "https://push.test" });
  });

  it("persists only expected fields and removes them on clear", () => {
    const storage = { setItem: vi.fn(), removeItem: vi.fn() };
    const credentials = { ...EMPTY_CREDENTIALS, auth: "local-secret" };
    persistCredentials(storage, credentials);
    expect(storage.setItem).toHaveBeenCalledWith(
      STORAGE_KEY,
      JSON.stringify(credentials),
    );
    expect(parseStoredCredentials(storage.setItem.mock.calls[0][1])).toEqual(
      credentials,
    );
    persistCredentials(storage, EMPTY_CREDENTIALS);
    expect(storage.removeItem).toHaveBeenCalledWith(STORAGE_KEY);
  });
});
