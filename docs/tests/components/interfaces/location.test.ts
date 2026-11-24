import { describe, expect, it, vi } from "vitest";
import { startLocationRequest } from "#/components/interfaces/location";

function fixture() {
  const calls: {
    success: PositionCallback;
    error?: PositionErrorCallback | null;
  }[] = [];
  const record = (
    success: PositionCallback,
    error?: PositionErrorCallback | null,
    _options?: PositionOptions,
  ) => {
    calls.push({ success, error });
    return 0;
  };
  const geo = {
    getCurrentPosition: vi.fn(record),
    watchPosition: vi.fn(record),
    clearWatch: vi.fn(),
  } satisfies Geolocation;
  const onPosition = vi.fn();
  const onError = vi.fn();
  return { geo, calls, onPosition, onError };
}

const position = {
  timestamp: 123,
  coords: { latitude: -33.8 },
} as GeolocationPosition;
const error = (code: number) =>
  ({ code, message: "Location error" }) as GeolocationPositionError;

describe("location requests", () => {
  it.each([false, true])(
    "requests a fresh, precise fix with watch=%s",
    (watch) => {
      const { geo, calls, onPosition, onError } = fixture();
      const request = startLocationRequest(geo, watch, onPosition, onError);
      const method = watch ? geo.watchPosition : geo.getCurrentPosition;
      expect(method).toHaveBeenCalledExactlyOnceWith(
        expect.any(Function),
        expect.any(Function),
        {
          enableHighAccuracy: true,
          maximumAge: 0,
          timeout: 30_000,
        },
      );
      expect(
        watch ? geo.getCurrentPosition : geo.watchPosition,
      ).not.toHaveBeenCalled();
      expect(request.active).toBe(true);
      calls[0].success(position);
      expect(onPosition).toHaveBeenCalledExactlyOnceWith(position);
      expect(request.active).toBe(watch);
      request.stop();
      request.stop();
      expect(request.active).toBe(false);
      expect(geo.clearWatch.mock.calls).toEqual(watch ? [[0]] : []);
    },
  );

  it.each([false, true])(
    "ignores late callbacks after cancellation with watch=%s",
    (watch) => {
      const { geo, calls, onPosition, onError } = fixture();
      const request = startLocationRequest(geo, watch, onPosition, onError);
      request.stop();
      calls[0].success(position);
      calls[0].error?.(error(1));
      expect(onPosition).not.toHaveBeenCalled();
      expect(onError).not.toHaveBeenCalled();
    },
  );

  it.each([1, 2, 3])("ends a one-shot request after error %s", (code) => {
    const { geo, calls, onPosition, onError } = fixture();
    const request = startLocationRequest(geo, false, onPosition, onError);
    calls[0].error?.(error(code));
    expect(onError).toHaveBeenCalledExactlyOnceWith(error(code));
    expect(request.active).toBe(false);
    calls[0].success(position);
    calls[0].error?.(error(code));
    expect(onPosition).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it("keeps watches active across transient errors and fresh fixes", () => {
    const { geo, calls, onPosition, onError } = fixture();
    const request = startLocationRequest(geo, true, onPosition, onError);
    for (const code of [2, 3]) {
      calls[0].error?.(error(code));
      calls[0].success(position);
      expect(request.active).toBe(true);
    }
    expect(onError).toHaveBeenCalledTimes(2);
    expect(onPosition).toHaveBeenCalledTimes(2);
    expect(geo.clearWatch).not.toHaveBeenCalled();
  });

  it("ends a watch on permission denial, including watch ID zero", () => {
    const { geo, calls, onPosition, onError } = fixture();
    const request = startLocationRequest(geo, true, onPosition, onError);
    calls[0].error?.(error(1));
    expect(request.active).toBe(false);
    expect(geo.clearWatch).toHaveBeenCalledExactlyOnceWith(0);
    request.stop();
    calls[0].error?.(error(1));
    calls[0].success(position);
    expect(geo.clearWatch).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onPosition).not.toHaveBeenCalled();
  });

  it("clears a watch denied before the provider returns its ID", () => {
    const { geo, onPosition, onError } = fixture();
    geo.watchPosition.mockImplementationOnce((_success, fail) => {
      fail?.(error(1));
      return 0;
    });
    const request = startLocationRequest(geo, true, onPosition, onError);
    expect(request.active).toBe(false);
    expect(geo.clearWatch).toHaveBeenCalledExactlyOnceWith(0);
    request.stop();
    expect(geo.clearWatch).toHaveBeenCalledTimes(1);
  });

  it.each([false, true])(
    "invalidates callbacks if the API throws with watch=%s",
    (watch) => {
      const { geo, onPosition, onError } = fixture();
      const method = watch ? geo.watchPosition : geo.getCurrentPosition;
      let callback: PositionCallback | undefined;
      method.mockImplementationOnce((success) => {
        callback = success;
        throw new Error("Location blocked");
      });
      expect(() =>
        startLocationRequest(geo, watch, onPosition, onError),
      ).toThrow("Location blocked");
      callback?.(position);
      expect(onPosition).not.toHaveBeenCalled();
    },
  );
});
