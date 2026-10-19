package uk.nktnet.webviewkiosk.adblock.engine

import java.io.Closeable
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32

/**
 * Single-owner, bounded cache reader. Decode primitives and strings directly from a
 * reusable buffer, rather than copying each primitive through DataInputStream.
 * Checksum only newly read payload bytes; leave the footer on the source stream.
 */
internal class CacheDataInput(
    private val source: InputStream,
    private var remaining: Long,
    private val checksum: CRC32,
) : Closeable {
    private val buffer = ByteArray(64 * 1024)
    private var position = 0
    private var limit = 0
    private var characters = CharArray(0)

    init {
        require(remaining in 0..Int.MAX_VALUE.toLong())
    }

    fun available(): Int = remaining.toInt() + limit - position

    private fun ensure(count: Int) {
        if (limit - position >= count) {
            return
        }
        buffer.copyInto(buffer, 0, position, limit)
        limit -= position
        position = 0
        while (limit < count) {
            if (remaining == 0L) {
                throw EOFException("Truncated compiled cache")
            }
            var read =
                source.read(
                    buffer,
                    limit,
                    minOf(buffer.size - limit, remaining.toInt()),
                )
            if (read < 0) {
                throw EOFException("Truncated compiled cache")
            }
            if (read == 0) {
                val byte = source.read()
                if (byte < 0) {
                    throw EOFException("Truncated compiled cache")
                }
                buffer[limit] = byte.toByte()
                read = 1
            }
            checksum.update(buffer, limit, read)
            remaining -= read
            limit += read
        }
    }

    fun read(): Int =
        if (available() == 0) {
            -1
        } else {
            readUnsignedByte()
        }

    fun readUnsignedByte(): Int {
        ensure(1)
        return buffer[position++].toInt() and 0xff
    }

    fun readByte(): Byte = readUnsignedByte().toByte()

    fun readBoolean(): Boolean = readUnsignedByte() != 0

    private fun readUnsignedShort(): Int {
        ensure(2)
        val offset = position
        position += 2
        return ((buffer[offset].toInt() and 0xff) shl 8) or
            (buffer[offset + 1].toInt() and 0xff)
    }

    fun readInt(): Int {
        ensure(4)
        val offset = position
        position += 4
        return ((buffer[offset].toInt() and 0xff) shl 24) or
            ((buffer[offset + 1].toInt() and 0xff) shl 16) or
            ((buffer[offset + 2].toInt() and 0xff) shl 8) or
            (buffer[offset + 3].toInt() and 0xff)
    }

    fun readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            ensure(1)
            val count = minOf(bytes.size - offset, limit - position)
            buffer.copyInto(bytes, offset, position, position + count)
            position += count
            offset += count
        }
    }

    fun readString(): String {
        val tag = readUnsignedShort()
        val length = tag and 0x7fff
        require(length <= CacheStringWriter.MAX_LENGTH)
        if (length == 0) {
            return ""
        }
        val wide = tag and 0x8000 != 0
        val byteCount =
            if (wide) {
                length * 2
            } else {
                length
            }
        ensure(byteCount)
        val value =
            if (wide) {
                // Keep exact UTF-16 code units, including unpaired surrogates.
                // A CharsetDecoder could replace them and change rule contents.
                if (characters.size < length) {
                    characters = CharArray(length)
                }
                for (index in 0 until length) {
                    val offset = position + index * 2
                    characters[index] =
                        (((buffer[offset].toInt() and 0xff) shl 8) or
                                (buffer[offset + 1].toInt() and 0xff))
                            .toChar()
                }
                String(characters, 0, length)
            } else {
                // The single-byte representation is Latin-1, not modified UTF-8.
                // Every byte is valid, and the platform decodes directly from the
                // buffer without our allocating a temporary byte or char array.
                String(buffer, position, length, Charsets.ISO_8859_1)
            }
        position += byteCount
        return value
    }

    override fun close() = source.close()
}

/** Schema 19 strings: tagged character count, then Latin-1 or raw UTF-16BE. */
internal class CacheStringWriter(private val output: DataOutputStream) {
    private val buffer = ByteArray(MAX_LENGTH * 2)

    fun write(value: String) {
        if (value.length > MAX_LENGTH) {
            throw IOException("Compiled cache string exceeds the size limit")
        }
        var wide = false
        for (index in value.indices) {
            val char = value[index].code
            if (char > 0xff) {
                wide = true
                break
            }
            buffer[index] = char.toByte()
        }
        val length = value.length
        output.writeShort(
            if (wide) {
                length or 0x8000
            } else {
                length
            }
        )
        if (wide) {
            for (index in value.indices) {
                val char = value[index].code
                buffer[index * 2] = (char ushr 8).toByte()
                buffer[index * 2 + 1] = char.toByte()
            }
        }
        output.write(
            buffer,
            0,
            if (wide) {
                length * 2
            } else {
                length
            },
        )
    }

    companion object {
        const val MAX_LENGTH = 8192
    }
}
