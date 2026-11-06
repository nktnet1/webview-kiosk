@file:Suppress("unused")

package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.webkit.JavascriptInterface
import org.json.JSONObject
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager
import uk.nktnet.webviewkiosk.utils.webview.isNfcEnabled

class NfcInterface(private val context: android.content.Context) {
    companion object {
        const val NAME = "WebviewKioskNfcInterface"

        const val JS_WEB_NFC_HOOK =
            """
            (function() {
                if (window.__webviewKioskWebNfcInstalled) return;
                const nativeBridge = window.WebviewKioskNfcInterface;
                if (!nativeBridge || typeof nativeBridge.openClient !== 'function') return;
                const clientId = nativeBridge.openClient();
                if (!clientId) return;
                window.__webviewKioskWebNfcInstalled = true;

                const activeReaders = new Set();
                const pendingWrites = new Map();

                function createDomException(message, name) {
                    try {
                        return new DOMException(message, name);
                    } catch (_) {
                        const error = new Error(message);
                        error.name = name;
                        return error;
                    }
                }

                function base64ToBytes(base64) {
                    if (!base64) return new Uint8Array(0);
                    const binary = atob(base64);
                    const bytes = new Uint8Array(binary.length);
                    for (let i = 0; i < binary.length; i++) {
                        bytes[i] = binary.charCodeAt(i);
                    }
                    return bytes;
                }

                function bytesToBase64(bytes) {
                    let binary = '';
                    const chunkSize = 0x8000;
                    for (let i = 0; i < bytes.length; i += chunkSize) {
                        const chunk = bytes.subarray(i, i + chunkSize);
                        binary += String.fromCharCode.apply(null, chunk);
                    }
                    return btoa(binary);
                }

                function toUint8Array(value) {
                    if (value == null) {
                        return new Uint8Array(0);
                    }
                    if (value instanceof ArrayBuffer) {
                        return new Uint8Array(value);
                    }
                    if (ArrayBuffer.isView(value)) {
                        return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
                    }
                    if (typeof value === 'string') {
                        return new TextEncoder().encode(value);
                    }
                    return new TextEncoder().encode(JSON.stringify(value));
                }

                function normalizeWriteMessage(message) {
                    if (typeof message === 'string') {
                        return {
                            records: [{
                                recordType: 'text',
                                text: message,
                                lang: 'en',
                                encoding: 'utf-8'
                            }]
                        };
                    }

                    if (message instanceof ArrayBuffer || ArrayBuffer.isView(message)) {
                        return {
                            records: [{
                                recordType: 'mime',
                                mediaType: 'application/octet-stream',
                                payloadBase64: bytesToBase64(toUint8Array(message))
                            }]
                        };
                    }

                    if (!message || !Array.isArray(message.records)) {
                        throw createDomException('Invalid NDEF message format.', 'TypeError');
                    }

                    return {
                        records: message.records.map(function(record) {
                            const recordType = (record.recordType || 'text').toLowerCase();
                            const normalized = {
                                recordType,
                                id: record.id || ''
                            };

                            if (recordType === 'text') {
                                normalized.text = record.text != null
                                    ? String(record.text)
                                    : String(record.data != null ? record.data : '');
                                normalized.lang = record.lang || 'en';
                                normalized.encoding = record.encoding || 'utf-8';
                                return normalized;
                            }

                            if (recordType === 'url' || recordType === 'uri') {
                                normalized.uri = record.uri != null
                                    ? String(record.uri)
                                    : String(record.data != null ? record.data : '');
                                return normalized;
                            }

                            if (recordType === 'mime') {
                                normalized.mediaType = record.mediaType || 'text/plain';
                                normalized.payloadBase64 = bytesToBase64(
                                    toUint8Array(record.data != null ? record.data : '')
                                );
                                return normalized;
                            }

                            normalized.tnf = Number.isInteger(record.tnf) ? record.tnf : 5;
                            normalized.rawType = record.rawType || '';
                            normalized.payloadBase64 = bytesToBase64(
                                toUint8Array(record.data != null ? record.data : '')
                            );
                            return normalized;
                        })
                    };
                }

                function createWritePromise(requestId) {
                    return new Promise(function(resolve, reject) {
                        pendingWrites.set(requestId, { resolve, reject });
                    });
                }

                function resolveWriteResult(result) {
                    if (!result || !result.requestId) {
                        return;
                    }

                    const pending = pendingWrites.get(result.requestId);
                    if (!pending) {
                        return;
                    }
                    pendingWrites.delete(result.requestId);

                    if (result.ok) {
                        pending.resolve();
                    } else {
                        pending.reject(
                            createDomException(
                                result.errorMessage || 'Failed to write NFC tag.',
                                result.errorName || 'OperationError'
                            )
                        );
                    }
                }

                function toNdefRecord(nativeRecord) {
                    const bytes = base64ToBytes(nativeRecord.payloadBase64);
                    const textValue = nativeRecord.text || '';
                    const encoding = nativeRecord.encoding || 'utf-8';

                    return {
                        recordType: nativeRecord.recordType || 'unknown',
                        mediaType: nativeRecord.mediaType || null,
                        id: nativeRecord.id || '',
                        encoding: nativeRecord.encoding || null,
                        lang: nativeRecord.lang || null,
                        data: bytes.buffer.slice(0),
                        async text() {
                            if (textValue) return textValue;
                            try {
                                return new TextDecoder(encoding).decode(bytes);
                            } catch (_) {
                                return new TextDecoder('utf-8').decode(bytes);
                            }
                        },
                        async json() {
                            const txt = await this.text();
                            return JSON.parse(txt);
                        },
                        async arrayBuffer() {
                            return bytes.buffer.slice(0);
                        }
                    };
                }

                function dispatchReading(nativePayload) {
                    const serialNumber = nativePayload.serialNumber || '';
                    const records = (nativePayload.records || []).map(toNdefRecord);
                    const message = { records };

                    activeReaders.forEach(function(reader) {
                        let event = null;

                        if (typeof NDEFReadingEvent === 'function') {
                            try {
                                event = new NDEFReadingEvent('reading', {
                                    serialNumber,
                                    message,
                                });
                            } catch (_) {
                                event = null;
                            }
                        }

                        if (!event) {
                            event = new Event('reading');
                            event.serialNumber = serialNumber;
                            event.message = message;
                        }

                        reader.dispatchEvent(event);

                        if (typeof reader.onreading === 'function') {
                            try {
                                reader.onreading(event);
                            } catch (_) {}
                        }
                    });
                }

                // A suspended WebView can retain its JavaScript readers even when
                // Android loses the native scanning session. Re-arm the existing
                // readers; never create another NDEFReader or reload the page.
                function rearmActiveReaders() {
                    if (activeReaders.size === 0 ||
                        !nativeBridge || typeof nativeBridge.scanForClient !== 'function') {
                        return;
                    }
                    try {
                        // A failed attempt keeps the readers for the next wake or
                        // adapter-enabled event. It does not change NFC settings.
                        nativeBridge.scanForClient(clientId, '{}');
                    } catch (_) {}
                }

                class WebviewKioskNDEFReader extends EventTarget {
                    constructor() {
                        super();
                        this.onreading = null;
                        this.onreadingerror = null;
                        this._scanActive = false;
                        this._scanSignal = null;
                        this._scanAbortListener = null;
                    }

                    async scan(options = {}) {
                        const signal = options && options.signal;
                        if (signal && signal.aborted) {
                            throw createDomException('NFC scan aborted.', 'AbortError');
                        }
                        if (!nativeBridge || typeof nativeBridge.scanForClient !== 'function') {
                            throw createDomException(
                                'Web NFC bridge unavailable.',
                                 'NotSupportedError'
                             );
                        }

                        const ok = nativeBridge.scanForClient(
                            clientId, JSON.stringify(options)
                        );
                        if (!ok) {
                            const error = createDomException(
                                'NFC is unavailable or disabled in device settings.',
                                'NotAllowedError'
                            );
                            if (typeof this.onreadingerror === 'function') {
                                try {
                                    this.onreadingerror(error);
                                } catch (_) {}
                            }
                            throw error;
                        }

                        this._scanActive = true;
                        activeReaders.add(this);

                        if (this._scanSignal && this._scanAbortListener) {
                            this._scanSignal.removeEventListener(
                                'abort', this._scanAbortListener
                            );
                        }
                        this._scanSignal = null;
                        this._scanAbortListener = null;
                        if (signal && typeof signal.addEventListener === 'function') {
                            this._scanSignal = signal;
                            this._scanAbortListener = () => this.stopScan();
                            signal.addEventListener('abort', this._scanAbortListener, {
                                once: true
                            });
                            if (signal.aborted) this.stopScan();
                        }
                    }

                    async write() {
                        if (!nativeBridge || typeof nativeBridge.writeForClient !== 'function') {
                            throw createDomException(
                                'Web NFC write bridge unavailable.',
                                 'NotSupportedError'
                             );
                        }

                        const message = arguments.length > 0 ? arguments[0] : null;
                        const options = arguments.length > 1 ? arguments[1] : {};
                        const normalizedMessage = normalizeWriteMessage(message);

                        let response;
                        try {
                            response = JSON.parse(nativeBridge.writeForClient(
                                clientId,
                                JSON.stringify(normalizedMessage),
                                JSON.stringify(options || {})
                            ));
                        } catch (_) {
                            throw createDomException(
                                'Unable to start NFC write.',
                                 'OperationError'
                             );
                        }

                        if (!response || !response.ok || !response.requestId) {
                            throw createDomException(
                                (response && response.errorMessage) || 'Unable to start NFC write.',
                                (response && response.errorName) || 'OperationError'
                            );
                        }

                        return createWritePromise(response.requestId);
                    }

                    async makeReadOnly() {
                        throw createDomException(
                            'NDEFReader.makeReadOnly() is not supported by this bridge.',
                             'NotSupportedError'
                         );
                    }

                    stopScan() {
                        const wasActive = this._scanActive;
                        this._scanActive = false;
                        if (this._scanSignal && this._scanAbortListener) {
                            this._scanSignal.removeEventListener(
                                'abort', this._scanAbortListener
                            );
                        }
                        this._scanSignal = null;
                        this._scanAbortListener = null;
                        activeReaders.delete(this);
                        // The native bridge is shared by every reader in this
                        // document. Only the last one may stop this document's
                        // scan; other frames/pages own separate client IDs.
                        if (wasActive && activeReaders.size === 0 && nativeBridge &&
                            typeof nativeBridge.stopScanForClient === 'function') {
                            try {
                                nativeBridge.stopScanForClient(clientId);
                            } catch (_) {}
                        }
                    }
                }

                if (typeof window.__nativeNDEFReader === 'undefined') {
                    window.__nativeNDEFReader = window.NDEFReader;
                }
                window.NDEFReader = WebviewKioskNDEFReader;

                if (!navigator.nfc) {
                    navigator.nfc = {};
                }

                window.__WebviewKioskNfcBridge = {
                    onTagScanned: function(nativePayload) {
                        dispatchReading(nativePayload);
                    },
                    onWriteResult: function(resultPayload) {
                        resolveWriteResult(resultPayload);
                    },
                    rearmScan: rearmActiveReaders
                };

                // Native onPageStarted owns session teardown. Calling reset from
                // beforeunload races with the next page's scan() on the shared
                // @JavascriptInterface, and can also run for cancelled unloads.
                // A frame may navigate without the top-level onPageStarted event.
                // Release only this document's client on pagehide; retaining the
                // client across bfcache allows pageshow to resume existing readers.
                window.addEventListener('pagehide', function(event) {
                    try {
                        if (event.persisted) {
                            nativeBridge.stopScanForClient(clientId);
                        } else {
                            nativeBridge.releaseClient(clientId);
                        }
                    } catch (_) {}
                });
                window.addEventListener('pageshow', rearmActiveReaders);
                window.addEventListener('focus', rearmActiveReaders);
                document.addEventListener('visibilitychange', function() {
                    if (!document.hidden) rearmActiveReaders();
                });
            })();
        """
    }

    @Suppress("unused")
    @JavascriptInterface
    fun openClient(): String = NfcBridgeManager.openClient()

    @Suppress("unused")
    @JavascriptInterface
    fun scanForClient(clientId: String?, optionsJson: String?): Boolean {
        if (!isNfcEnabled(context)) {
            return false
        }
        return NfcBridgeManager.startClientScan(clientId)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun stopScanForClient(clientId: String?) {
        NfcBridgeManager.stopClientScan(clientId)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun releaseClient(clientId: String?) {
        NfcBridgeManager.releaseClient(clientId)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun scan(optionsJson: String?): Boolean {
        if (!isNfcEnabled(context)) {
            return false
        }

        NfcBridgeManager.setScanActive(true)
        return true
    }

    @Suppress("unused")
    @JavascriptInterface
    fun stopScan() {
        NfcBridgeManager.setScanActive(false)
    }

    @JavascriptInterface
    fun reset() {
        NfcBridgeManager.resetSession()
    }

    @JavascriptInterface
    fun write(messageJson: String?, optionsJson: String?): String {
        return writeInternal(messageJson)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun writeForClient(
        clientId: String?,
        messageJson: String?,
        optionsJson: String?,
    ): String {
        if (!NfcBridgeManager.isCurrentClient(clientId)) {
            return JSONObject()
                .apply {
                    put("ok", false)
                    put("errorName", "InvalidStateError")
                    put("errorMessage", "NFC page session has ended")
                }
                .toString()
        }
        return writeInternal(messageJson, clientId)
    }

    private fun writeInternal(
        messageJson: String?,
        clientId: String? = null,
    ): String {
        if (!isNfcEnabled(context)) {
            return JSONObject()
                .apply {
                    put("ok", false)
                    put("errorName", "NotAllowedError")
                    put(
                        "errorMessage",
                        "NFC is unavailable or disabled in device settings.",
                    )
                }
                .toString()
        }

        val message =
            messageJson?.takeIf { it.isNotBlank() }
                ?: return JSONObject()
                    .apply {
                        put("ok", false)
                        put("errorName", "TypeError")
                        put("errorMessage", "NDEF message is required")
                    }
                    .toString()

        val requestId =
            NfcBridgeManager.queueWrite(message, clientId)
                ?: return JSONObject()
                    .apply {
                        put("ok", false)
                        put("errorName", "InvalidStateError")
                        put("errorMessage", "An NFC write is already pending")
                    }
                    .toString()
        return JSONObject()
            .apply {
                put("ok", true)
                put("requestId", requestId)
            }
            .toString()
    }
}
