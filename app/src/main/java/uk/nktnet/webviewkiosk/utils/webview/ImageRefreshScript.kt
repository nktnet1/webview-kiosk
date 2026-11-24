package uk.nktnet.webviewkiosk.utils.webview

import org.json.JSONObject

/** Refresh images matching the long-pressed URL without reloading the page. */
internal fun refreshImageScript(imageUrl: String): String =
    """
    (() => {
        const targetUrl = ${JSONObject.quote(imageUrl)};
        const refreshToken = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
        let refreshed = false;

        for (const image of document.images) {
            const base = image.ownerDocument.baseURI;
            const sourceUrl = image.currentSrc || image.src;
            let selectedUrl;
            let fallbackUrl;
            let target;
            try {
                selectedUrl = new URL(sourceUrl, base);
                fallbackUrl = new URL(image.src, base).href;
                target = new URL(targetUrl, base).href;
            } catch (_) {
                continue;
            }
            if (selectedUrl.href !== target && fallbackUrl !== target) {
                continue;
            }

            const original = selectedUrl.href;
            if (selectedUrl.protocol === 'http:' || selectedUrl.protocol === 'https:') {
                selectedUrl.searchParams.set('_webviewkiosk_refresh', refreshToken);
            }
            const refreshedUrl = selectedUrl.href;

            // Preserve responsive candidates by refreshing the currently selected URL.
            const refreshSrcset = element => {
                const srcset = element.getAttribute('srcset');
                if (!srcset) return false;
                let replaced = false;
                const updated = srcset.split(',').map(candidate => {
                    const parts = /^(\s*)(\S+)(.*)$/.exec(candidate);
                    if (!parts) return candidate;
                    try {
                        if (new URL(parts[2], base).href === original) {
                            replaced = true;
                            return parts[1] + refreshedUrl + parts[3];
                        }
                    } catch (_) {}
                    return candidate;
                }).join(',');
                if (replaced) element.setAttribute('srcset', updated);
                return replaced;
            };

            let updated = false;
            if (image.parentElement && image.parentElement.tagName === 'PICTURE') {
                for (const source of image.parentElement.querySelectorAll('source')) {
                    updated = refreshSrcset(source) || updated;
                }
            }
            updated = refreshSrcset(image) || updated;
            if (!updated) image.src = refreshedUrl;
            refreshed = true;
        }
        return refreshed;
    })()
    """
        .trimIndent()
