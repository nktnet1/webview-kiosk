package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.nfc.NfcAdapter
import android.webkit.JavascriptInterface
import org.json.JSONObject
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager

class NfcInterface(private val context: android.content.Context) {
    companion object {
        const val NAME = "WebviewKioskNfcInterface"

        const val JS_WEB_NFC_HOOK = """
            (function() {
                if (window.__webviewKioskWebNfcInstalled) return;
                window.__webviewKioskWebNfcInstalled = true;

                const nativeBridge = window.WebviewKioskNfcInterface;
                if (!nativeBridge) return;

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

                class WebviewKioskNDEFReader extends EventTarget {
                    constructor() {
                        super();
                        this.onreading = null;
                        this.onreadingerror = null;
                        this._scanActive = false;
                    }

                    async scan(options = {}) {
                        if (!nativeBridge || typeof nativeBridge.scan !== 'function') {
                            throw createDomException(
                                'Web NFC bridge unavailable.',
                                 'NotSupportedError'
                             );
                        }

                        const ok = nativeBridge.scan(JSON.stringify(options));
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
                    }

                    async write() {
                        if (!nativeBridge || typeof nativeBridge.write !== 'function') {
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
                            response = JSON.parse(nativeBridge.write(
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
                        if (
                            this._scanActive
                            && nativeBridge
                            && typeof nativeBridge.stopScan === 'function'
                        ) {
                            nativeBridge.stopScan();
                        }
                        this._scanActive = false;
                        activeReaders.delete(this);
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
                    }
                };

                window.addEventListener('beforeunload', function() {
                    activeReaders.clear();
                    pendingWrites.clear();
                    if (nativeBridge && typeof nativeBridge.stopScan === 'function') {
                        nativeBridge.stopScan();
                    }
                });
            })();
        """
    }

    @Suppress("unused")
    @JavascriptInterface
    fun scan(optionsJson: String?): Boolean {
        val isAvailable = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
        if (!isAvailable) {
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

    @Suppress("unused")
    @JavascriptInterface
    fun write(messageJson: String?, optionsJson: String?): String {
        val isAvailable = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
        if (!isAvailable) {
            return JSONObject().apply {
                put("ok", false)
                put("errorName", "NotAllowedError")
                put(
                    "errorMessage",
                    "NFC is unavailable or disabled in device settings."
                )
            }.toString()
        }

        val message = messageJson?.takeIf { it.isNotBlank() }
            ?: return JSONObject().apply {
                put("ok", false)
                put("errorName", "TypeError")
                put("errorMessage", "NDEF message is required")
            }.toString()

        val requestId = NfcBridgeManager.queueWrite(message)
        return JSONObject().apply {
            put("ok", true)
            put("requestId", requestId)
        }.toString()
    }
}
