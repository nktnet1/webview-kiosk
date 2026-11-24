/** Start a standard geolocation request with explicit watch cleanup. */
export function startLocationRequest(
  geo: Geolocation,
  watch: boolean,
  onPosition: PositionCallback,
  onError: PositionErrorCallback,
) {
  let active = true;
  let watchId: number | null = null;
  const request = {
    get active() {
      return active;
    },
    stop() {
      if (!active) return;
      active = false;
      if (watchId !== null) geo.clearWatch(watchId);
    },
  };
  const success: PositionCallback = (position) => {
    if (!active) return;
    if (!watch) request.stop();
    onPosition(position);
  };
  const error: PositionErrorCallback = (cause) => {
    if (!active) return;
    if (!watch || cause.code === 1) request.stop();
    onError(cause);
  };
  const options: PositionOptions = {
    enableHighAccuracy: true,
    maximumAge: 0,
    timeout: 30_000,
  };
  try {
    if (watch) {
      watchId = geo.watchPosition(success, error, options);
      // A provider may invoke an error callback before returning its watch ID.
      if (!active) geo.clearWatch(watchId);
    } else {
      geo.getCurrentPosition(success, error, options);
    }
  } catch (cause) {
    request.stop();
    throw cause;
  }
  return request;
}
