import { Battery, Download, Nfc, Sun, Vibrate } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
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
  type BatteryStatus,
  decodeTag,
  describeError,
  type InterfaceHost,
  isValidBrightness,
  isValidVibrationDuration,
  type NfcReader,
  type NfcReadingEvent,
  requestVibration,
  type ScannedTag,
  setBrightness,
  VIBRATION_DEFAULT_MS,
  VIBRATION_MAX_MS,
  VIBRATION_MIN_MS,
} from "./bridge";
import { type DeviceSnapshot, subscribeDeviceSnapshots } from "./polling";

function currentHost(): InterfaceHost {
  return window as Window & InterfaceHost;
}

function Availability({ available }: { available: boolean }) {
  return (
    <span
      className={`ml-auto shrink-0 rounded-full border px-2.5 py-1 text-xs font-medium ${
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
  setting,
  available,
  icon: Icon,
}: {
  title: string;
  description: string;
  setting: string;
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
      <CardDescription className="space-y-1">
        <p>{description}</p>
        <p className="text-xs leading-relaxed">
          Enable{" "}
          <code className="font-mono text-foreground">
            {setting.split(/(?<=[._])/).map((part) => (
              <span key={part}>
                {part}
                <wbr />
              </span>
            ))}
          </code>
        </p>
      </CardDescription>
    </CardHeader>
  );
}

function BatteryPanel({
  available,
  reading,
}: {
  available: boolean;
  reading: BatteryStatus | null;
}) {
  return (
    <Card>
      <PanelHeading
        title="Battery"
        description="Live battery metrics."
        setting="js_scripts.enable_battery_api"
        available={available}
        icon={Battery}
      />
      <CardContent>
        {reading ? (
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
        ) : (
          <p className="text-sm text-muted-foreground">No reading</p>
        )}
      </CardContent>
    </Card>
  );
}

function BrightnessPanel({
  available,
  brightness,
}: {
  available: boolean;
  brightness: number | null;
}) {
  const [value, setValue] = useState("");
  const initialized = useRef(false);

  useEffect(() => {
    // Prefill only once. Subsequent polls only update the current reading.
    if (!initialized.current && brightness !== null) {
      initialized.current = true;
      setValue(String(brightness));
    }
  }, [brightness]);

  function applyBrightness(target: number) {
    try {
      setBrightness(currentHost(), target);
      toast.info(
        target === -1
          ? "System brightness requested."
          : "Brightness requested.",
      );
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  function handleSet() {
    if (!isValidBrightness(value)) {
      toast.warning("Enter an integer from -1 to 100.");
      return;
    }
    applyBrightness(Number(value));
  }

  return (
    <Card>
      <PanelHeading
        title="Brightness"
        description="Auto uses the system default (-1)."
        setting="js_scripts.enable_brightness_api"
        available={available}
        icon={Sun}
      />
      <CardContent className="space-y-3">
        <p className="text-sm text-muted-foreground">
          Current:{" "}
          {brightness === null
            ? "No reading"
            : brightness === -1
              ? "System default"
              : `${brightness}%`}
        </p>
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-end gap-2">
          <div className="min-w-0 space-y-2">
            <Label htmlFor="brightness-value">Brightness (-1 to 100)</Label>
            <div className="relative">
              <Input
                id="brightness-value"
                type="number"
                min={-1}
                max={100}
                step={1}
                className="pr-15 [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                value={value}
                onChange={(event) => {
                  initialized.current = true;
                  setValue(event.target.value);
                }}
              />
              <Button
                type="button"
                size="sm"
                variant="ghost"
                className="absolute top-1/2 right-1 h-7 -translate-y-1/2 rounded-sm px-2 text-xs"
                title="Set system brightness (-1)"
                disabled={!available}
                onClick={() => applyBrightness(-1)}
              >
                Auto
              </Button>
            </div>
          </div>
          <Button
            disabled={!available || !isValidBrightness(value)}
            onClick={handleSet}
          >
            Set
          </Button>
        </div>
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

  useEffect(() => {
    // Client scans persist in the native bridge across SPA route changes unless
    // they are explicitly aborted. Never leave NFC scanning in the background.
    return () => {
      if (session.current) stopScanSession(session.current);
      session.current = null;
      toast.dismiss("nfc-scan");
    };
  }, []);

  function handleStop() {
    if (session.current) stopScanSession(session.current);
    session.current = null;
    setScanning("idle");
    toast.info("Scan stopped.", { id: "nfc-scan" });
  }

  async function handleStart() {
    if (session.current) return;
    const Reader = currentHost().NDEFReader;
    if (!Reader || !available) {
      toast.error("NFC unavailable. Enable device.allow_nfc.");
      return;
    }

    let current: ScanSession | null = null;
    try {
      const reader = new Reader();
      const abort = new AbortController();
      const onReading: EventListener = (event) => {
        void decodeTag(event as NfcReadingEvent)
          .then((tag) => {
            if (session.current !== current) return;
            const id = ++nextTagId.current;
            setTags((previous) => [{ ...tag, id }, ...previous].slice(0, 5));
          })
          .catch((cause: unknown) => {
            if (session.current === current)
              toast.error(describeError(cause), { id: "nfc-scan" });
          });
      };
      reader.addEventListener("reading", onReading);
      // A failed scan can emit readingerror and reject scan(). Buffer the
      // first event until startup settles so it produces only one toast.
      let started = false;
      let startupReadingError: unknown = null;
      reader.onreadingerror = (cause) => {
        if (session.current !== current) return;
        if (!started) {
          startupReadingError = cause;
        } else {
          toast.error(describeError(cause), { id: "nfc-scan" });
        }
      };
      current = { reader, abort, onReading };
      session.current = current;
      setScanning("starting");
      await reader.scan({ signal: abort.signal });
      if (session.current === current) {
        started = true;
        setScanning("active");
        if (startupReadingError !== null) {
          toast.error(describeError(startupReadingError), { id: "nfc-scan" });
        } else {
          toast.success("Scanning. Hold a tag near your device.", {
            id: "nfc-scan",
          });
        }
      }
    } catch (cause) {
      if (current && session.current === current) {
        stopScanSession(current);
        session.current = null;
        setScanning("idle");
        toast.error(describeError(cause), { id: "nfc-scan" });
      } else if (!current) {
        toast.error(describeError(cause), { id: "nfc-scan" });
      }
    }
  }

  return (
    <Card>
      <PanelHeading
        title="NFC"
        description="Read NFC tags without writing to them."
        setting="device.allow_nfc"
        available={available}
        icon={Nfc}
      />
      <CardContent className="space-y-4">
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            variant="ghost"
            disabled={tags.length === 0}
            onClick={() => setTags([])}
          >
            Clear tags
          </Button>
          {scanning === "idle" ? (
            <Button disabled={!available} onClick={() => void handleStart()}>
              Start scan
            </Button>
          ) : (
            <Button variant="outline" onClick={handleStop}>
              Stop scan
            </Button>
          )}
        </div>
        {tags.length > 0 && (
          <div aria-live="polite" className="space-y-3">
            <h3 className="text-sm font-medium">Recent tags</h3>
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
      toast.info("Download requested. Check Downloads.");
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Blob downloads"
        description="Download a sample text file."
        setting="web_engine.allow_file_download"
        available={available}
        icon={Download}
      />
      <CardContent className="flex justify-end">
        <Button disabled={!available} onClick={handleDownload}>
          Download sample
        </Button>
      </CardContent>
    </Card>
  );
}

function VibrationPanel({ available }: { available: boolean }) {
  const [duration, setDuration] = useState(String(VIBRATION_DEFAULT_MS));

  function handleTest() {
    try {
      requestVibration(currentHost(), Number(duration));
      toast.info(`Vibration requested (${duration} ms).`);
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Vibration"
        description="Test a pulse."
        setting="js_scripts.enable_vibration_api"
        available={available}
        icon={Vibrate}
      />
      <CardContent>
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-end gap-2">
          <div className="min-w-0 space-y-2">
            <Label htmlFor="vibration-duration">
              Duration ({VIBRATION_MIN_MS}–{VIBRATION_MAX_MS} ms)
            </Label>
            <Input
              id="vibration-duration"
              type="number"
              min={VIBRATION_MIN_MS}
              max={VIBRATION_MAX_MS}
              step={1}
              value={duration}
              onChange={(event) => setDuration(event.target.value)}
            />
          </div>
          <Button
            disabled={!available || !isValidVibrationDuration(duration)}
            onClick={handleTest}
          >
            Vibrate
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export default function InterfaceTester() {
  const [latest, setLatest] = useState<{
    snapshot: DeviceSnapshot;
    refreshedAt: Date;
  } | null>(null);

  useEffect(
    () =>
      subscribeDeviceSnapshots(currentHost, (snapshot) => {
        setLatest({ snapshot, refreshedAt: new Date() });
      }),
    [],
  );

  const snapshot = latest?.snapshot;

  return (
    <div className="space-y-5">
      <p className="text-right text-xs text-muted-foreground">
        Refreshed at{" "}
        {latest ? (
          <time dateTime={latest.refreshedAt.toISOString()}>
            {latest.refreshedAt.toLocaleTimeString()}
          </time>
        ) : (
          "—"
        )}
      </p>
      <div className="grid min-w-0 gap-5 lg:grid-cols-2">
        <BatteryPanel
          available={snapshot?.availability.battery ?? false}
          reading={snapshot?.battery ?? null}
        />
        <BrightnessPanel
          available={snapshot?.availability.brightness ?? false}
          brightness={snapshot?.brightness ?? null}
        />
        <NfcPanel available={snapshot?.availability.nfc ?? false} />
        <VibrationPanel available={snapshot?.availability.vibration ?? false} />
        <BlobPanel available={snapshot?.availability.blob ?? false} />
      </div>
    </div>
  );
}
