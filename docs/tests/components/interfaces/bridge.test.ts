import { describe, expect, it, vi } from "vitest";
import {
  decodeTag,
  describeError,
  detectInterfaces,
  type InterfaceHost,
  isValidBrightness,
  type NfcReader,
  readBattery,
  readBrightness,
  requestVibration,
  setBrightness,
} from "#/components/interfaces/bridge";

const batteryStatus = {
  level: 0.62,
  percentage: 62,
  charging: true,
  chargingType: "usb",
  voltage: 3.8,
  temperature: 27.3,
  health: "good",
};

class TestNfcReader extends EventTarget implements NfcReader {
  onreadingerror: ((error: unknown) => void) | null = null;
  async scan() {}
}

describe("WebView interface detection", () => {
  it("reports only exposed and callable device APIs", () => {
    const fullHost: InterfaceHost = {
      WebviewKioskBatteryInterface: { getBatteryStatus: () => "{}" },
      WebviewKioskBrightnessInterface: {
        getBrightness: () => 10,
        setBrightness: () => {},
      },
      WebviewKioskNfcInterface: {
        openClient: () => "client",
        scanForClient: () => true,
      },
      __webviewKioskWebNfcInstalled: true,
      NDEFReader: TestNfcReader,
      navigator: { vibrate: () => true },
      WebviewKioskBlobInterface: {
        startDownload: () => true,
        appendChunk: () => true,
        finishDownload: () => true,
      },
    };
    expect(detectInterfaces(fullHost)).toEqual({
      battery: true,
      brightness: true,
      nfc: true,
      vibration: true,
      blob: true,
    });
    expect(detectInterfaces({})).toEqual({
      battery: false,
      brightness: false,
      nfc: false,
      vibration: false,
      blob: false,
    });
    expect(
      detectInterfaces({ ...fullHost, __webviewKioskWebNfcInstalled: false })
        .nfc,
    ).toBe(false);
    expect(detectInterfaces({ ...fullHost, NDEFReader: undefined }).nfc).toBe(
      false,
    );
    expect(
      detectInterfaces({ ...fullHost, WebviewKioskNfcInterface: undefined })
        .nfc,
    ).toBe(false);
    expect(detectInterfaces({ ...fullHost, navigator: {} }).vibration).toBe(
      false,
    );
  });
});

describe("battery bridge", () => {
  it("parses real battery status fields", () => {
    expect(
      readBattery({
        WebviewKioskBatteryInterface: {
          getBatteryStatus: () => JSON.stringify(batteryStatus),
        },
      }),
    ).toEqual(batteryStatus);
  });

  it("rejects missing, malformed, and incomplete responses", () => {
    expect(() => readBattery({})).toThrow("Battery bridge not available");
    for (const raw of [
      "not JSON",
      "[]",
      "null",
      "{}",
      JSON.stringify({ ...batteryStatus, percentage: "62" }),
    ]) {
      expect(() =>
        readBattery({
          WebviewKioskBatteryInterface: { getBatteryStatus: () => raw },
        }),
      ).toThrow();
    }
  });
});

describe("brightness bridge", () => {
  it("reads numeric and string values and supports the system default", () => {
    expect(
      readBrightness({
        WebviewKioskBrightnessInterface: {
          getBrightness: () => "54",
          setBrightness: () => {},
        },
      }),
    ).toBe(54);
    expect(
      readBrightness({
        WebviewKioskBrightnessInterface: {
          getBrightness: () => -1,
          setBrightness: () => {},
        },
      }),
    ).toBe(-1);
  });

  it("rejects unavailable, fractional, or out-of-range brightness", () => {
    expect(() => readBrightness({})).toThrow("Brightness bridge not available");
    for (const value of [-2, 101, 12.5, "foo"]) {
      expect(() =>
        readBrightness({
          WebviewKioskBrightnessInterface: {
            getBrightness: () => value,
            setBrightness: () => {},
          },
        }),
      ).toThrow("outside -1 to 100");
    }
  });

  it("validates before invoking a brightness change", () => {
    const setter = vi.fn();
    const host: InterfaceHost = {
      WebviewKioskBrightnessInterface: {
        getBrightness: () => 20,
        setBrightness: setter,
      },
    };
    for (const value of [-1, 0, 50, 100]) {
      setBrightness(host, value);
    }
    expect(setter.mock.calls).toEqual([[-1], [0], [50], [100]]);
    for (const value of [-2, 101, 2.5, Number.NaN]) {
      expect(() => setBrightness(host, value)).toThrow("between -1 and 100");
    }
    expect(setter).toHaveBeenCalledTimes(4);
    expect(() => setBrightness({}, 25)).toThrow(
      "Brightness bridge not available",
    );
  });

  it("only allows valid integer input", () => {
    for (const value of ["-1", "0", "50", "100"]) {
      expect(isValidBrightness(value)).toBe(true);
    }
    for (const value of ["", "-2", "101", "1.5", "NaN", "0x20", " 10 "]) {
      expect(isValidBrightness(value)).toBe(false);
    }
  });
});

describe("vibration API", () => {
  it("requests one short pulse and checks the return value", () => {
    const vibrate = vi.fn(() => true);
    requestVibration({ navigator: { vibrate } });
    expect(vibrate).toHaveBeenCalledExactlyOnceWith(150);
    expect(() => requestVibration({})).toThrow("not available");
    expect(() =>
      requestVibration({ navigator: { vibrate: () => false } }),
    ).toThrow("rejected");
  });
});

describe("NFC tag decoding", () => {
  it("preserves serial numbers and multiple decoded record types", async () => {
    const event = Object.assign(new Event("reading"), {
      serialNumber: "A1B2",
      message: {
        records: [
          { recordType: "text", text: async () => "Hello" },
          { recordType: "url", text: async () => "https://example.com" },
        ],
      },
    });
    expect(await decodeTag(event)).toEqual({
      serialNumber: "A1B2",
      records: [
        { id: 0, type: "text", content: "Hello" },
        { id: 1, type: "url", content: "https://example.com" },
      ],
    });
  });

  it("decodes URI prefixes and distinguishes binary records", async () => {
    const uriPayload = new Uint8Array([
      4,
      ...new TextEncoder().encode("example.com/device"),
    ]);
    const event = Object.assign(new Event("reading"), {
      message: {
        records: [
          { recordType: "url", data: uriPayload.buffer },
          {
            recordType: "mime",
            mediaType: "application/octet-stream",
            data: new DataView(new Uint8Array([0, 1, 2, 3]).buffer),
          },
          { recordType: "url", data: new Uint8Array([255, 65]).buffer },
        ],
      },
    });
    expect((await decodeTag(event)).records).toEqual([
      { id: 0, type: "url", content: "https://example.com/device" },
      { id: 1, type: "mime", content: "Binary payload (4 bytes)" },
      { id: 2, type: "url", content: "(unknown URI prefix)" },
    ]);
  });

  it("handles empty and unreadable records without failing the scan", async () => {
    expect(await decodeTag(new Event("reading"))).toEqual({
      serialNumber: "Unknown",
      records: [],
    });
    const event = Object.assign(new Event("reading"), {
      message: {
        records: [
          { mediaType: "application/octet-stream" },
          {
            recordType: "text",
            text: async () => {
              throw new Error("bad text");
            },
          },
          { recordType: "text", text: async () => "x".repeat(1_500) },
        ],
      },
    });
    const result = await decodeTag(event);
    expect(result.records).toEqual([
      { id: 0, type: "application/octet-stream", content: "(no text decoder)" },
      { id: 1, type: "text", content: "(unable to decode record)" },
      { id: 2, type: "text", content: "x".repeat(1_000) },
    ]);
  });
});

describe("bridge errors", () => {
  it("reports Error messages and non-error throws", () => {
    expect(describeError(new Error("Denied"))).toBe("Denied");
    expect(describeError("Failed")).toBe("Failed");
  });
});
