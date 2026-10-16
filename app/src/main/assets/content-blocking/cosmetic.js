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
  var procedures = new Map();
  var procedureCursor = 0;
  var procedurePending = false;
  var domProcedureCount = 0;
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
    if (!parent || (!selectors.size && !procedures.size)) return;
    if (!style || !style.isConnected) {
      style = document.createElement("style");
      parent.appendChild(style);
      installedSelectors.clear();
    }
    var sheet = style.sheet;
    if (!sheet) return;
    function insert(selector, declarations) {
      var key = selector + "{" + declarations + "}";
      if (installedSelectors.has(key)) return;
      try {
        // The Kotlin parser validates declarations; WebView validates selectors.
        selectorProbe.matches(selector);
        sheet.insertRule(key, sheet.cssRules.length);
      } catch (_) {
        // One unsupported selector must not invalidate other rules.
      }
      installedSelectors.add(key);
    }
    selectors.forEach(function (selector) {
      insert(selector, "display:none!important");
    });
    procedures.forEach(function (rule) {
      if (rule.style !== null) insert(rule.selector, rule.style);
    });
  }

  // Strictly declarative rules: filter text never becomes executable JavaScript.
  // Bound selector evaluation even on pages with very large/dynamic DOMs.
  function applyProcedures() {
    procedurePending = false;
    if (!domProcedureCount || !document.documentElement) return;
    var rules = Array.from(procedures.values());
    var budget = 2000;
    var deadline = Date.now() + 8;
    var processed = 0;
    for (; processed < rules.length && budget > 0; processed++) {
      if (Date.now() >= deadline) break;
      var rule = rules[(procedureCursor + processed) % rules.length];
      if (rule.style !== null) continue; // CSSOM applies these without DOM scans.
      try {
        selectorProbe.matches(rule.selector);
        var matches = document.querySelectorAll(rule.selector);
        for (var i = 0; i < matches.length && i < 256 && budget > 0; i++) {
          budget--;
          var element = matches[i];
          if (rule.text !== null &&
              (!element.textContent || element.textContent.indexOf(rule.text) < 0)) continue;
          for (var depth = 0; depth < rule.upward && element; depth++) {
            element = element.parentElement;
          }
          if (rule.upwardSelector && element) {
            element = element.parentElement && element.parentElement.closest(rule.upwardSelector);
          }
          if (!element || element === document.documentElement) continue;
          if (rule.removeAttribute) element.removeAttribute(rule.removeAttribute);
          else if (rule.removeClass) element.classList.remove(rule.removeClass);
          else if (rule.remove) element.remove();
          else element.style.setProperty("display", "none", "important");
        }
      } catch (_) {
        // A selector unsupported by this WebView cannot affect other rules.
      }
    }
    procedureCursor = (procedureCursor + processed) % rules.length;
    procedurePending = processed < rules.length;
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
    procedures.clear();
    domProcedureCount = 0;
    procedurePending = false;
  }

  function flush() {
    timer = null;
    if (stopped) return;
    scan();
    applySelectors();
    applyProcedures();
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
    else if (procedurePending) schedule(120);
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
    (response.procedures || []).forEach(function (entry) {
      if (!entry || typeof entry.selector !== "string" || entry.selector.length > 256 ||
          !(entry.text === null || typeof entry.text === "string") ||
          (entry.text !== null && entry.text.length > 128) ||
          !Number.isInteger(entry.upward) || entry.upward < 0 || entry.upward > 4 ||
          typeof entry.remove !== "boolean" ||
          !(entry.style === null ||
            (typeof entry.style === "string" && entry.style.length <= 256)) ||
          !(entry.removeAttribute === null || typeof entry.removeAttribute === "string") ||
          !(entry.removeClass === null || typeof entry.removeClass === "string") ||
          !(entry.upwardSelector === null ||
            (typeof entry.upwardSelector === "string" && entry.upwardSelector.length <= 128)) ||
          procedures.size >= 128) return;
      var key = JSON.stringify(entry);
      if (!procedures.has(key)) {
        procedures.set(key, entry);
        if (entry.style === null) domProcedureCount++;
      }
    });
    applySelectors();
    applyProcedures();
    if (classes.size || ids.size || work.length || procedurePending) schedule();
  };

  var observer = new MutationObserver(function (records) {
    if (stopped) return;
    records.forEach(function (record) {
      if (record.type === "attributes") queue(record.target);
      else if (record.type === "characterData") queue(record.target.parentElement);
      else record.addedNodes.forEach(queue);
    });
    if (style && !style.isConnected) schedule();
    if (domProcedureCount) schedule(120);
  });
  observer.observe(document, { childList: true, characterData: true, subtree: true,
    attributes: true, attributeFilter: ["class", "id"] });
  window.addEventListener("pagehide", function () {
    observer.disconnect();
    if (timer !== null) clearTimeout(timer);
    timer = null;
  });
  window.addEventListener("pageshow", function () {
    if (stopped) return;
    observer.observe(document, { childList: true, characterData: true, subtree: true,
    attributes: true, attributeFilter: ["class", "id"] });
    queue(document);
  });
  queue(document);
})();
