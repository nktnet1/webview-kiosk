package uk.nktnet.webviewkiosk.utils.webview

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Base64
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

object NfcBridgeManager {
    @Volatile
    private var isScanActive = false

    private var webViewRef: WeakReference<WebView>? = null
    private val writeCounter = AtomicLong(0)

    @Volatile
    private var pendingWrite: PendingWriteRequest? = null

    private data class PendingWriteRequest(
        val requestId: String,
        val messageJson: String
    )

    fun attachWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun detachWebView(webView: WebView) {
        if (webViewRef?.get() == webView) {
            webViewRef?.clear()
            webViewRef = null
        }
    }

    fun setScanActive(active: Boolean) {
        isScanActive = active
    }

    fun queueWrite(messageJson: String): String {
        val requestId = "nfc-write-${System.currentTimeMillis()}-${writeCounter.incrementAndGet()}"
        pendingWrite = PendingWriteRequest(
            requestId = requestId,
            messageJson = messageJson
        )
        return requestId
    }

    fun onTagScanned(tag: Tag, action: String? = null) {
        val webView = webViewRef?.get() ?: return

        val writeResult = consumePendingWriteAndWrite(tag)
        if (writeResult != null) {
            webView.post {
                webView.evaluateJavascript(
                    "window.__WebviewKioskNfcBridge && window.__WebviewKioskNfcBridge.onWriteResult($writeResult);",
                    null
                )
            }
        }

        if (!isScanActive) {
            return
        }

        val payload = buildTagPayload(tag, action)
        webView.post {
            webView.evaluateJavascript(
                "window.__WebviewKioskNfcBridge && window.__WebviewKioskNfcBridge.onTagScanned($payload);",
                null
            )
        }
    }

    private fun consumePendingWriteAndWrite(tag: Tag): JSONObject? {
        val request = pendingWrite ?: return null
        pendingWrite = null

        return runCatching {
            writeNdefToTag(tag, request.messageJson)
            JSONObject().apply {
                put("requestId", request.requestId)
                put("ok", true)
            }
        }.getOrElse { error ->
            JSONObject().apply {
                put("requestId", request.requestId)
                put("ok", false)
                put("errorName", mapWriteErrorName(error))
                put("errorMessage", error.message ?: "Failed to write NFC tag")
            }
        }
    }

    private fun mapWriteErrorName(error: Throwable): String {
        return when (error) {
            is IllegalArgumentException, is JSONException -> "TypeError"
            is UnsupportedOperationException -> "NotSupportedError"
            is IllegalStateException -> "NotAllowedError"
            else -> "OperationError"
        }
    }

    private fun writeNdefToTag(tag: Tag, messageJson: String) {
        val message = parseNdefMessageFromJson(messageJson)
        val ndef = Ndef.get(tag)
            ?: throw UnsupportedOperationException("Tag does not support NDEF")

        ndef.connect()
        try {
            if (!ndef.isWritable) {
                throw IllegalStateException("Tag is read-only")
            }
            val bytes = message.toByteArray()
            if (bytes.size > ndef.maxSize) {
                throw IllegalStateException("NDEF message too large for tag")
            }
            ndef.writeNdefMessage(message)
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun parseNdefMessageFromJson(messageJson: String): NdefMessage {
        val root = JSONObject(messageJson)
        val recordsJson = root.optJSONArray("records")
            ?: throw IllegalArgumentException("NDEF message must include records")
        if (recordsJson.length() == 0) {
            throw IllegalArgumentException("NDEF message must include at least one record")
        }

        val records = Array(recordsJson.length()) { index ->
            val record = recordsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("Record at index $index must be an object")
            parseNdefRecordFromJson(record)
        }

        return NdefMessage(records)
    }

    private fun parseNdefRecordFromJson(record: JSONObject): NdefRecord {
        val recordType = record.optString("recordType", "text").lowercase()
        val idBytes = record
            .optString("id", "")
            .toByteArray(StandardCharsets.UTF_8)

        return when (recordType) {
            "text" -> {
                val text = if (record.has("text")) {
                    record.optString("text", "")
                } else {
                    record.optString("data", "")
                }
                val language = record.optString("lang", "en")
                val encoding = record.optString("encoding", "utf-8").lowercase()
                val isUtf16 = encoding == "utf-16"

                val languageBytes = language.toByteArray(StandardCharsets.US_ASCII)
                val textBytes = if (isUtf16) {
                    text.toByteArray(StandardCharsets.UTF_16)
                } else {
                    text.toByteArray(StandardCharsets.UTF_8)
                }
                val status = (languageBytes.size and 0x3F) or if (isUtf16) 0x80 else 0x00

                val payload = ByteArray(1 + languageBytes.size + textBytes.size)
                payload[0] = status.toByte()
                System.arraycopy(
                    languageBytes,
                    0,
                    payload,
                    1,
                    languageBytes.size
                )
                System.arraycopy(
                    textBytes,
                    0,
                    payload,
                    1 + languageBytes.size,
                    textBytes.size
                )

                NdefRecord(
                    NdefRecord.TNF_WELL_KNOWN,
                    NdefRecord.RTD_TEXT,
                    idBytes,
                    payload
                )
            }

            "url", "uri" -> {
                val uri = if (record.has("uri")) {
                    record.optString("uri", "")
                } else {
                    record.optString("data", "")
                }
                if (uri.isBlank()) {
                    throw IllegalArgumentException("URI record must include a non-empty uri")
                }
                NdefRecord.createUri(uri)
            }

            "mime" -> {
                val mediaType = record.optString("mediaType", "text/plain")
                val payload = decodeRecordPayload(record)
                NdefRecord.createMime(mediaType, payload)
            }

            else -> {
                val tnf = record
                    .optInt("tnf", NdefRecord.TNF_UNKNOWN.toInt())
                    .toShort()
                val rawType = record.optString("rawType", "")
                val typeBytes = rawType.toByteArray(StandardCharsets.UTF_8)
                val payload = decodeRecordPayload(record)

                NdefRecord(
                    tnf,
                    typeBytes,
                    idBytes,
                    payload
                )
            }
        }
    }

    private fun decodeRecordPayload(record: JSONObject): ByteArray {
        if (record.has("payloadBase64")) {
            val base64 = record.optString("payloadBase64", "")
            if (base64.isNotEmpty()) {
                return Base64.decode(base64, Base64.DEFAULT)
            }
        }
        if (record.has("dataBase64")) {
            val base64 = record.optString("dataBase64", "")
            if (base64.isNotEmpty()) {
                return Base64.decode(base64, Base64.DEFAULT)
            }
        }
        if (record.has("data")) {
            val value = record.get("data")
            return when (value) {
                is String -> value.toByteArray(StandardCharsets.UTF_8)
                else -> value.toString().toByteArray(StandardCharsets.UTF_8)
            }
        }
        return byteArrayOf()
    }

    private fun buildTagPayload(tag: Tag, action: String?): JSONObject {
        val obj = JSONObject()
        obj.put("action", action ?: "")
        obj.put("serialNumber", toHex(tag.id ?: byteArrayOf()))
        obj.put("timestamp", System.currentTimeMillis())

        val records = JSONArray()
        readNdefMessage(tag)?.records?.forEach { record ->
            records.put(parseNdefRecord(record))
        }
        obj.put("records", records)

        return obj
    }

    private fun readNdefMessage(tag: Tag): NdefMessage? {
        val ndef = Ndef.get(tag) ?: return null
        return runCatching {
            ndef.connect()
            ndef.cachedNdefMessage ?: ndef.ndefMessage
        }.getOrNull().also {
            runCatching { ndef.close() }
        }
    }

    private fun parseNdefRecord(record: NdefRecord): JSONObject {
        val json = JSONObject()
        val payload = record.payload ?: byteArrayOf()
        val payloadBase64 = Base64.encodeToString(payload, Base64.NO_WRAP)

        json.put("tnf", record.tnf.toInt())
        json.put(
            "id",
            String(record.id ?: byteArrayOf(), StandardCharsets.UTF_8)
        )
        json.put(
            "rawType",
            String(record.type ?: byteArrayOf(), StandardCharsets.UTF_8)
        )
        json.put("payloadBase64", payloadBase64)

        when {
            isTextRecord(record) -> {
                val textData = parseTextPayload(payload)
                json.put("recordType", "text")
                json.put("text", textData.text)
                json.put("lang", textData.language)
                json.put("encoding", textData.encoding)
            }

            isUriRecord(record) -> {
                json.put("recordType", "url")
                json.put("uri", parseUriPayload(payload))
            }

            record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                json.put("recordType", "mime")
                json.put(
                    "mediaType",
                    String(
                        record.type ?: byteArrayOf(),
                        StandardCharsets.US_ASCII
                    )
                )
            } else -> {
                json.put("recordType", "unknown")
            }
        }

        return json
    }

    private fun isTextRecord(record: NdefRecord): Boolean {
        return (
            record.tnf == NdefRecord.TNF_WELL_KNOWN
            && record.type.contentEquals(NdefRecord.RTD_TEXT)
        )
    }

    private fun isUriRecord(record: NdefRecord): Boolean {
        return (
            record.tnf == NdefRecord.TNF_WELL_KNOWN
            && record.type.contentEquals(NdefRecord.RTD_URI)
        )
    }

    private data class TextPayload(
        val text: String,
        val language: String,
        val encoding: String
    )

    private fun parseTextPayload(payload: ByteArray): TextPayload {
        if (payload.isEmpty()) {
            return TextPayload("", "", "utf-8")
        }

        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val languageLength = status and 0x3F
        val textStart = 1 + languageLength
        if (textStart > payload.size) {
            return TextPayload("", "", if (isUtf16) "utf-16" else "utf-8")
        }

        val language = if (languageLength > 0 && payload.size >= 1 + languageLength) {
            String(payload, 1, languageLength, StandardCharsets.US_ASCII)
        } else {
            ""
        }

        val charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
        val text = String(
            payload,
            textStart,
            payload.size - textStart, charset
        )

        return TextPayload(text, language, if (isUtf16) "utf-16" else "utf-8")
    }

    private val uriPrefixes = arrayOf(
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:"
    )

    private fun parseUriPayload(payload: ByteArray): String {
        if (payload.isEmpty()) {
            return ""
        }

        val prefixIndex = payload[0].toInt() and 0xFF
        val prefix = uriPrefixes.getOrElse(prefixIndex) { "" }
        val uriRemainder = String(
            payload,
            1,
            payload.size - 1,
            StandardCharsets.UTF_8
        )
        return prefix + uriRemainder
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { b ->
            "%02X".format(b)
        }
    }
}
