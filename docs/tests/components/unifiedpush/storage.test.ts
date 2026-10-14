import { describe, expect, it, vi } from "vitest";
import {
  EMPTY_CREDENTIALS,
  parseStoredCredentials,
  persistCredentials,
  STORAGE_KEY,
} from "#/components/unifiedpush/storage";

describe("UnifiedPush browser storage", () => {
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
