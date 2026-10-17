(function () {
  "use strict";
  if (!/^https?:$/.test(location.protocol) || !window.wkRequestRules) return;
  if (
    ["appassets.androidplatform.net", "pdf-dummy.webviewkiosk.nktnet.uk"].indexOf(
      location.hostname,
    ) !== -1 ||
    window.__wkRequestsInstalled
  )
    return;
  Object.defineProperty(window, "__wkRequestsInstalled", { value: true });
  var bridge = window.wkRequestRules;
  var NativeURL = URL;
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
      return bridge.shouldBlock(url.href, location.href, type) === true;
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
