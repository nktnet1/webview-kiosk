import { Link } from "@tanstack/react-router";
import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";
import { ArrowUpRight, Check, ChevronDown, QrCode } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import QRCode from "react-qr-code";
import type { QrData } from "#/components/qr/schema";
import {
  QR_INSTALLATION_VERSIONS,
  type QrInstallationSource,
} from "#/components/qr/version";
import { Button } from "#/components/ui/button";

export default function QrCodePreview({
  result,
  hasChanges,
  canSubmit,
}: {
  result: { payload: QrData; downloadSource: QrInstallationSource } | null;
  hasChanges: boolean;
  canSubmit: boolean;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [showJson, setShowJson] = useState(false);

  useEffect(() => {
    if (!result) return;
    headingRef.current?.focus({ preventScroll: true });
    if (window.matchMedia("(max-width: 1023px)").matches) {
      headingRef.current?.scrollIntoView({
        block: "start",
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
      });
    }
  }, [result]);

  return (
    <section
      aria-labelledby="qr-preview-title"
      className="min-w-0 overflow-hidden rounded-2xl border bg-card text-card-foreground lg:sticky lg:top-24"
    >
      <div className="border-b px-5 py-5 sm:px-6">
        <h2
          ref={headingRef}
          id="qr-preview-title"
          tabIndex={-1}
          className="scroll-mt-28 font-semibold outline-none"
        >
          Your installation code
        </h2>
        <p role="status" className="mt-1 text-sm text-muted-foreground">
          {result ? (
            hasChanges ? (
              "Settings changed. Generate a new code before scanning."
            ) : (
              <span className="inline-flex items-center gap-1.5 text-foreground">
                <Check aria-hidden="true" className="size-4" />
                Ready to scan
              </span>
            )
          ) : (
            "Your QR code will appear here."
          )}
        </p>
      </div>

      <div className="flex flex-col items-center gap-4 px-5 py-6 text-center sm:px-6">
        {result && !hasChanges ? (
          <>
            <div
              className="w-full max-w-80 rounded-xl bg-white p-4"
              data-testid="provisioning-qr"
              data-provisioning-payload={JSON.stringify(result.payload)}
            >
              <QRCode
                title="Android device provisioning QR code"
                value={JSON.stringify(result.payload)}
                size={288}
                bgColor="#ffffff"
                fgColor="#000000"
                className="h-auto w-full"
              />
            </div>
            <p className="text-sm text-muted-foreground">
              {result.downloadSource} ·{" "}
              {QR_INSTALLATION_VERSIONS[result.downloadSource].tag}
            </p>
          </>
        ) : (
          <div className="flex min-h-64 w-full flex-col items-center justify-center rounded-xl border border-dashed bg-muted/30 px-5 py-8">
            <QrCode
              aria-hidden="true"
              className="mb-4 size-12 text-muted-foreground/60"
            />
            <p className="text-sm font-medium">
              {hasChanges ? "Update your QR code" : "Ready when you are"}
            </p>
            <p className="mt-2 max-w-60 text-sm leading-relaxed text-muted-foreground">
              {hasChanges
                ? "Generate a new code to include your latest settings."
                : "Choose your settings, then generate a QR code to set up your device."}
            </p>
            {hasChanges && (
              <Button
                type="submit"
                form="qr-installation-form"
                disabled={!canSubmit}
                variant="outline"
                className="mt-4 min-h-10"
              >
                Update QR code
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="border-t bg-muted/30 px-5 py-5 sm:px-6">
        <h3 className="text-sm font-semibold">How to scan</h3>
        <ol className="mt-4 space-y-4 text-sm text-muted-foreground">
          {[
            "Start with a new or factory-reset Android device.",
            "On the welcome screen, tap the same spot 6 times. Connect to Wi-Fi if prompted.",
            "Scan the QR code and follow the setup instructions.",
          ].map((step, index) => (
            <li key={step} className="flex items-start gap-3">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full border bg-card text-xs font-medium text-foreground">
                {index + 1}
              </span>
              <span className="pt-0.5 leading-relaxed">{step}</span>
            </li>
          ))}
        </ol>
        <Link
          to="/docs/$"
          params={{ _splat: "installation" }}
          hash="3-qr-code-provisioning"
          className="mt-5 inline-flex items-center gap-1.5 rounded text-sm font-medium underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          Read the installation guide
          <ArrowUpRight aria-hidden="true" className="size-4" />
        </Link>
      </div>

      {result && !hasChanges && (
        <details
          className="group border-t px-5 sm:px-6"
          open={showJson}
          onToggle={(event) => setShowJson(event.currentTarget.open)}
        >
          <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 rounded text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
            Show JSON
            <ChevronDown
              aria-hidden="true"
              className="size-4 shrink-0 transition-transform group-open:rotate-180"
            />
          </summary>
          {showJson && (
            <div className="min-w-0 pb-4 text-left [&_figure]:my-0">
              <DynamicCodeBlock
                lang="json"
                code={JSON.stringify(result.payload, null, 2)}
              />
            </div>
          )}
        </details>
      )}
    </section>
  );
}
