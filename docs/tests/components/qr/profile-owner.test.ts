import { describe, expect, it } from "vitest";
import {
  buildProfileOwnerCommand,
  PROFILE_OWNER_COMPONENTS,
} from "#/components/qr/profile-owner";

describe("Profile Owner ADB setup", () => {
  it.each(["0", "10", "128"])("uses the chosen full user %s", (userId) => {
    expect(buildProfileOwnerCommand(userId, "Dhizuku")).toBe(
      `adb shell dpm set-profile-owner --user ${userId} com.rosan.dhizuku/.server.DhizukuDAReceiver`,
    );
  });

  it.each(Object.entries(PROFILE_OWNER_COMPONENTS))(
    "uses the correct component for %s",
    (target, component) => {
      expect(
        buildProfileOwnerCommand(
          "42",
          target as keyof typeof PROFILE_OWNER_COMPONENTS,
        ),
      ).toContain(component);
    },
  );

  it.each([
    "",
    "-1",
    "current",
    "01",
    "1.5",
    "1\n",
    "0; reboot",
    "$(id)",
    "2147483648",
    "9999999999999999999",
  ])("does not generate a command for invalid input %j", (userId) => {
    expect(buildProfileOwnerCommand(userId, "Dhizuku")).toBeNull();
  });
});
