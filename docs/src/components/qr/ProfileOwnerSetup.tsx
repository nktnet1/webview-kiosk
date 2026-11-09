import { useId, useState } from "react";
import { joinURL } from "ufo";
import {
  buildProfileOwnerCommand,
  PROFILE_OWNER_COMPONENTS,
  type ProfileOwnerTarget,
} from "#/components/qr/profile-owner";
import { getBasePath } from "#/lib/basePath";

export default function ProfileOwnerSetup() {
  const userIdInput = useId();
  const targetInput = useId();
  const [userId, setUserId] = useState("");
  const [target, setTarget] = useState<ProfileOwnerTarget>(
    "Webview Kiosk (GitHub / F-Droid / IzzyOnDroid)",
  );
  const command = buildProfileOwnerCommand(userId, target);

  return (
    <section className="max-w-3xl space-y-5 rounded-2xl border bg-card p-5 sm:p-6">
      <h2 className="text-xl font-semibold">Main-user Profile Owner via ADB</h2>
      <p>
        Android has no QR provisioning mode that assigns a Profile Owner to the
        existing main user. Work-profile provisioning creates a separate
        profile. Install the selected app first, enable USB debugging, and
        connect ADB.
      </p>
      <p>Find the foreground full user and inspect the users on your device:</p>
      <pre className="overflow-x-auto rounded-lg bg-muted p-4 text-sm">
        <code>{"adb shell am get-current-user\nadb shell pm list users"}</code>
      </pre>
      <p>
        Use that full user ID below. Do not choose a managed work profile or
        assume the main user is user 0, especially on headless-system-user
        devices.
      </p>
      <div className="space-y-2">
        <label htmlFor={targetInput} className="block font-medium">
          Management app
        </label>
        <select
          id={targetInput}
          value={target}
          onChange={(event) =>
            setTarget(event.target.value as ProfileOwnerTarget)
          }
          className="w-full rounded-lg border bg-background p-3"
        >
          {Object.keys(PROFILE_OWNER_COMPONENTS).map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </div>
      <div className="space-y-2">
        <label htmlFor={userIdInput} className="block font-medium">
          Full Android user ID
        </label>
        <input
          id={userIdInput}
          inputMode="numeric"
          value={userId}
          onChange={(event) => setUserId(event.target.value)}
          placeholder="Enter the user ID reported by ADB"
          aria-invalid={userId !== "" && command === null}
          className="w-full rounded-lg border bg-background p-3"
        />
      </div>
      {command ? (
        <pre
          className="overflow-x-auto rounded-lg bg-muted p-4 text-sm"
          data-testid="profile-owner-command"
        >
          <code>{command}</code>
        </pre>
      ) : (
        <p role="status">
          Enter a valid non-negative user ID to generate the ADB command.
        </p>
      )}
      <p>
        Android may reject setup because an owner already exists, accounts are
        incompatible, or setup restrictions apply. Use an eligible test device
        and follow the exact ADB error; this tool does not bypass those checks.
      </p>
      <p>
        Independent full-user Profile Owners can manage Lock Task policies on
        Android 9 or later when no Device Owner is set. Android 8 supports
        affiliated secondary users. Device Owner remains the recommended mode
        for fully managed kiosks. With Dhizuku, open Webview Kiosk in the same
        user and grant shared owner permission under Device → Manage Device
        Policies.
      </p>
      <a
        className="underline"
        href={joinURL(getBasePath(), "/docs/security#profile-owner-mode")}
      >
        Profile Owner capabilities and restrictions
      </a>
    </section>
  );
}
