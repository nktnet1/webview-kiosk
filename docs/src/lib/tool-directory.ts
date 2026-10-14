/** Shared metadata for the public documentation tools directory. */
export const toolDirectory = [
  {
    id: "mqtt",
    title: "MQTT",
    description:
      "Connect to an MQTT broker, publish commands and settings, and inspect live messages.",
    href: "/tools/mqtt",
  },
  {
    id: "unifiedpush",
    title: "UnifiedPush",
    description:
      "Send encrypted Web Push messages to a UnifiedPush endpoint and inspect responses.",
    href: "/tools/unifiedpush",
  },
  {
    id: "regex",
    title: "Regex Playground",
    description:
      "Test URL blacklist and whitelist patterns against a sample address.",
    href: "/tools/regex",
  },
  {
    id: "qr",
    title: "QR Installation",
    description:
      "Generate an Android device provisioning QR code using the existing installer.",
    href: "/tools/qr-installation",
  },
] as const satisfies ReadonlyArray<{
  id: string;
  title: string;
  description: string;
  href: `/tools/${string}`;
}>;
