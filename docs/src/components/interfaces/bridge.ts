/** Device APIs injected into pages loaded by WebView Kiosk when enabled. */
export interface BatteryStatus {
  level: number;
  percentage: number;
  charging: boolean;
  chargingType: string;
  voltage: number;
  temperature: number;
  health: string;
}

export interface NfcReadingRecord {
  recordType?: string;
  mediaType?: string | null;
  text?: () => Promise<string>;
  data?: ArrayBuffer | ArrayBufferView;
}

export interface NfcReadingEvent extends Event {
  serialNumber?: string;
  message?: { records?: NfcReadingRecord[] };
}

export interface NfcReader extends EventTarget {
  scan(options?: { signal?: AbortSignal }): Promise<void>;
  stopScan?(): void; // Provided by the WebView Kiosk compatibility bridge.
  onreadingerror: ((error: unknown) => void) | null;
}

export interface InterfaceHost {
  WebviewKioskBatteryInterface?: { getBatteryStatus(): string };
  WebviewKioskBrightnessInterface?: {
    getBrightness(): number | string;
    setBrightness(value: number): void;
  };
  WebviewKioskNfcInterface?: { openClient(): string; scanForClient(): boolean };
  WebviewKioskBlobInterface?: {
    startDownload(
      transferId: string,
      mimeType: string,
      filename: string,
    ): boolean;
    appendChunk(transferId: string, base64Chunk: string): boolean;
    finishDownload(transferId: string): boolean;
  };
  __webviewKioskWebNfcInstalled?: boolean;
  NDEFReader?: new () => NfcReader;
  navigator?: { vibrate?: (duration: number) => boolean };
}

export type AvailableInterfaces = Record<
  "battery" | "brightness" | "nfc" | "vibration" | "blob",
  boolean
>;

export function detectInterfaces(host: InterfaceHost): AvailableInterfaces {
  return {
    battery:
      typeof host.WebviewKioskBatteryInterface?.getBatteryStatus === "function",
    brightness:
      typeof host.WebviewKioskBrightnessInterface?.getBrightness === "function"
      && typeof host.WebviewKioskBrightnessInterface?.setBrightness
        === "function",
    nfc:
      typeof host.WebviewKioskNfcInterface?.openClient === "function"
      && typeof host.WebviewKioskNfcInterface?.scanForClient === "function"
      && host.__webviewKioskWebNfcInstalled === true
      && typeof host.NDEFReader === "function",
    vibration: typeof host.navigator?.vibrate === "function",
    blob:
      typeof host.WebviewKioskBlobInterface?.startDownload === "function"
      && typeof host.WebviewKioskBlobInterface?.appendChunk === "function"
      && typeof host.WebviewKioskBlobInterface?.finishDownload === "function",
  };
}

export function readBattery(host: InterfaceHost): BatteryStatus {
  const raw = host.WebviewKioskBatteryInterface?.getBatteryStatus();
  if (typeof raw !== "string") {
    throw new Error(
      "Battery bridge not available. Enable Battery API in JS Scripts.",
    );
  }

  const data: unknown = JSON.parse(raw);
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new Error("Battery bridge returned an invalid response.");
  }

  const battery = data as Record<keyof BatteryStatus, unknown>;
  if (
    !["level", "percentage", "voltage", "temperature"].every(
      (key) =>
        typeof battery[key as keyof BatteryStatus] === "number"
        && Number.isFinite(battery[key as keyof BatteryStatus]),
    )
    || typeof battery.charging !== "boolean"
    || typeof battery.chargingType !== "string"
    || typeof battery.health !== "string"
  ) {
    throw new Error("Battery bridge returned an invalid response.");
  }

  return battery as unknown as BatteryStatus;
}

export function readBrightness(host: InterfaceHost): number {
  const bridge = host.WebviewKioskBrightnessInterface;
  if (typeof bridge?.getBrightness !== "function") {
    throw new Error(
      "Brightness bridge not available. Enable Brightness API in JS Scripts.",
    );
  }
  const value = Number(bridge.getBrightness());
  if (!Number.isInteger(value) || value < -1 || value > 100) {
    throw new Error("Brightness bridge returned a value outside -1 to 100.");
  }
  return value;
}

export function isValidBrightness(value: string): boolean {
  if (!/^(?:-1|\d{1,3})$/.test(value)) return false;
  const parsed = Number(value);
  return parsed >= -1 && parsed <= 100;
}

export function setBrightness(host: InterfaceHost, value: number): void {
  if (!Number.isInteger(value) || value < -1 || value > 100) {
    throw new Error("Brightness must be an integer between -1 and 100.");
  }
  const bridge = host.WebviewKioskBrightnessInterface;
  if (typeof bridge?.setBrightness !== "function") {
    throw new Error(
      "Brightness bridge not available. Enable Brightness API in JS Scripts.",
    );
  }
  bridge.setBrightness(value);
}

export function requestVibration(host: InterfaceHost): void {
  const navigator = host.navigator;
  if (typeof navigator?.vibrate !== "function") {
    throw new Error("Vibration API is not available in this browser.");
  }
  if (!navigator.vibrate(150)) {
    throw new Error("The browser rejected the vibration request.");
  }
}

export interface ScannedTag {
  serialNumber: string;
  records: { id: number; type: string; content: string }[];
}

// NFC Forum NDEF URI record identifier codes (the most common schemes).
const URI_PREFIXES: Record<number, string> = {
  0: "",
  1: "http://www.",
  2: "https://www.",
  3: "http://",
  4: "https://",
  5: "tel:",
  6: "mailto:",
  7: "ftp://anonymous:anonymous@",
  8: "ftp://ftp.",
  9: "ftps://",
  10: "sftp://",
  11: "smb://",
  12: "nfs://",
  13: "ftp://",
  14: "dav://",
  15: "news:",
  16: "telnet://",
  17: "imap:",
  18: "rtsp://",
  19: "urn:",
  20: "pop:",
  21: "sip:",
  22: "sips:",
  23: "tftp:",
  24: "btspp://",
  25: "btl2cap://",
  26: "btgoep://",
  27: "tcpobex://",
  28: "irdaobex://",
  29: "file://",
  30: "urn:epc:id:",
  31: "urn:epc:tag:",
  32: "urn:epc:pat:",
  33: "urn:epc:raw:",
  34: "urn:epc:",
  35: "urn:nfc:",
};

function recordBytes(record: NfcReadingRecord): Uint8Array | null {
  if (record.data instanceof ArrayBuffer) return new Uint8Array(record.data);
  if (ArrayBuffer.isView(record.data)) {
    return new Uint8Array(
      record.data.buffer,
      record.data.byteOffset,
      record.data.byteLength,
    );
  }
  return null;
}

/** Decode through the WebView Kiosk bridge, including NFC Forum URI prefixes. */
export async function decodeTag(event: NfcReadingEvent): Promise<ScannedTag> {
  const records = await Promise.all(
    (event.message?.records ?? []).map(async (record, id) => {
      let content = "(no text decoder)";
      const bytes = recordBytes(record);
      if (record.recordType === "url" && bytes && bytes.length > 0) {
        const prefix = URI_PREFIXES[bytes[0]];
        content =
          prefix === undefined
            ? "(unknown URI prefix)"
            : (prefix + new TextDecoder().decode(bytes.subarray(1))).slice(
                0,
                1_000,
              );
      } else if (
        record.recordType === "mime"
        && bytes
        && !record.mediaType?.startsWith("text/")
        && record.mediaType !== "application/json"
      ) {
        content = `Binary payload (${bytes.length} bytes)`;
      } else if (typeof record.text === "function") {
        try {
          content = (await record.text()).slice(0, 1_000);
        } catch {
          content = "(unable to decode record)";
        }
      }
      return {
        id,
        type: record.recordType || record.mediaType || "unknown",
        content,
      };
    }),
  );
  return { serialNumber: event.serialNumber || "Unknown", records };
}

export function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
