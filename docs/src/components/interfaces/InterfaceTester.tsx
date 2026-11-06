import { Battery, Download, Nfc, RotateCcw, Sun, Vibrate } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "#/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "#/components/ui/card";
import { Input } from "#/components/ui/input";
import { Label } from "#/components/ui/label";
import {
  type AvailableInterfaces,
  type BatteryStatus,
  decodeTag,
  describeError,
  detectInterfaces,
  type InterfaceHost,
  isValidBrightness,
  type NfcReader,
  type NfcReadingEvent,
  readBattery,
  readBrightness,
  requestVibration,
  type ScannedTag,
  setBrightness,
} from "./bridge";

function currentHost(): InterfaceHost {
  return window as Window & InterfaceHost;
}

function Availability({ available }: { available: boolean }) {
  return (
    <span
      className={`shrink-0 rounded-full border px-2.5 py-1 text-xs font-medium ${
        available
          ? "border-success/40 text-success"
          : "border-border text-muted-foreground"
      }`}
    >
      {available ? "Detected" : "Not detected"}
    </span>
  );
}

function PanelHeading({
  title,
  description,
  available,
  icon: Icon,
}: {
  title: string;
  description: string;
  available: boolean;
  icon: typeof Battery;
}) {
  return (
    <CardHeader>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <CardTitle className="flex items-center gap-2">
          <Icon aria-hidden="true" className="size-5" />
          {title}
        </CardTitle>
        <Availability available={available} />
      </div>
      <CardDescription>{description}</CardDescription>
    </CardHeader>
  );
}

function Result({
  message,
  error = false,
}: {
  message: string;
  error?: boolean;
}) {
  return (
    <p
      role={error ? "alert" : "status"}
      className={`text-sm ${error ? "text-destructive" : "text-muted-foreground"}`}
    >
      {message}
    </p>
  );
}

function BatteryPanel({ available }: { available: boolean }) {
  const [reading, setReading] = useState<BatteryStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleRead() {
    try {
      setReading(readBattery(currentHost()));
      setError(null);
    } catch (cause) {
      setReading(null);
      setError(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Battery"
        description="Read charge, health, voltage, and temperature from the Android battery bridge."
        available={available}
        icon={Battery}
      />
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleRead}>
          Read battery status
        </Button>
        {error && <Result message={error} error />}
        {reading && (
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-lg border p-4 text-sm sm:grid-cols-3">
            {(
              [
                [
                  "Charge",
                  reading.percentage < 0 ? "Unknown" : `${reading.percentage}%`,
                ],
                [
                  "Level",
                  reading.level < 0 ? "Unknown" : String(reading.level),
                ],
                ["Charging", reading.charging ? "Yes" : "No"],
                ["Source", reading.chargingType],
                [
                  "Voltage",
                  reading.voltage < 0 ? "Unknown" : `${reading.voltage} V`,
                ],
                ["Temperature", `${reading.temperature} °C`],
                ["Health", reading.health],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="mt-0.5 font-medium">{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  );
}

function BrightnessPanel({ available }: { available: boolean }) {
  const [value, setValue] = useState("-1");
  const [current, setCurrent] = useState<number | null>(null);
  const [initial, setInitial] = useState<number | null>(null);
  const [changed, setChanged] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleRead() {
    try {
      const brightness = readBrightness(currentHost());
      setCurrent(brightness);
      setValue(String(brightness));
      setInitial((old) => old ?? brightness);
      setMessage(null);
      setError(null);
    } catch (cause) {
      setError(describeError(cause));
    }
  }

  function handleSet() {
    if (!isValidBrightness(value)) {
      setError("Enter an integer from -1 to 100.");
      return;
    }
    try {
      const host = currentHost();
      const before = readBrightness(host);
      setBrightness(host, Number(value));
      setInitial((old) => old ?? before);
      setChanged(true);
      setCurrent(null); // The Android setter runs asynchronously on the UI thread.
      setMessage(
        "Brightness change requested. Read again to verify the value.",
      );
      setError(null);
    } catch (cause) {
      setError(describeError(cause));
      setMessage(null);
    }
  }

  function handleRestore() {
    if (initial === null) return;
    try {
      setBrightness(currentHost(), initial);
      setValue(String(initial));
      setCurrent(null);
      setChanged(false);
      setMessage(
        "Original brightness requested. Read again to verify the value.",
      );
      setError(null);
    } catch (cause) {
      setError(describeError(cause));
      setMessage(null);
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Brightness"
        description="Read or set the kiosk window brightness. -1 follows the system setting; 0 is very dim."
        available={available}
        icon={Sun}
      />
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="w-32 space-y-2">
            <Label htmlFor="brightness-value">Brightness (-1 to 100)</Label>
            <Input
              id="brightness-value"
              type="number"
              min={-1}
              max={100}
              step={1}
              value={value}
              onChange={(event) => setValue(event.target.value)}
            />
          </div>
          <Button variant="outline" disabled={!available} onClick={handleRead}>
            Read
          </Button>
          <Button
            disabled={!available || !isValidBrightness(value)}
            onClick={handleSet}
          >
            Set brightness
          </Button>
          {changed && initial !== null && (
            <Button variant="outline" onClick={handleRestore}>
              <RotateCcw aria-hidden="true" />
              Restore {initial === -1 ? "system default" : `${initial}%`}
            </Button>
          )}
        </div>
        {current !== null && (
          <Result
            message={`Current window brightness: ${current === -1 ? "system default" : `${current}%`}.`}
          />
        )}
        {message && <Result message={message} />}
        {error && <Result message={error} error />}
      </CardContent>
    </Card>
  );
}

type ScanSession = {
  reader: NfcReader;
  abort: AbortController;
  onReading: EventListener;
};

function stopScanSession(session: ScanSession) {
  session.reader.removeEventListener("reading", session.onReading);
  session.reader.onreadingerror = null;
  session.abort.abort();
  try {
    session.reader.stopScan?.();
  } catch {
    // Route cleanup must still succeed if the Android scan session has ended.
  }
}

function NfcPanel({ available }: { available: boolean }) {
  const session = useRef<ScanSession | null>(null);
  const [scanning, setScanning] = useState<"idle" | "starting" | "active">(
    "idle",
  );
  const [tags, setTags] = useState<(ScannedTag & { id: number })[]>([]);
  const nextTagId = useRef(0);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Client scans persist in the native bridge across SPA route changes unless
    // they are explicitly aborted. Never leave NFC scanning in the background.
    return () => {
      if (session.current) stopScanSession(session.current);
      session.current = null;
    };
  }, []);

  function handleStop() {
    if (session.current) stopScanSession(session.current);
    session.current = null;
    setScanning("idle");
    setMessage("Scan stopped.");
  }

  async function handleStart() {
    if (session.current) return;
    const Reader = currentHost().NDEFReader;
    if (!Reader || !available) {
      setError(
        "NFC WebView bridge not detected. Enable Allow NFC in Device settings.",
      );
      return;
    }

    let current: ScanSession | null = null;
    try {
      const reader = new Reader();
      const abort = new AbortController();
      const onReading: EventListener = (event) => {
        void decodeTag(event as NfcReadingEvent).then((tag) => {
          if (session.current !== current) return;
          const id = ++nextTagId.current;
          setTags((previous) => [{ ...tag, id }, ...previous].slice(0, 5));
          setError(null);
        });
      };
      reader.addEventListener("reading", onReading);
      reader.onreadingerror = (cause) => setError(describeError(cause));
      current = { reader, abort, onReading };
      session.current = current;
      setScanning("starting");
      setError(null);
      setMessage("Starting NFC scan…");
      await reader.scan({ signal: abort.signal });
      if (session.current === current) {
        setScanning("active");
        setMessage("Scanning. Hold an NFC tag near your device.");
      }
    } catch (cause) {
      if (current && session.current === current) {
        stopScanSession(current);
        session.current = null;
        setScanning("idle");
        setMessage(null);
        setError(describeError(cause));
      } else if (!current) {
        setError(describeError(cause));
      }
    }
  }

  return (
    <Card>
      <PanelHeading
        title="NFC"
        description="Scan nearby NDEF tags through the Web NFC compatibility bridge. This test never writes to a tag."
        available={available}
        icon={Nfc}
      />
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {scanning === "idle" ? (
            <Button disabled={!available} onClick={() => void handleStart()}>
              Start tag scan
            </Button>
          ) : (
            <Button variant="outline" onClick={handleStop}>
              Stop scan
            </Button>
          )}
          <Button
            variant="ghost"
            disabled={tags.length === 0}
            onClick={() => setTags([])}
          >
            Clear readings
          </Button>
        </div>
        {message && <Result message={message} />}
        {error && <Result message={error} error />}
        {tags.length > 0 && (
          <div aria-live="polite" className="space-y-3">
            <h3 className="text-sm font-medium">Recent tags (up to 5)</h3>
            <ol className="space-y-3">
              {tags.map((tag) => (
                <li
                  key={tag.id}
                  className="min-w-0 space-y-2 rounded-lg border p-3 text-sm"
                >
                  <p className="break-all font-medium">
                    Serial number: {tag.serialNumber}
                  </p>
                  {tag.records.length === 0 ? (
                    <p className="text-muted-foreground">
                      No NDEF records found.
                    </p>
                  ) : (
                    <ul className="space-y-2">
                      {tag.records.map((record) => (
                        <li key={record.id}>
                          <span className="text-muted-foreground">
                            {record.type}:
                          </span>{" "}
                          <span className="break-all whitespace-pre-wrap">
                            {record.content}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ol>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function BlobPanel({ available }: { available: boolean }) {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleDownload() {
    try {
      const blob = new Blob(["WebView Kiosk Blob download test\n"], {
        type: "text/plain",
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "webview-kiosk-test.txt";
      document.body.append(link);
      link.click();
      link.remove();
      // Leave the URL available while Android handles the download request.
      window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
      setMessage(
        "Download requested. Verify the file in Downloads and confirm any prompts.",
      );
      setError(null);
    } catch (cause) {
      setError(describeError(cause));
      setMessage(null);
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Blob downloads"
        description="Request a small text-file download to test the app's Blob transfer bridge."
        available={available}
        icon={Download}
      />
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleDownload}>
          Download test file
        </Button>
        {message && <Result message={message} />}
        {error && <Result message={error} error />}
      </CardContent>
    </Card>
  );
}

function VibrationPanel({ available }: { available: boolean }) {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleTest() {
    try {
      requestVibration(currentHost());
      setMessage("150 ms vibration requested. Confirm the device vibrated.");
      setError(null);
    } catch (cause) {
      setError(describeError(cause));
      setMessage(null);
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Vibration"
        description="Use the standard navigator.vibrate() API with a single short pulse."
        available={available}
        icon={Vibrate}
      />
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleTest}>
          Test vibration (150 ms)
        </Button>
        {message && <Result message={message} />}
        {error && <Result message={error} error />}
      </CardContent>
    </Card>
  );
}

export default function InterfaceTester() {
  const [availability, setAvailability] = useState<AvailableInterfaces | null>(
    null,
  );

  function refresh() {
    setAvailability(detectInterfaces(currentHost()));
  }

  useEffect(() => {
    setAvailability(detectInterfaces(currentHost()));
  }, []);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border p-4">
        <p className="max-w-2xl text-sm text-muted-foreground">
          Detection checks whether the APIs are exposed in this browser; the
          device can still reject a request. Enable Battery API, Brightness API,
          and Vibration API under JS Scripts, Allow NFC under Device, and Allow
          File Download under Web Engine. Reload after changing settings.
        </p>
        <Button variant="outline" onClick={refresh}>
          Refresh detection
        </Button>
      </div>
      <div className="grid min-w-0 gap-5 lg:grid-cols-2">
        <BatteryPanel available={availability?.battery ?? false} />
        <BrightnessPanel available={availability?.brightness ?? false} />
        <NfcPanel available={availability?.nfc ?? false} />
        <VibrationPanel available={availability?.vibration ?? false} />
        <BlobPanel available={availability?.blob ?? false} />
      </div>
    </div>
  );
}
