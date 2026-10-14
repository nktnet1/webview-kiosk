import { describe, expect, it } from "vitest";
import {
  credentialsDiffer,
  parseCredentialJson,
  sanitizeStoredCredentials,
  serializeCredentialJson,
} from "#/components/remote-control/credential-json";
import { isJsonObject } from "#/components/remote-control/json-validation";
import { EMPTY_CREDENTIALS } from "#/components/unifiedpush/storage";

describe("Shared credential JSON backups", () => {
  it("round-trips only expected credential fields, including secrets", () => {
    const credentials = {
      ...EMPTY_CREDENTIALS,
      endpoint: "https://push.example.org/secret",
      auth: "secret-auth",
      vapidPrivateKey: "secret-vapid-key",
      extra: "should not appear",
    };
    const exported = serializeCredentialJson(credentials, EMPTY_CREDENTIALS);
    expect(JSON.parse(exported)).toEqual({
      ...EMPTY_CREDENTIALS,
      endpoint: credentials.endpoint,
      auth: credentials.auth,
      vapidPrivateKey: credentials.vapidPrivateKey,
    });
    expect(exported).not.toContain("extra");
    expect(parseCredentialJson(exported, EMPTY_CREDENTIALS)).toEqual(
      JSON.parse(exported),
    );
  });

  it("imports partial files without reusing an existing endpoint's secrets", () => {
    const imported = parseCredentialJson(
      '{"endpoint":"https://push.example.org/new","ignored":"value"}',
      EMPTY_CREDENTIALS,
    );
    expect(imported).toEqual({
      ...EMPTY_CREDENTIALS,
      endpoint: "https://push.example.org/new",
    });
    expect(
      credentialsDiffer(
        { ...EMPTY_CREDENTIALS, auth: "previous-secret" },
        imported,
        EMPTY_CREDENTIALS,
      ),
    ).toBe(true);
    expect(credentialsDiffer(imported, imported, EMPTY_CREDENTIALS)).toBe(
      false,
    );
  });

  it("uses the same credential schema for MQTT-style backups and local storage", () => {
    const template = { brokerUrl: "", username: "", password: "" };
    expect(
      parseCredentialJson(
        '{"brokerUrl":"wss://broker.example/mqtt","unknown":123}',
        template,
      ),
    ).toEqual({ ...template, brokerUrl: "wss://broker.example/mqtt" });
    expect(
      sanitizeStoredCredentials(
        { brokerUrl: "wss://broker.example/mqtt", password: null, extra: 1 },
        template,
      ),
    ).toEqual({ ...template, brokerUrl: "wss://broker.example/mqtt" });
    expect(sanitizeStoredCredentials([], template)).toEqual(template);
  });

  it("rejects arrays before Valibot can convert them into records", () => {
    for (const raw of ["[]", '["auth"]', '[{"auth":"secret"}]']) {
      expect(() => parseCredentialJson(raw, EMPTY_CREDENTIALS)).toThrow(
        /JSON object/,
      );
    }
    expect(isJsonObject([])).toBe(false);
    expect(isJsonObject(["auth"])).toBe(false);
    expect(isJsonObject({ auth: "secret" })).toBe(true);
  });

  it("rejects malformed JSON, non-string values, and unknown-only files", () => {
    expect(() => parseCredentialJson("not json", EMPTY_CREDENTIALS)).toThrow(
      /valid JSON/,
    );
    expect(() => parseCredentialJson("null", EMPTY_CREDENTIALS)).toThrow(
      /JSON object/,
    );
    expect(() => parseCredentialJson("[]", EMPTY_CREDENTIALS)).toThrow(
      /JSON object/,
    );
    expect(() => parseCredentialJson("{}", EMPTY_CREDENTIALS)).toThrow(
      /credential fields/,
    );
    expect(() =>
      parseCredentialJson('{"unknown":"value"}', EMPTY_CREDENTIALS),
    ).toThrow(/credential fields/);
    expect(() =>
      parseCredentialJson('{"auth":null}', EMPTY_CREDENTIALS),
    ).toThrow(/"auth" must be a string/);
    expect(() =>
      parseCredentialJson('{"endpoint":1}', EMPTY_CREDENTIALS),
    ).toThrow(/"endpoint" must be a string/);
    expect(() =>
      parseCredentialJson(
        '{"auth":"value","vapidPrivateKey":false}',
        EMPTY_CREDENTIALS,
      ),
    ).toThrow(/"vapidPrivateKey" must be a string/);
  });
});
