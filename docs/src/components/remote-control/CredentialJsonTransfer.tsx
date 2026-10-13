"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "#/components/ui/alert-dialog";
import { Button } from "#/components/ui/button";
import {
  credentialsDiffer,
  parseCredentialJson,
  serializeCredentialJson,
} from "./credential-json";

const MAX_CREDENTIAL_FILE_BYTES = 64 * 1024;

type CredentialJsonTransferProps<T extends object> = {
  filename: string;
  template: T;
  /** Read current form state on demand, rather than capturing a stale snapshot. */
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
  const [pendingImport, setPendingImport] = useState<T | null>(null);

  function exportJson() {
    try {
      const json = serializeCredentialJson(getCredentials(), template);
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
      toast.success("Credential JSON exported. Keep the file secure.");
    } catch (error) {
      toast.error(
        `Unable to export credentials: ${error instanceof Error ? error.message : "Unexpected error"}`,
      );
    }
  }

  async function importJson(file: File) {
    try {
      if (file.size > MAX_CREDENTIAL_FILE_BYTES) {
        throw new Error("The credentials JSON file must be 64 KB or smaller.");
      }
      const credentials = parseCredentialJson(await file.text(), template);
      const existing = getCredentials();
      if (
        credentialsDiffer(existing, credentials, template)
        && Object.values(existing).some((value) => value !== "")
      ) {
        setPendingImport(credentials);
      } else {
        onImport(credentials);
      }
    } catch (error) {
      toast.error(
        `Unable to import credentials: ${error instanceof Error ? error.message : "Unexpected error"}`,
      );
    }
  }

  return (
    <>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          className="w-full sm:w-60 shrink-0"
          onClick={exportJson}
        >
          Export credentials JSON
        </Button>
        <Button
          type="button"
          variant="outline"
          className="w-full sm:w-60 shrink-0"
          onClick={() => fileInput.current?.click()}
        >
          Import credentials JSON
        </Button>
        <input
          ref={fileInput}
          type="file"
          accept=".json,application/json"
          aria-label="Import credentials JSON file"
          className="hidden"
          onChange={(event) => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            if (file) void importJson(file);
          }}
        />
      </div>
      <p className="text-xs text-muted-foreground">{warning}</p>
      <AlertDialog
        open={pendingImport !== null}
        onOpenChange={(open) => {
          if (!open) setPendingImport(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Replace existing credentials?</AlertDialogTitle>
            <AlertDialogDescription>
              Importing this file will overwrite your saved credentials. Missing
              fields will be cleared. Export a backup first if needed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel asChild>
              <Button type="button" variant="outline">
                Cancel
              </Button>
            </AlertDialogCancel>
            <AlertDialogAction asChild>
              <Button
                type="button"
                onClick={() => {
                  if (pendingImport) onImport(pendingImport);
                  setPendingImport(null);
                }}
              >
                Replace credentials
              </Button>
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
