import { Battery, Download, Nfc, RotateCcw, Sun, Vibrate } from "lucide-react";
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

function BatteryPanel({ available }: { available: boolean }) {
  const [reading, setReading] = useState<BatteryStatus | null>(null);

  function handleRead() {
    try {
      setReading(readBattery(currentHost()));
    } catch (cause) {
      setReading(null);
      toast.error(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Battery"
        description="Read battery metrics."
        setting="js_scripts.enable_battery_api"
        available={available}
        icon={Battery}
      />
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleRead}>
          Read battery
        </Button>
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
  const [initial, setInitial] = useState<number | null>(null);
  const [changed, setChanged] = useState(false);

  function handleRead() {
    try {
      const brightness = readBrightness(currentHost());
      setValue(String(brightness));
      setInitial((old) => old ?? brightness);
      toast.info(
        `Brightness: ${brightness === -1 ? "system default" : `${brightness}%`}`,
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
    try {
      const host = currentHost();
      const before = readBrightness(host);
      setBrightness(host, Number(value));
      setInitial((old) => old ?? before);
      setChanged(true);
      // Android applies brightness asynchronously on the UI thread.
      toast.info("Brightness requested. Read to verify.");
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  function handleRestore() {
    if (initial === null) return;
    try {
      setBrightness(currentHost(), initial);
      setValue(String(initial));
      setChanged(false);
      toast.info("Restore requested. Read to verify.");
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Brightness"
        description="-1 uses the system default."
        setting="js_scripts.enable_brightness_api"
        available={available}
        icon={Sun}
      />
      <CardContent className="space-y-2">
        <Label htmlFor="brightness-value">Brightness (-1 to 100)</Label>
        <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
          <Input
            id="brightness-value"
            type="number"
            min={-1}
            max={100}
            step={1}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            className="col-span-2 min-w-0 sm:w-28"
          />
          <Button
            className="w-full sm:w-auto"
            variant="outline"
            disabled={!available}
            onClick={handleRead}
          >
            Read
          </Button>
          <Button
            className="w-full sm:w-auto"
            disabled={!available || !isValidBrightness(value)}
            onClick={handleSet}
          >
            Set
          </Button>
          {changed && initial !== null && (
            <Button
              className="col-span-2 w-full sm:w-auto"
              variant="outline"
              onClick={handleRestore}
            >
              <RotateCcw aria-hidden="true" />
              Restore {initial === -1 ? "system default" : `${initial}%`}
            </Button>
          )}
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
            if (session.current === current) toast.error(describeError(cause));
          });
      };
      reader.addEventListener("reading", onReading);
      reader.onreadingerror = (cause) => toast.error(describeError(cause));
      current = { reader, abort, onReading };
      session.current = current;
      setScanning("starting");
      toast.info("Starting scan…", { id: "nfc-scan" });
      await reader.scan({ signal: abort.signal });
      if (session.current === current) {
        setScanning("active");
        toast.success("Scanning. Hold a tag near your device.", {
          id: "nfc-scan",
        });
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
        <div className="flex flex-wrap gap-2">
          {scanning === "idle" ? (
            <Button disabled={!available} onClick={() => void handleStart()}>
              Start scan
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
            Clear tags
          </Button>
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
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleDownload}>
          Download sample
        </Button>
      </CardContent>
    </Card>
  );
}

function VibrationPanel({ available }: { available: boolean }) {
  function handleTest() {
    try {
      requestVibration(currentHost());
      toast.info("Vibration requested (150 ms).");
    } catch (cause) {
      toast.error(describeError(cause));
    }
  }

  return (
    <Card>
      <PanelHeading
        title="Vibration"
        description="Trigger a 150 ms pulse."
        setting="js_scripts.enable_vibration_api"
        available={available}
        icon={Vibrate}
      />
      <CardContent className="space-y-4">
        <Button disabled={!available} onClick={handleTest}>
          Vibrate
        </Button>
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
          Enable the settings below, then reload the page.
        </p>
        <Button variant="outline" onClick={refresh}>
          Refresh
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
