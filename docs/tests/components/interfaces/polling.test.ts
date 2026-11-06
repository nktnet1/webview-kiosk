import { afterEach, describe, expect, it, vi } from "vitest";
import type { InterfaceHost } from "#/components/interfaces/bridge";
import {
  DEVICE_POLL_INTERVAL_MS,
  readDeviceSnapshot,
  subscribeDeviceSnapshots,
} from "#/components/interfaces/polling";

const initialBattery = {
  level: 0.4,
  percentage: 40,
  charging: false,
  chargingType: "none",
  voltage: 3.7,
  temperature: 27,
  health: "good",
};

describe("device API polling", () => {
  afterEach(() => vi.useRealTimers());

  it("reads every battery field and brightness independently", () => {
    let battery = { ...initialBattery };
    let brightness = 41;
    const host: InterfaceHost = {
      WebviewKioskBatteryInterface: {
        getBatteryStatus: () => JSON.stringify(battery),
      },
      WebviewKioskBrightnessInterface: {
        getBrightness: () => brightness,
        setBrightness: () => {},
      },
      navigator: { vibrate: () => true },
    };
    expect(readDeviceSnapshot(host)).toEqual({
      availability: {
        battery: true,
        brightness: true,
        nfc: false,
        vibration: true,
        blob: false,
      },
      battery: initialBattery,
      brightness: 41,
    });

    battery = {
      level: 0.9,
      percentage: 90,
      charging: true,
      chargingType: "usb",
      voltage: 4.1,
      temperature: 30,
      health: "overheat",
    };
    brightness = -1;
    expect(readDeviceSnapshot(host)).toMatchObject({
      battery,
      brightness: -1,
    });
  });

  it("continues reading other bridges when one throws", () => {
    const host: InterfaceHost = {
      WebviewKioskBatteryInterface: {
        getBatteryStatus: () => {
          throw new Error("not ready");
        },
      },
      WebviewKioskBrightnessInterface: {
        getBrightness: () => 25,
        setBrightness: () => {},
      },
    };
    expect(readDeviceSnapshot(host)).toMatchObject({
      availability: { battery: true, brightness: true },
      battery: null,
      brightness: 25,
    });
    host.WebviewKioskBatteryInterface = {
      getBatteryStatus: () => JSON.stringify(initialBattery),
    };
    host.WebviewKioskBrightnessInterface = {
      getBrightness: () => "not a number",
      setBrightness: () => {},
    };
    expect(readDeviceSnapshot(host)).toMatchObject({
      battery: initialBattery,
      brightness: null,
    });
  });

  it("polls immediately and every second, detects new APIs, and stops on cleanup", () => {
    vi.useFakeTimers();
    let level = 40;
    const host: InterfaceHost = {
      WebviewKioskBatteryInterface: {
        getBatteryStatus: () =>
          JSON.stringify({ ...initialBattery, percentage: level }),
      },
    };
    const onSnapshot = vi.fn();
    const getHost = vi.fn(() => host);
    const stop = subscribeDeviceSnapshots(getHost, onSnapshot);

    expect(DEVICE_POLL_INTERVAL_MS).toBe(1_000);
    expect(onSnapshot).toHaveBeenCalledTimes(1);
    expect(onSnapshot.mock.lastCall?.[0]).toMatchObject({
      availability: { battery: true, brightness: false },
      battery: { percentage: 40 },
    });

    level = 85;
    host.WebviewKioskBrightnessInterface = {
      getBrightness: () => 65,
      setBrightness: () => {},
    };
    host.navigator = { vibrate: () => true };
    host.WebviewKioskBlobInterface = {
      startDownload: () => true,
      appendChunk: () => true,
      finishDownload: () => true,
    };
    vi.advanceTimersByTime(999);
    expect(onSnapshot).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(onSnapshot).toHaveBeenCalledTimes(2);
    expect(onSnapshot.mock.lastCall?.[0]).toMatchObject({
      availability: {
        battery: true,
        brightness: true,
        vibration: true,
        blob: true,
      },
      battery: { percentage: 85 },
      brightness: 65,
    });

    delete host.WebviewKioskBatteryInterface;
    vi.advanceTimersByTime(1_000);
    expect(onSnapshot.mock.lastCall?.[0]).toMatchObject({
      availability: { battery: false },
      battery: null,
      brightness: 65,
    });
    stop();
    vi.advanceTimersByTime(3_000);
    expect(onSnapshot).toHaveBeenCalledTimes(3);
    expect(getHost).toHaveBeenCalledTimes(3);
  });
});
