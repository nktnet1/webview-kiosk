/* Function expression invoked by ImageRefreshScript with a quoted URL. */
(function (targetUrl) {
  'use strict';
  var parameter = '_webviewkiosk_refresh';
  var token = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
  var refreshed = false;

  function resolve(value, base) {
    if (!value) return null;
    try { return new URL(value, base); } catch (_) { return null; }
  }

  function refreshUrl(url) {
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return url.href;
    var hashIndex = url.href.indexOf('#');
    var hash = hashIndex < 0 ? '' : url.href.slice(hashIndex);
    var original = hashIndex < 0 ? url.href : url.href.slice(0, hashIndex);
    var queryIndex = original.indexOf('?');
    var path = queryIndex < 0 ? original : original.slice(0, queryIndex);
    var query = queryIndex < 0 ? '' : original.slice(queryIndex + 1);
    var parts = query ? query.split('&') : [];
    // Common signed URLs cannot accept additional query parameters. Retry the
    // original URL instead, although WebView may serve it from its cache.
    for (var i = 0; i < parts.length; i++) {
      var key = parts[i].split('=')[0];
      try { key = decodeURIComponent(key); } catch (_) { /* Keep malformed keys. */ }
      if (/^(?:sig|signature|x-amz-signature|x-goog-signature|hmac|hdnts|hdnea)$/i.test(key)) {
        return url.href;
      }
    }
    // Do not use URLSearchParams to serialize the existing query. Only replace
    // our own parameter, preserving all other bytes, ordering and duplicates.
    parts = parts.filter(function (part) { return part.split('=')[0] !== parameter; });
    parts.push(parameter + '=' + token);
    return path + '?' + parts.join('&') + hash;
  }

  function isSpace(character) {
    return /[\t\n\f\r ]/.test(character);
  }

  function refreshSrcset(element, base, target, replacement) {
    var srcset = element.getAttribute('srcset');
    if (!srcset) return false;
    var cursor = 0;
    var copied = 0;
    var result = '';
    var matched = false;
    // Follow srcset URL tokenization: commas inside a URL are not separators.
    // A trailing comma ends a URL token, while descriptors end at a comma
    // outside parentheses. Preserve the original separators and descriptors.
    while (cursor < srcset.length) {
      while (cursor < srcset.length && (isSpace(srcset[cursor]) || srcset[cursor] === ',')) cursor++;
      if (cursor === srcset.length) break;
      var start = cursor;
      while (cursor < srcset.length && !isSpace(srcset[cursor])) cursor++;
      var end = cursor;
      while (end > start && srcset[end - 1] === ',') end--;
      var candidate = resolve(srcset.slice(start, end), base);
      if (candidate && candidate.href === target) {
        matched = true;
        result += srcset.slice(copied, start) + (replacement === target ? srcset.slice(start, end) : replacement);
        copied = end;
      }
      if (end !== cursor) continue;
      var inParentheses = false;
      while (cursor < srcset.length) {
        var character = srcset[cursor++];
        if (character === '(') inParentheses = true;
        else if (character === ')') inParentheses = false;
        else if (character === ',' && !inParentheses) break;
      }
    }
    if (matched) element.setAttribute('srcset', result + srcset.slice(copied));
    return matched;
  }

  // Use indexed collections and ES5 syntax for older Android WebViews.
  for (var i = 0; i < document.images.length; i++) {
    var image = document.images[i];
    var base = image.ownerDocument.baseURI;
    var selected = resolve(image.currentSrc || image.src, base);
    var target = resolve(targetUrl, base);
    if (!selected || !target) continue;
    var fallback = resolve(image.getAttribute('src'), base);
    // A WebView hit test can report the src attribute for a broken linked image.
    var brokenWithMatchingFallback = image.complete && image.naturalWidth === 0 &&
      fallback && fallback.href === target.href;
    if (selected.href !== target.href && !brokenWithMatchingFallback) continue;
    var replacement = refreshUrl(selected);
    var updated = false;
    var parent = image.parentElement;
    if (parent && parent.tagName === 'PICTURE') {
      var sources = parent.querySelectorAll('source');
      for (var j = 0; j < sources.length; j++) {
        updated = refreshSrcset(sources[j], base, selected.href, replacement) || updated;
      }
    }
    updated = refreshSrcset(image, base, selected.href, replacement) || updated;
    if (fallback && fallback.href === selected.href) {
      image.setAttribute('src', replacement === selected.href ? image.getAttribute('src') : replacement);
      updated = true;
    }
    refreshed = updated || refreshed;
  }
  return refreshed;
})
