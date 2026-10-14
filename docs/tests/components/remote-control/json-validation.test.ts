import * as v from "valibot";
import { describe, expect, it } from "vitest";
import {
  isJsonObject,
  JsonObjectSchema,
  parseJsonObject,
} from "#/components/remote-control/json-validation";

const invalidJsonMessage = "Invalid JSON.";
const invalidObjectMessage = "Expected a JSON object.";

describe("shared JSON object validation", () => {
  it("rejects arrays and primitives before record parsing", () => {
    for (const value of [[], ["item"], null, 123, false, "text"]) {
      expect(v.safeParse(JsonObjectSchema, value).success).toBe(false);
      expect(isJsonObject(value)).toBe(false);
      expect(() =>
        parseJsonObject(
          JSON.stringify(value),
          invalidJsonMessage,
          invalidObjectMessage,
        ),
      ).toThrow(invalidObjectMessage);
    }
  });

  it("preserves ordinary objects, unknown keys and nested arrays", () => {
    const message = {
      command: "toast",
      data: { targets: ["device-a", "device-b"] },
      futureField: { enabled: true },
    };
    expect(isJsonObject(message)).toBe(true);
    expect(v.parse(JsonObjectSchema, message)).toEqual(message);
    expect(
      parseJsonObject(
        JSON.stringify(message),
        invalidJsonMessage,
        invalidObjectMessage,
      ),
    ).toEqual(message);
    expect(() =>
      parseJsonObject("{", invalidJsonMessage, invalidObjectMessage),
    ).toThrow(invalidJsonMessage);
  });
});
