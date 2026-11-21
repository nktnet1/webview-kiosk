/* Direct GPS compatibility for HTTPS main documents. Installed before page scripts. */
(() => {
  'use strict';
  const bridge = window.WebviewKioskGps;
  const geo = navigator.geolocation;
  if (window !== window.top || !window.isSecureContext ||
      location.protocol !== 'https:' || !bridge || !geo) return;

  const browserWatch = geo.watchPosition.bind(geo);
  const browserClear = geo.clearWatch.bind(geo);
  const requests = new Map();
  const maximum = 0xffffffff;
  let nextId = 1;
  let nonce;
  let token = null;
  let allowed = false;
  let hostVisible = false;
  let subscription = false;
  let permissionWatch = null;
  let permissionAttempt = 0;
  let cache = null;
  let suspended = false;

  function visible() {
    return !suspended && hostVisible && !document.hidden;
  }

  function post(type, data = {}) {
    if (!token) return;
    bridge.postMessage(JSON.stringify({ ...data, type, token }));
  }

  function stopPermissionWatch() {
    permissionAttempt++;
    if (permissionWatch !== null) browserClear(permissionWatch);
    permissionWatch = null;
  }

  function stopTimer(request) {
    if (request.timer === null) return;
    clearTimeout(request.timer);
    request.timer = null;
    request.remaining = Math.max(0, request.remaining - (performance.now() - request.since));
  }

  function errorValue(code, message) {
    const value = Object.create(window.GeolocationPositionError?.prototype || Object.prototype);
    Object.defineProperties(value, {
      code: { enumerable: true, value: code },
      message: { enumerable: true, value: message },
      PERMISSION_DENIED: { value: 1 },
      POSITION_UNAVAILABLE: { value: 2 },
      TIMEOUT: { value: 3 },
    });
    return value;
  }

  function report(request, code, message, terminal = false) {
    stopTimer(request);
    if (!request.watch || terminal) requests.delete(request.id);
    if (request.error) {
      setTimeout(() => {
        if (!request.cancelled) {
          const callback = request.error;
          callback(errorValue(code, message));
        }
      }, 0);
    }
  }

  function failAll(code, message) {
    for (const request of requests.values()) report(request, code, message, true);
    stopPermissionWatch();
    if (subscription) post('unsubscribe');
    subscription = false;
  }

  function startTimer(request) {
    if (request.timer !== null || request.timedOut || request.timeout === maximum) return;
    request.since = performance.now();
    // Browsers clamp a single timer to a signed 32-bit delay.
    request.timer = setTimeout(() => {
      stopTimer(request);
      if (request.remaining > 0) {
        startTimer(request);
        return;
      }
      request.timedOut = true;
      report(request, 3, 'Direct GPS location request timed out');
      refresh();
    }, Math.min(request.remaining, 0x7fffffff));
  }

  function deliver(request, position) {
    if (request.deliveryPending) return;
    request.deliveryPending = true;
    stopTimer(request);
    request.remaining = request.timeout;
    request.timedOut = false;
    setTimeout(() => {
      request.deliveryPending = false;
      if (request.cancelled || requests.get(request.id) !== request) return;
      if (visible()) {
        if (!request.watch) requests.delete(request.id);
        const callback = request.success;
        try { callback(position); } finally { refresh(); }
      } else {
        refresh();
      }
    }, 0);
  }

  function askBrowserPermission() {
    if (permissionWatch !== null || allowed || !requests.size || !token || !visible()) return;
    // Do not wait for a fused fix. Chromium's real request enforces secure context,
    // Permissions Policy and the app's existing site permission dialog. The native
    // permission callback authorizes GPS and lets us cancel this temporary watch.
    const attempt = ++permissionAttempt;
    permissionWatch = browserWatch(
      () => {},
      error => {
        if (attempt !== permissionAttempt || allowed) return;
        if (error.code === 1) {
          allowed = false;
          failAll(1, error.message || 'Location permission denied');
        }
      },
      { enableHighAccuracy: false, maximumAge: 0, timeout: 0x7fffffff },
    );
  }

  function refresh() {
    if (!requests.size) stopPermissionWatch();
    if (!visible() || !allowed) {
      if (!visible()) {
        stopPermissionWatch();
        cache = null;
      }
      for (const request of requests.values()) stopTimer(request);
      if (subscription) post('unsubscribe');
      subscription = false;
      if (visible()) askBrowserPermission();
      return;
    }
    for (const request of requests.values()) {
      if (request.deliveryPending) continue;
      if (request.awaitingValidation) {
        if (!request.validationSent) {
          request.validationSent = true;
          post('validate', { id: request.id });
        }
        continue;
      }
      if (request.useCache) {
        request.useCache = false;
        if (cache && request.maximumAge > 0 &&
            performance.now() - cache.acquiredAt <= request.maximumAge) {
          deliver(request, cache.position);
        }
      }
      if (requests.has(request.id) && !request.deliveryPending) startTimer(request);
    }
    const needed = Array.from(requests.values()).some(request =>
      !request.awaitingValidation && (request.watch || !request.deliveryPending));
    if (subscription !== needed) {
      subscription = needed;
      post(needed ? 'subscribe' : 'unsubscribe');
    }
    if (!needed) stopPermissionWatch();
  }

  function clamp(value, fallback) {
    if (value === undefined) return fallback;
    const number = +value;
    if (Number.isNaN(number) || number <= 0) return 0;
    if (number >= maximum) return maximum;
    const lower = Math.floor(number);
    return number - lower === 0.5 ? lower + (lower % 2) : Math.round(number);
  }

  function request(watch, success, error, options) {
    if (typeof success !== 'function' ||
        (error != null && typeof error !== 'function')) {
      throw new TypeError('Geolocation callbacks must be functions');
    }
    if (options != null && typeof options !== 'object' && typeof options !== 'function') {
      throw new TypeError('Geolocation options must be a dictionary');
    }
    const timeout = clamp(options?.timeout, maximum);
    const maximumAge = clamp(options?.maximumAge, 0);
    // GPS always requests precise fixes, including when this hint is false.
    Boolean(options?.enableHighAccuracy);
    while (requests.has(nextId)) nextId = nextId === 0x7fffffff ? 1 : nextId + 1;
    const id = nextId;
    nextId = nextId === 0x7fffffff ? 1 : nextId + 1;
    requests.set(id, {
      id, watch, success, error, timeout, maximumAge, remaining: timeout,
      timer: null, since: 0, timedOut: false, cancelled: false, useCache: true,
      awaitingValidation: true, validationSent: false,
      deliveryPending: false,
    });
    refresh();
    return id;
  }

  Object.defineProperties(geo, {
    getCurrentPosition: {
      configurable: true, writable: true, enumerable: true,
      value(success, error, options) { request(false, success, error, options); },
    },
    watchPosition: {
      configurable: true, writable: true, enumerable: true,
      value(success, error, options) { return request(true, success, error, options); },
    },
    clearWatch: {
      configurable: true, writable: true, enumerable: true,
      value(id) {
        if (!arguments.length) throw new TypeError('clearWatch requires an ID');
        const request = requests.get(Number(id) >> 0);
        if (!request) return;
        request.cancelled = true;
        stopTimer(request);
        requests.delete(request.id);
        refresh();
      },
    },
  });

  function positionValue(message) {
    const coords = Object.create(window.GeolocationCoordinates?.prototype || Object.prototype);
    for (const [key, value] of Object.entries(message.coords)) {
      Object.defineProperty(coords, key, { enumerable: true, value });
    }
    Object.defineProperty(coords, 'toJSON', { value() { return { ...this }; } });
    const position = Object.create(window.GeolocationPosition?.prototype || Object.prototype);
    Object.defineProperties(position, {
      coords: { enumerable: true, value: coords },
      timestamp: { enumerable: true, value: message.timestamp },
    });
    Object.defineProperty(position, 'toJSON', {
      value() { return { coords: coords.toJSON(), timestamp: this.timestamp }; },
    });
    return position;
  }

  bridge.onmessage = event => {
    let message;
    try { message = JSON.parse(event.data); } catch { return; }
    if (message.type === 'hello') {
      if (message.nonce !== nonce) return;
      token = message.token;
      hostVisible = message.visible;
      refresh();
      return;
    }
    if (!token || message.token !== token || suspended) return;
    switch (message.type) {
      case 'permission':
        allowed = message.allowed === true;
        stopPermissionWatch();
        if (!allowed) failAll(1, 'Location permission denied');
        refresh();
        break;
      case 'foreground':
        hostVisible = message.visible;
        refresh();
        break;
      case 'validated': {
        const request = requests.get(message.id);
        if (!request || !request.awaitingValidation) return;
        if (message.allowed !== true) {
          allowed = false;
          cache = null;
          failAll(1, 'Direct GPS requires Allow Location and precise location permission');
          return;
        }
        request.awaitingValidation = false;
        refresh();
        break;
      }
      case 'position': {
        if (!allowed || !visible()) return;
        const position = positionValue(message);
        cache = { position, acquiredAt: performance.now() - message.ageMs };
        for (const request of requests.values()) {
          if (!request.awaitingValidation) deliver(request, position);
        }
        refresh();
        break;
      }
      case 'error':
        if (message.code === 1) {
          allowed = false;
          cache = null;
          failAll(1, message.message);
        } else {
          for (const request of requests.values()) report(request, message.code, message.message);
        }
        refresh();
        break;
    }
  };

  function hello() {
    nonce = Array.from(crypto.getRandomValues(new Uint32Array(4)), n => n.toString(16)).join('-');
    bridge.postMessage(JSON.stringify({ type: 'hello', nonce }));
  }

  document.addEventListener('visibilitychange', refresh);
  window.addEventListener('pagehide', () => {
    suspended = true;
    refresh();
    stopPermissionWatch();
    post('release');
    token = null;
    allowed = false;
    cache = null;
    for (const request of requests.values()) {
      request.awaitingValidation = true;
      request.validationSent = false;
    }
  });
  window.addEventListener('pageshow', event => {
    if (!event.persisted) return;
    suspended = false;
    hello();
  });
  hello();
})();
