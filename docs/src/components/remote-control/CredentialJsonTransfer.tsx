"use client";

import { ClipboardCopy, Download, FileUp, Import, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "#/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "#/components/ui/dialog";
import { Label } from "#/components/ui/label";
import { Textarea } from "#/components/ui/textarea";
import {
  credentialsDiffer,
  parseCredentialJson,
  serializeCredentialJson,
} from "./credential-json";
import { InfoTooltip } from "./InfoTooltip";

const MAX_CREDENTIAL_FILE_BYTES = 64 * 1024;
type TransferMode = "export" | "import";

type CredentialJsonTransferProps<T extends object> = {
  filename: string;
  template: T;
  /** Read current form state when opening export or importing, never a stale render. */
  getCredentials: () => T;
  onImport: (credentials: T) => void;
  warning: string;
};

export function CredentialJsonTransfer<T extends object>({
  filename,
  template,
  getCredentials,
  onImport,
  warning,
}: CredentialJsonTransferProps<T>) {
  const fileInput = useRef<HTMLInputElement>(null);
  const lastTrigger = useRef<HTMLButtonElement>(null);
  const [mode, setMode] = useState<TransferMode | null>(null);
  const [json, setJson] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pendingImport, setPendingImport] = useState<T | null>(null);

  function closeDialog() {
    setMode(null);
    setJson("");
    setError(null);
    setPendingImport(null);
  }

  function openDialog(next: TransferMode) {
    try {
      setJson(
        next === "export"
          ? serializeCredentialJson(getCredentials(), template)
          : "",
      );
      setError(null);
      setPendingImport(null);
      setMode(next);
    } catch (cause) {
      toast.error(
        cause instanceof Error
          ? cause.message
          : "Unable to open credentials JSON.",
      );
    }
  }

  function downloadJson() {
    try {
      const url = URL.createObjectURL(
        new Blob([json], { type: "application/json" }),
      );
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
      toast.success("Credentials downloaded");
    } catch (cause) {
      toast.error(
        cause instanceof Error ? cause.message : "Unable to download JSON.",
      );
    }
  }

  async function copyJson() {
    try {
      await navigator.clipboard.writeText(json);
      toast.success("Credentials copied to clipboard");
    } catch {
      toast.error("Clipboard unavailable. Select and copy the JSON manually.");
    }
  }

  async function readFile(file: File) {
    try {
      if (file.size > MAX_CREDENTIAL_FILE_BYTES) {
        throw new Error("The credentials JSON must be 64 KB or smaller.");
      }
      setJson(await file.text());
      setError(null);
      setPendingImport(null);
    } catch (cause) {
      setJson("");
      setPendingImport(null);
      setError(
        cause instanceof Error ? cause.message : "Unable to read JSON file.",
      );
    }
  }

  function importJson() {
    try {
      if (new TextEncoder().encode(json).length > MAX_CREDENTIAL_FILE_BYTES) {
        throw new Error("The credentials JSON must be 64 KB or smaller.");
      }
      const credentials = parseCredentialJson(json, template);
      const existing = getCredentials();
      setError(null);
      if (
        credentialsDiffer(existing, credentials, template)
        && Object.values(existing).some((value) => value !== "")
      ) {
        setPendingImport(credentials);
        return;
      }
      onImport(credentials);
      closeDialog();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Unable to import credentials.",
      );
    }
  }

  function confirmImport() {
    if (pendingImport === null) return;
    try {
      onImport(pendingImport);
      closeDialog();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Unable to import credentials.",
      );
      setPendingImport(null);
    }
  }

  return (
    <>
      <div className="space-y-2">
        <div className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
          JSON backup <InfoTooltip>{warning}</InfoTooltip>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={(event) => {
              lastTrigger.current = event.currentTarget;
              openDialog("export");
            }}
          >
            <Download aria-hidden="true" /> Export JSON
          </Button>
          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={(event) => {
              lastTrigger.current = event.currentTarget;
              openDialog("import");
            }}
          >
            <Import aria-hidden="true" /> Import JSON
          </Button>
        </div>
      </div>
      <Dialog
        open={mode !== null}
        onOpenChange={(open) => {
          if (!open) closeDialog();
        }}
      >
        <DialogContent
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            lastTrigger.current?.focus();
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {pendingImport
                ? "Replace existing credentials?"
                : mode === "export"
                  ? "Export credentials"
                  : "Import credentials"}
            </DialogTitle>
            <DialogDescription>
              {pendingImport
                ? "This replaces all saved credentials. Missing JSON fields will be cleared."
                : mode === "export"
                  ? "This JSON includes private keys and secrets. Keep it secure."
                  : "Paste JSON or choose a file. Missing fields will be cleared."}
            </DialogDescription>
          </DialogHeader>
          {pendingImport === null && (
            <div className="space-y-2">
              <Label htmlFor="credential-transfer-json">Credentials JSON</Label>
              <Textarea
                id="credential-transfer-json"
                className="min-h-56 max-h-[45dvh] resize-y font-mono text-xs"
                rows={12}
                spellCheck={false}
                autoComplete="off"
                readOnly={mode === "export"}
                aria-invalid={error !== null}
                value={json}
                placeholder={
                  mode === "import" ? '{ "endpoint": "https://…" }' : undefined
                }
                onChange={(event) => {
                  setJson(event.target.value);
                  setError(null);
                }}
              />
            </div>
          )}
          {error && (
            <p role="alert" className="text-destructive text-sm">
              {error}
            </p>
          )}
          <DialogFooter>
            {pendingImport ? (
              <>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setPendingImport(null)}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  onClick={confirmImport}
                >
                  Replace credentials
                </Button>
              </>
            ) : mode === "export" ? (
              <>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void copyJson()}
                >
                  <ClipboardCopy aria-hidden="true" /> Copy JSON
                </Button>
                <Button type="button" onClick={downloadJson}>
                  <Download aria-hidden="true" /> Download file
                </Button>
              </>
            ) : (
              <>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => fileInput.current?.click()}
                >
                  <FileUp aria-hidden="true" /> Choose file
                </Button>
                <Button type="button" onClick={importJson}>
                  <Upload aria-hidden="true" /> Import credentials
                </Button>
              </>
            )}
          </DialogFooter>
          {mode === "import" && (
            <input
              ref={fileInput}
              type="file"
              accept=".json,application/json"
              aria-label="Credentials JSON file"
              className="sr-only"
              tabIndex={-1}
              onChange={(event) => {
                const file = event.currentTarget.files?.[0];
                event.currentTarget.value = "";
                if (file) void readFile(file);
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
