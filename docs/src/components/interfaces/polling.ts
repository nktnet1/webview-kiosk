import {
  type AvailableInterfaces,
  type BatteryStatus,
  detectInterfaces,
  type InterfaceHost,
  readBattery,
  readBrightness,
} from "./bridge";

export const DEVICE_POLL_INTERVAL_MS = 2_000;

export interface DeviceSnapshot {
  availability: AvailableInterfaces;
  battery: BatteryStatus | null;
  brightness: number | null;
}

/** Read each exposed API independently; a failing bridge must not stop polling. */
export function readDeviceSnapshot(host: InterfaceHost): DeviceSnapshot {
  const availability = detectInterfaces(host);
  let battery: BatteryStatus | null = null;
  let brightness: number | null = null;

  if (availability.battery) {
    try {
      battery = readBattery(host);
    } catch {
      // An unreadable bridge must not prevent updates from other APIs.
    }
  }
  if (availability.brightness) {
    try {
      brightness = readBrightness(host);
    } catch {
      // Values may temporarily be unavailable while Android initializes.
    }
  }

  return { availability, battery, brightness };
}

/** Poll immediately and every two seconds; return a route-unmount cleanup. */
export function subscribeDeviceSnapshots(
  getHost: () => InterfaceHost,
  onSnapshot: (snapshot: DeviceSnapshot) => void,
): () => void {
  const poll = () => onSnapshot(readDeviceSnapshot(getHost()));
  poll();
  const timer = setInterval(poll, DEVICE_POLL_INTERVAL_MS);
  return () => clearInterval(timer);
}
