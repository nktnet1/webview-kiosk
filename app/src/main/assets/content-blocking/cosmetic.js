(function () {
  "use strict";
  if (!/^https?:$/.test(location.protocol) || !window.wkCosmeticRules) return;
  // The same private asset hosts are excluded by WebViewBlocker on the app side.
  if (["appassets.androidplatform.net", "pdf-dummy.webviewkiosk.nktnet.uk"].indexOf(location.hostname) !== -1) return;
  if (window.__wkCosmeticsInstalled) return;
  Object.defineProperty(window, "__wkCosmeticsInstalled", { value: true });

  var bridge = window.wkCosmeticRules;
  var seenClasses = new Set();
  var seenIds = new Set();
  var classes = new Set();
  var ids = new Set();
  var selectors = new Set();
  var installedSelectors = new Set();
  var selectorProbe = document.createElement("div");
  var work = [];
  var style = null;
  var timer = null;
  var sequence = 0;
  var inFlight = null;
  var initial = true;
  var stopped = false;

  function schedule(delay) {
    if (stopped || timer !== null) return;
    timer = setTimeout(flush, delay === undefined ? 50 : delay);
  }

  function queue(root) {
    if (!root || root === style || (root.nodeType !== 1 && root.nodeType !== 9)) return;
    if (work.some(function (item) { return item.root.contains(root); })) return;
    work = work.filter(function (item) { return !root.contains(item.root); });
    if (work.length >= 64) work = [{ root: document, walker: null }];
    else work.push({ root: root, walker: null });
    schedule();
  }

  function remember(value, seen, pending) {
    if (!value || value.length > 128 || /[\x00-\x1f\x7f-\x9f]/.test(value) ||
        seen.size >= 8192 || seen.has(value)) return;
    seen.add(value);
    pending.add(value);
  }

  function collect(element) {
    if (element.nodeType !== 1 || element === style) return;
    remember(element.id, seenIds, ids);
    if (element.classList) element.classList.forEach(function (name) {
      remember(name, seenClasses, classes);
    });
  }

  function scan() {
    var budget = 400;
    while (work.length && budget > 0) {
      var item = work[0];
      if (!item.walker) {
        collect(item.root);
        budget--;
        item.walker = document.createTreeWalker(item.root, 1);
      }
      var element;
      while (budget > 0 && (element = item.walker.nextNode())) {
        collect(element);
        budget--;
      }
      if (budget > 0) work.shift();
    }
  }

  function applySelectors() {
    var parent = document.head || document.documentElement;
    if (!parent || !selectors.size) return;
    if (!style || !style.isConnected) {
      style = document.createElement("style");
      parent.appendChild(style);
      installedSelectors.clear();
    }
    var sheet = style.sheet;
    if (!sheet) return;
    selectors.forEach(function (selector) {
      if (installedSelectors.has(selector)) return;
      try {
        // Validate as a selector first: reject @import, injected declarations,
        // malformed CSS, and syntax this WebView does not support.
        selectorProbe.matches(selector);
        sheet.insertRule(selector + "{display:none!important}", sheet.cssRules.length);
      } catch (_) {
        // One bad/unsupported selector must not invalidate the other rules.
      }
      installedSelectors.add(selector);
    });
  }

  function take(values) {
    var batch = [];
    values.forEach(function (value) {
      if (batch.length < 256) batch.push(value);
    });
    batch.forEach(function (value) { values.delete(value); });
    return batch;
  }

  function stop() {
    stopped = true;
    observer.disconnect();
    if (timer !== null) clearTimeout(timer);
    timer = null;
    work = [];
    classes.clear();
    ids.clear();
    if (style) style.remove();
  }

  function flush() {
    timer = null;
    if (stopped) return;
    scan();
    applySelectors();
    if (!inFlight && (initial || classes.size || ids.size)) {
      inFlight = {
        id: ++sequence,
        url: location.href,
        classes: take(classes),
        ids: take(ids)
      };
      initial = false;
      bridge.postMessage(JSON.stringify(inFlight));
    }
    if (work.length) schedule(16);
  }

  bridge.onmessage = function (event) {
    if (stopped || !inFlight) return;
    var response;
    try { response = JSON.parse(event.data); } catch (_) { return; }
    if (response.id !== inFlight.id) return;
    var batch = inFlight;
    inFlight = null;
    if (response.retry) {
      batch.classes.forEach(function (name) { classes.add(name); });
      batch.ids.forEach(function (name) { ids.add(name); });
      initial = true;
      schedule(250);
      return;
    }
    if (!response.active) { stop(); return; }
    (response.selectors || []).forEach(function (selector) {
      if (typeof selector === "string" && selector.length <= 4096 && selectors.size < 8192) {
        selectors.add(selector);
      }
    });
    applySelectors();
    if (classes.size || ids.size || work.length) schedule();
  };

  var observer = new MutationObserver(function (records) {
    if (stopped) return;
    records.forEach(function (record) {
      if (record.type === "attributes") queue(record.target);
      else record.addedNodes.forEach(queue);
    });
    if (style && !style.isConnected) schedule();
  });
  observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "id"] });
  window.addEventListener("pagehide", function () {
    observer.disconnect();
    if (timer !== null) clearTimeout(timer);
    timer = null;
  });
  window.addEventListener("pageshow", function () {
    if (stopped) return;
    observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "id"] });
    queue(document);
  });
  queue(document);
})();
