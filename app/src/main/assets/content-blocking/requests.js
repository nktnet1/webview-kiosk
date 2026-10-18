(function () {
  "use strict";
  if (!window.wkRequestRules || /^(?:file|content):$/.test(location.protocol)) return;
  // Window.origin is the security origin: it covers inherited about:blank/srcdoc
  // frames and stays opaque for sandboxed frames with an HTTP document URL.
  // Capture it at document start before page scripts can replace the property.
  var source = typeof window.origin === "string" ? window.origin : "";
  if (source === "null") source = "";
  var NativeURL = URL;
  var sourceHost = "";
  if (source) {
    try {
      var origin = new NativeURL(source);
      if (!/^https?:$/.test(origin.protocol)) return;
      source = origin.origin;
      sourceHost = origin.hostname;
    } catch (_) {
      source = "";
    }
  }
  if (
    ["appassets.androidplatform.net", "pdf-dummy.webviewkiosk.nktnet.uk"].indexOf(
      sourceHost,
    ) !== -1 ||
    window.__wkRequestsInstalled
  )
    return;
  Object.defineProperty(window, "__wkRequestsInstalled", { value: true });
  var bridge = window.wkRequestRules;
  var NativeException = DOMException;
  var apply = Reflect.apply;
  function blocked(value, type) {
    // Other coercible arguments retain the native API's coercion/error behavior.
    if (typeof value !== "string") return false;
    try {
      var url = new NativeURL(value, document.baseURI || location.href);
      if (type === "websocket") {
        if (url.protocol === "https:") url.protocol = "wss:";
        if (url.protocol === "http:") url.protocol = "ws:";
        if (!/^wss?:$/.test(url.protocol) || url.hash) return false;
      } else if (!/^https?:$/.test(url.protocol)) return false;
      return bridge.shouldBlock(url.href, source, type) === true;
    } catch (_) {
      return false;
    }
  }
  if (typeof window.WebSocket === "function") {
    window.WebSocket = new Proxy(window.WebSocket, {
      construct: function (target, args, derived) {
        if (blocked(args[0], "websocket")) {
          throw new NativeException("WebSocket blocked by content filtering", "SecurityError");
        }
        return Reflect.construct(target, args, derived);
      },
    });
  }
  if (window.navigator && typeof navigator.sendBeacon === "function") {
    navigator.sendBeacon = new Proxy(navigator.sendBeacon, {
      apply: function (target, receiver, args) {
        if (receiver === navigator && blocked(args[0], "ping")) return true;
        return apply(target, receiver, args);
      },
    });
  }
})();
