(function () {
  "use strict";
  if (!/^https?:$/.test(location.protocol) || !window.wkCosmeticRules) return;
  // The same private asset hosts are excluded by WebViewBlocker on the app side.
  if (["appassets.androidplatform.net", "pdf-dummy.webviewkiosk.nktnet.uk"]
      .indexOf(location.hostname) !== -1) return;
  if (window.__wkCosmeticsInstalled) return;
  Object.defineProperty(window, "__wkCosmeticsInstalled", { value: true });

  var bridge = window.wkCosmeticRules;
  // Installed before scriptlets: cosmetic work must keep using native timers.
  var scheduleTimeout = setTimeout.bind(window);
  var cancelTimeout = clearTimeout.bind(window);
  var regexTest = Function.prototype.call.bind(RegExp.prototype.test);
  var seenClasses = new Set();
  var seenIds = new Set();
  var classes = new Set();
  var ids = new Set();
  var selectors = new Set();
  var installedSelectors = new Set();
  var procedures = new Map();
  var candidates = new Map();
  var relations = new Set();
  var relationTasks = new Map();
  var siblingRelations = false;
  var descendantRefresh = false;
  var styleSubjects = new Set();
  var pathSubjects = new Set();
  var invalidations = new Map();
  var invalidateAgain = new Set();
  var historyInstalled = false;
  var observingProcedures = false;
  var hideTags = new Set();
  var inlineBackups = new Map();
  var ownStyles = new WeakMap();
  var regexes = new Map();
  var xpathExpressions = new Map();
  var scopedObservers = new Map();
  var boundaryOwners = new WeakMap();
  var shadowOwners = new Map();
  var ownStyleNodes = new WeakSet();
  var restoreAttachShadow = null;
  var nativeShadow = getter(window.Element, "shadowRoot");
  var nativeContent = getter(window.HTMLTemplateElement, "content");
  var nativeRoot = window.Node && window.Node.prototype.getRootNode;
  var nativeFragmentQuery = window.DocumentFragment &&
    window.DocumentFragment.prototype.querySelectorAll;
  var nativeExpression = document.createExpression && document.createExpression.bind(document);
  var attribute = "data-wk-cosmetic-" + Math.random().toString(36).slice(2);
  var effects = new Map();
  var suspended = false;
  var nativeStyle = window.getComputedStyle && window.getComputedStyle.bind(window);
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
    if (stopped || suspended || timer !== null) return;
    timer = scheduleTimeout(flush, delay === undefined ? 50 : delay);
  }

  function queue(root) {
    if (!root || ownStyleNodes.has(root) ||
        (root.nodeType !== 1 && root.nodeType !== 9)) return;
    if (work.some(function (item) { return !item.walker && item.root.contains(root); })) return;
    work = work.filter(function (item) { return !root.contains(item.root); });
    if (work.length >= 64 && root.isConnected !== false) {
      work = work.filter(function (item) { return item.root.isConnected === false; });
      work.push({ root: document, walker: null });
    }
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
    if (element.nodeType !== 1 || ownStyleNodes.has(element)) return;
    if (domProcedureCount && !candidates.has(element)) candidates.set(element, 0);
    if (element.isConnected === false) return;
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
      ownStyleNodes.add(style);
      parent.appendChild(style);
      installedSelectors.clear();
    }
    var sheet = style.sheet;
    if (!sheet) return;
    function insert(selector, declarations, media) {
      var key = selector + "{" + declarations + "}";
      if (media) { key = "@media " + media + "{" + key + "}"; }
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
      if (rule.style !== null) insert(rule.selector, rule.style, rule.media);
      else if (rule._tag) insert("[" + attribute + '~="' + rule._tag + '"]', rule._declarations);
    });
    scopedObservers.forEach(function (scope) {
      if (scope.styled) applyScopedSelectors(scope);
    });
  }

  function getter(type, name) {
    var descriptor = type && Object.getOwnPropertyDescriptor(type.prototype, name);
    return descriptor && descriptor.get;
  }

  function rootOf(node) {
    return nativeRoot ? Reflect.apply(nativeRoot, node, []) : document;
  }

  function live(node) {
    for (var depth = 0; node && depth < 32; depth++) {
      if (node.isConnected) return true;
      node = boundaryOwners.get(rootOf(node));
    }
    return false;
  }

  function ownMutation(record) {
    if (ownStyleNodes.has(record.target) || record.attributeName === attribute) return true;
    if (record.type === "attributes" && record.attributeName === "style" &&
        trackDisplayMutation(record.target)) return true;
    if (record.type !== "childList") return false;
    var changed = Array.from(record.addedNodes).concat(Array.from(record.removedNodes));
    return changed.length !== 0 && changed.every(function (node) {
      return ownStyleNodes.has(node);
    });
  }

  function trackDisplayMutation(element) {
    var currentStyle = element.getAttribute("style");
    if (ownStyles.has(element) && ownStyles.get(element) === currentStyle) return true;
    ownStyles.delete(element);
    var backup = inlineBackups.get(element);
    if (backup && (element.style.getPropertyValue("display") !== "none" ||
        element.style.getPropertyPriority("display") !== "important")) {
      backup.value = element.style.getPropertyValue("display");
      backup.priority = element.style.getPropertyPriority("display");
      backup.hadStyle = element.hasAttribute("style");
    }
    return false;
  }

  function observerOptions() {
    return { childList: true, characterData: true, subtree: true, attributes: true };
  }

  function applyScopedSelectors(scope) {
    if (!scope.root.isConnected) return;
    if (!scope.style || scope.style.parentNode !== scope.root) {
      scope.style = document.createElement("style");
      ownStyleNodes.add(scope.style);
      scope.root.appendChild(scope.style);
      scope.installed.clear();
    }
    var sheet = scope.style.sheet;
    if (!sheet) return;
    procedures.forEach(function (rule) {
      if (!rule._tag || scope.installed.has(rule._tag)) return;
      try {
        sheet.insertRule("[" + attribute + '~="' + rule._tag + '"]{' +
          rule._declarations + "}", sheet.cssRules.length);
        scope.installed.add(rule._tag);
      } catch (_) {}
    });
  }

  function addDependency(map, node, seed, observed) {
    var scope = map.get(node);
    if (!scope) {
      scope = { root: node, counts: new Map(), subjects: new Set(),
        installed: new Set(), styled: false, style: null };
      if (observed) {
        scope.observer = new MutationObserver(function (records) {
          if (stopped || suspended) return;
          var changed = false;
          records.forEach(function (record) {
            if (!ownMutation(record)) changed = true;
          });
          if (changed || scope.style && scope.style.parentNode !== scope.root) {
            invalidate(scope.subjects);
          }
        });
        scope.observer.observe(node, observerOptions());
      }
      map.set(node, scope);
    }
    scope.counts.set(seed, (scope.counts.get(seed) || 0) + 1);
    scope.subjects.add(seed);
  }

  function removeDependency(map, node, seed) {
    var scope = map.get(node);
    if (!scope) return;
    var count = scope.counts.get(seed) - 1;
    if (count > 0) { scope.counts.set(seed, count); return; }
    scope.counts.delete(seed);
    scope.subjects.delete(seed);
    if (scope.subjects.size) return;
    if (scope.observer) scope.observer.disconnect();
    if (scope.style) scope.style.remove();
    invalidations.delete(scope.subjects);
    invalidateAgain.delete(scope.subjects);
    map.delete(node);
  }

  function dependencies(rule, seed, budget) {
    [[rule._scopes, budget.scopes, scopedObservers, true],
      [rule._owners, budget.owners, shadowOwners, false]].forEach(function (entry) {
      var before = entry[0].get(seed) || new Set(), after = entry[1];
      before.forEach(function (node) {
        if (!after.has(node)) removeDependency(entry[2], node, seed);
      });
      after.forEach(function (node) {
        if (!before.has(node)) addDependency(entry[2], node, seed, entry[3]);
      });
      if (after.size) entry[0].set(seed, after);
      else entry[0].delete(seed);
    });
  }

  function installShadowHook() {
    if (restoreAttachShadow || !nativeShadow || !window.Element) return;
    var prototype = window.Element.prototype;
    var descriptor = Object.getOwnPropertyDescriptor(prototype, "attachShadow");
    if (!descriptor || typeof descriptor.value !== "function" ||
        !descriptor.configurable && !descriptor.writable) return;
    var hooked = new Proxy(descriptor.value, { apply: function (target, receiver, args) {
      var result = Reflect.apply(target, receiver, args);
      // Use the native getter, never the returned root: closed roots stay opaque.
      if (!stopped && Reflect.apply(nativeShadow, receiver, [])) {
        var scope = shadowOwners.get(receiver);
        if (scope) invalidate(scope.subjects);
      }
      return result;
    } });
    Object.defineProperty(prototype, "attachShadow",
      Object.assign({}, descriptor, { value: hooked }));
    restoreAttachShadow = function () {
      var current = Object.getOwnPropertyDescriptor(prototype, "attachShadow");
      if (current && current.value === hooked) {
        Object.defineProperty(prototype, "attachShadow", descriptor);
      }
    };
  }

  function xpathScope(element, expression) {
    var syntax = expression.replace(/'[^']*'|"[^"]*"/g, "q").trim();
    var scope = element;
    while (syntax === ".." || syntax.indexOf("../") === 0) {
      scope = scope.parentNode || scope;
      syntax = syntax.slice(2).replace(/^\//, "");
    }
    if (/\.\.|(?:ancestor|parent|following|preceding)(?:-or-self|-sibling)?\s*::/.test(syntax) ||
        syntax.indexOf("|") !== -1) return rootOf(element);
    return scope;
  }

  function testPattern(pattern, value, exact) {
    if (typeof value !== "string" || value.length > 65536) return false;
    if (pattern[0] === "/" && pattern.lastIndexOf("/") > 0 &&
        /^[im]*$/.test(pattern.slice(pattern.lastIndexOf("/") + 1))) {
      var end = pattern.lastIndexOf("/");
      var re = regexes.get(pattern);
      if (!re) {
        re = new RegExp(pattern.slice(1, end), pattern.slice(end + 1));
        regexes.set(pattern, re);
      }
      return regexTest(re, value);
    }
    return exact ? value === pattern : value.indexOf(pattern) !== -1;
  }

  function relative(element, selector, suffix) {
    selector = selector.trim();
    if (/^[+~]/.test(selector) || suffix && selector[0] === ':') {
      var parent = element.parentElement;
      if (!parent) return [];
      var index = Array.prototype.indexOf.call(parent.children, element) + 1;
      return parent.querySelectorAll(":scope > :nth-child(" + index + ")" + selector);
    }
    return element.querySelectorAll(selector[0] === ">" ? ":scope " + selector : selector);
  }

  function evaluate(program, input, budget) {
    var values = input;
    program.steps.forEach(function (step) {
      var next = new Set();
      values.forEach(function (element) {
        if (--budget.remaining < 0) throw new Error("Cosmetic work budget");
        var operation = step[0], argument = step[1];
        if (operation === "css") {
          Array.from(relative(element, argument, true)).forEach(function (node) {
            next.add(node);
          });
        } else if (operation === "shadow" || operation === "content") {
          var accessor = operation === "shadow" ? nativeShadow : nativeContent;
          if (operation === "shadow") budget.owners.add(element);
          var root;
          try { root = accessor && Reflect.apply(accessor, element, []); } catch (_) {}
          if (root && nativeFragmentQuery) {
            boundaryOwners.set(root, element);
            budget.scopes.add(root);
            Array.from(Reflect.apply(nativeFragmentQuery, root, [argument]))
              .forEach(function (node) { next.add(node); });
          }
        } else if (operation === "xpath") {
          budget.scopes.add(xpathScope(element, argument));
          var expression = xpathExpressions.get(argument);
          var result = expression.evaluate(element, 7, null);
          if (result.snapshotLength > 1024) throw new Error("Cosmetic candidate budget");
          for (var n = 0; n < result.snapshotLength; n++) {
            var node = result.snapshotItem(n);
            if (node && node.nodeType === 1) next.add(node);
          }
        } else if (operation === "upward") {
          var count = /^[0-9]+$/.test(argument) ? Number(argument) : null;
          if (count === null) element = element.parentElement &&
            element.parentElement.closest(argument);
          else for (var i = 0; element && i < count; i++) element = element.parentElement;
          if (element) next.add(element);
        } else if (operation === "has" || operation === "has-not" || operation === "not") {
          var nested = argument.selector === "" || operation === "not" ? [element] :
            Array.from(relative(element, argument.selector));
          if (nested.length > 1024) throw new Error("Cosmetic candidate budget");
          var matched = evaluate(argument, nested, budget).length !== 0;
          if (matched === (operation === "has")) next.add(element);
        } else if (operation === "text") {
          if (testPattern(argument, element.textContent, false)) next.add(element);
        } else if (operation === "length") {
          var text = element.textContent;
          if (typeof text === "string" && text.length >= Number(argument)) next.add(element);
        } else if (operation === "attr") {
          if (Array.from(element.attributes).some(function (entry) {
            return testPattern(argument, entry.name, true) &&
              (!step[2] || testPattern(step[2], entry.value, true));
          })) next.add(element);
        } else if (operation === "style") {
          if (nativeStyle && testPattern(step[2],
              authorStyle(element, step[3], argument), true)) {
            next.add(element);
          }
        } else if (operation === "path") {
          var path = location.pathname + location.search;
          if (testPattern(argument, path, false)) next.add(element);
        } else if (operation === "watch") next.add(element);
      });
      if (next.size > 1024) throw new Error("Cosmetic candidate budget");
      values = Array.from(next);
    });
    return values;
  }

  function authorStyle(element, pseudo, property) {
    var saved = [];
    // Read author styles without feedback from this runner's reversible effects.
    // Only affected elements/ancestors change, and private attributes are ignored
    // by the observer. No document-wide stylesheet toggling is needed.
    for (var node = element; node; node = node.parentElement) {
      if (node.hasAttribute(attribute)) {
        saved.push([node, node.getAttribute(attribute)]);
        node.removeAttribute(attribute);
      }
      var backup = inlineBackups.get(node);
      if (backup) restoreDisplay(node, backup);
    }
    try { return nativeStyle(element, pseudo || null).getPropertyValue(property); }
    finally {
      saved.forEach(function (item) { item[0].setAttribute(attribute, item[1]); });
      for (var current = element; current; current = current.parentElement) {
        if (inlineBackups.has(current)) {
          current.style.setProperty("display", "none", "important");
          ownStyles.set(current, current.getAttribute("style"));
        }
      }
    }
  }

  function invalidate(subjects) {
    if (invalidations.has(subjects)) invalidateAgain.add(subjects);
    else invalidations.set(subjects, subjects.values());
    if (subjects.size) schedule();
  }

  function effect(element, tag, delta) {
    var scope = scopedObservers.get(rootOf(element));
    if (scope && scope.root.nodeType === 11 && delta > 0) {
      scope.styled = true;
      applyScopedSelectors(scope);
    }
    var tags = effects.get(element) || new Map();
    var count = (tags.get(tag) || 0) + delta;
    if (count > 0) tags.set(tag, count);
    else tags.delete(tag);
    var hidden = Array.from(tags.keys()).some(function (name) { return hideTags.has(name); });
    var backup = inlineBackups.get(element);
    if (hidden && element.style) {
      if (!backup) inlineBackups.set(element, {
        value: element.style.getPropertyValue("display"),
        priority: element.style.getPropertyPriority("display"),
        hadStyle: element.hasAttribute("style")
      });
      element.style.setProperty("display", "none", "important");
      ownStyles.set(element, element.getAttribute("style"));
    } else if (backup) {
      restoreDisplay(element, backup);
      ownStyles.set(element, element.getAttribute("style"));
      inlineBackups.delete(element);
    }
    if (tags.size) {
      effects.set(element, tags);
      element.setAttribute(attribute, Array.from(tags.keys()).join(" "));
    } else {
      effects.delete(element);
      element.removeAttribute(attribute);
    }
  }

  function restoreDisplay(element, backup) {
    if (backup.value) element.style.setProperty("display", backup.value, backup.priority);
    else element.style.removeProperty("display");
    if (!backup.hadStyle && !element.style.length) element.removeAttribute("style");
  }

  function applyRule(rule, seed) {
    var before = rule._hits.get(seed) || new Set();
    var after = new Set();
    var budget = { remaining: 4000, scopes: new Set(), owners: new Set() };
    if (!live(seed)) { styleSubjects.delete(seed); pathSubjects.delete(seed); }
    if (live(seed) && seed.matches(rule.selector)) {
      if (rule._style) styleSubjects.add(seed);
      if (rule._path) pathSubjects.add(seed);
      after = new Set(evaluate(rule._program, [seed], budget).filter(function (node) {
        // Inert contents can match predicates or explicit removal actions, but
        // reversible styles must never be copied from templates into untracked clones.
        return node && (rule._tag ? node.isConnected : live(node)) &&
          node !== document.documentElement;
      }));
    }
    dependencies(rule, seed, budget);
    if (rule._tag) {
      before.forEach(function (element) {
        if (!after.has(element)) effect(element, rule._tag, -1);
      });
      after.forEach(function (element) {
        if (!before.has(element)) effect(element, rule._tag, 1);
      });
      if (after.size) rule._hits.set(seed, after);
      else rule._hits.delete(seed);
    } else after.forEach(function (element) {
      var action = rule._program.action;
      if (action[0] === "remove") element.remove();
      else if (action[0] === "remove-attr" && element.hasAttribute(action[1])) {
        element.removeAttribute(action[1]);
      } else if (action[0] === "remove-class" && element.classList.contains(action[1])) {
        element.classList.remove(action[1]);
      }
    });
  }

  // Scan each new/changed subtree once. Text and child changes revisit ancestors
  // and sibling subjects without scanning their unchanged descendants.
  function applyProcedures() {
    procedurePending = false;
    if (!domProcedureCount || !document.documentElement) return;
    var contextBudget = 200, contextDeadline = Date.now() + 4;
    for (var invalidation of invalidations) {
      var cursor = invalidation[1], item;
      while (contextBudget-- > 0 && Date.now() < contextDeadline) {
        item = cursor.next();
        if (item.done) break;
        candidates.set(item.value, 0);
      }
      if (!item || !item.done) break;
      invalidations.delete(invalidation[0]);
      if (invalidateAgain.delete(invalidation[0])) {
        invalidations.set(invalidation[0], invalidation[0].values());
        break;
      }
    }
    while (relations.size && contextBudget-- > 0 && Date.now() < contextDeadline) {
      var node = relations.values().next().value;
      relations.delete(node);
      if (!node) continue;
      candidates.set(node, 0);
      if (node.parentElement) {
        relations.add(node.parentElement);
        if (siblingRelations && !relationTasks.has(node.parentElement)) {
          relationTasks.set(node.parentElement, node.parentElement.firstElementChild);
        }
      }
    }
    for (var task of relationTasks) {
      var sibling = task[1];
      while (sibling && contextBudget-- > 0 && Date.now() < contextDeadline) {
        if (!candidates.has(sibling)) candidates.set(sibling, 0);
        sibling = sibling.nextElementSibling;
      }
      if (sibling) { relationTasks.set(task[0], sibling); break; }
      relationTasks.delete(task[0]);
    }
    var rules = Array.from(procedures.values()).filter(function (rule) {
      return rule.style === null;
    });
    var remaining = 400, deadline = Date.now() + 8;
    for (var entry of candidates) {
      var element = entry[0], index = entry[1];
      while (index < rules.length && remaining-- > 0 && Date.now() < deadline) {
        try { applyRule(rules[index], element); } catch (_) {}
        index++;
      }
      if (index === rules.length) candidates.delete(element);
      else { candidates.set(element, index); break; }
    }
    procedurePending = candidates.size !== 0 || relations.size !== 0 ||
      relationTasks.size !== 0 || invalidations.size !== 0;
  }

  function prepareProcedure(entry) {
    var program;
    if (entry.operations) program = JSON.parse(entry.operations);
    else {
      var steps = [];
      if (entry.text !== null) {
        if (entry.textSelector) steps.push(["has", {
          selector: entry.textSelector, steps: [["text", entry.text]]
        }]);
        else steps.push(["text", entry.text]);
      }
      if (entry.upward) steps.push(["upward", String(entry.upward)]);
      if (entry.upwardSelector) steps.push(["upward", entry.upwardSelector]);
      program = { selector: entry.selector, steps: steps,
        action: entry.remove ? ["remove", ""] : entry.removeAttribute
          ? ["remove-attr", entry.removeAttribute] : entry.removeClass
            ? ["remove-class", entry.removeClass] : ["hide", ""] };
    }
    function hasOperation(current, name) {
      return current.steps.some(function (step) {
        return step[0] === name || step[1] && typeof step[1] === "object" &&
          hasOperation(step[1], name);
      });
    }
    function siblings(current) {
      return /[+~]|:nth-|:first-|:last-|:only-/.test(current.selector) ||
        current.steps.some(function (step) {
          return step[0] === "css" && /[+~]|:nth-|:first-|:last-|:only-/.test(step[1]) ||
            step[1] && typeof step[1] === "object" && siblings(step[1]);
        });
    }
    function prepareSteps(current) {
      current.steps.forEach(function (step) {
        if (step[0] === "xpath") {
          if (!nativeExpression) throw new Error("XPath unavailable");
          if (!xpathExpressions.has(step[1])) {
            xpathExpressions.set(step[1], nativeExpression(step[1], null));
          }
        }
        if (step[1] && typeof step[1] === "object") prepareSteps(step[1]);
      });
    }
    prepareSteps(program);
    if (hasOperation(program, "shadow")) installShadowHook();
    entry._scopes = new Map();
    entry._owners = new Map();
    siblingRelations = siblingRelations || siblings(program);
    descendantRefresh = descendantRefresh ||
      /[ >+~]|:nth-|:first-|:last-|:only-/.test(entry.selector);
    entry._program = program;
    entry._style = hasOperation(program, "style");
    entry._path = hasOperation(program, "path");
    if (entry._path && !historyInstalled && window.history) {
      historyInstalled = true;
      ["pushState", "replaceState"].forEach(function (name) {
        var native = window.history[name];
        if (typeof native !== "function") return;
        window.history[name] = new Proxy(native, { apply: function (target, receiver, args) {
          var result = Reflect.apply(target, receiver, args);
          invalidate(pathSubjects);
          return result;
        } });
      });
    }
    entry._hits = new Map();
    if (program.action[0] === "hide" || program.action[0] === "style-action") {
      entry._tag = "r" + procedures.size;
      if (program.action[0] === "hide") hideTags.add(entry._tag);
      entry._declarations = program.action[0] === "hide"
        ? "display:none!important" : program.action[1];
    }
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
    scopedObservers.forEach(function (scope) {
      scope.observer.disconnect();
      if (scope.style) scope.style.remove();
    });
    scopedObservers.clear();
    shadowOwners.clear();
    xpathExpressions.clear();
    if (restoreAttachShadow) restoreAttachShadow();
    if (timer !== null) cancelTimeout(timer);
    timer = null;
    work = [];
    candidates.clear();
    relations.clear();
    relationTasks.clear();
    invalidations.clear();
    invalidateAgain.clear();
    styleSubjects.clear();
    pathSubjects.clear();
    effects.forEach(function (_, element) { element.removeAttribute(attribute); });
    effects.clear();
    inlineBackups.forEach(function (backup, element) { restoreDisplay(element, backup); });
    inlineBackups.clear();
    regexes.clear();
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
          !(entry.textSelector == null ||
            (typeof entry.textSelector === "string" && entry.textSelector.length <= 256)) ||
          !(entry.textPattern === undefined || typeof entry.textPattern === "boolean") ||
          (entry.text !== null && entry.text.length > 128) ||
          !Number.isInteger(entry.upward) || entry.upward < 0 || entry.upward > 4 ||
          typeof entry.remove !== "boolean" ||
          !(entry.style === null ||
            (typeof entry.style === "string" && entry.style.length <= 256)) ||
          !(entry.media == null || (typeof entry.media === "string" &&
            entry.media.length <= 256 && /^[A-Za-z0-9 ():.%+,-]+$/.test(entry.media) &&
            typeof entry.style === "string")) ||
          !(entry.removeAttribute === null || typeof entry.removeAttribute === "string") ||
          !(entry.removeClass === null || typeof entry.removeClass === "string") ||
          !(entry.upwardSelector === null ||
            (typeof entry.upwardSelector === "string" && entry.upwardSelector.length <= 128)) ||
          !(entry.operations == null || (typeof entry.operations === "string" &&
            entry.operations.length <= 16384)) || procedures.size >= 128) return;
      var key = JSON.stringify(entry);
      if (!procedures.has(key)) {
        if (entry.textPattern) {
          var end = entry.text.lastIndexOf("/");
          if (entry.text[0] !== "/" || end <= 0) return;
          try {
            entry._textRegex = new RegExp(entry.text.slice(1, end), entry.text.slice(end + 1));
          }
          catch (_) { return; }
        }
        if (entry.style === null) {
          try { prepareProcedure(entry); } catch (_) { return; }
          domProcedureCount++;
          queue(document);
        }
        procedures.set(key, entry);
      }
    });
    if (domProcedureCount && !observingProcedures) {
      observingProcedures = true;
      observe();
    }
    applySelectors();
    applyProcedures();
    if (classes.size || ids.size || work.length || procedurePending) schedule();
  };

  var observer = new MutationObserver(function (records) {
    if (stopped || suspended) return;
    records.forEach(function (record) {
      if (record.type === "attributes") {
        if (record.target === style || record.attributeName === attribute) return;
        if (record.attributeName === "style") {
          if (trackDisplayMutation(record.target)) return;
        }
        if (styleSubjects.size) invalidate(styleSubjects);
        collect(record.target);
        if (domProcedureCount) {
          candidates.set(record.target, 0);
          relations.add(record.target);
          if (descendantRefresh) queue(record.target);
        }
        if (classes.size || ids.size) schedule();
      } else if (record.type === "characterData") {
        if (domProcedureCount) relations.add(record.target.parentElement);
        if (record.target.parentElement && record.target.parentElement.localName === "style") {
          invalidate(styleSubjects);
        }
      } else {
        if (record.target && record.target.localName === "style" && record.target !== style) {
          invalidate(styleSubjects);
        }
        Array.from(record.addedNodes).concat(Array.from(record.removedNodes || []))
          .forEach(function (node) {
            if (node !== style && ["style", "link"].indexOf(node.localName) !== -1) {
              invalidate(styleSubjects);
            }
          });
        record.addedNodes.forEach(queue);
        if (domProcedureCount) {
          if (record.target) {
            relations.add(record.target);
            if (siblingRelations) queue(record.target);
          }
          (record.removedNodes || []).forEach(queue);
        }
      }
    });
    if (style && !style.isConnected || domProcedureCount && relations.size) schedule();
  });
  function observe() {
    var options = observerOptions();
    if (!domProcedureCount) options.attributeFilter = ["class", "id"];
    observer.observe(document, options);
  }
  observe();
  window.addEventListener("pagehide", function () {
    suspended = true;
    observer.disconnect();
    scopedObservers.forEach(function (scope) { scope.observer.disconnect(); });
    if (timer !== null) cancelTimeout(timer);
    timer = null;
  });
  window.addEventListener("pageshow", function () {
    if (stopped) return;
    suspended = false;
    observe();
    scopedObservers.forEach(function (scope) {
      scope.observer.observe(scope.root, observerOptions());
      invalidate(scope.subjects);
    });
    queue(document);
  });
  window.addEventListener("resize", function () {
    invalidate(styleSubjects);
  });
  window.addEventListener("popstate", function () {
    invalidate(pathSubjects);
  });
  window.addEventListener("load", function (event) {
    if (event.target && event.target.localName === "link") invalidate(styleSubjects);
  }, true);
  queue(document);
})();
