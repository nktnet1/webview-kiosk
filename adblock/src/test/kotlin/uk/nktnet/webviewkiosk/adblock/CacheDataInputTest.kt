package uk.nktnet.webviewkiosk.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.Random
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.CacheDataInput
import uk.nktnet.webviewkiosk.adblock.engine.CacheStringWriter

class CacheDataInputTest {
    @Test
    fun primitivesAndBulkReadsMatchDataInputAcrossBufferBoundaries() {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        val values = intArrayOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)
        repeat(20_000) { index ->
            output.writeInt(values[index % values.size])
            output.writeBoolean(index % 2 == 0)
            output.writeByte(-128)
            output.writeByte(255)
        }
        val bulk = ByteArray(128 * 1024) { it.toByte() }
        output.write(bulk)
        val payload = bytes.toByteArray()
        val checksum = CRC32()
        CacheDataInput(
                ByteArrayInputStream(payload),
                payload.size.toLong(),
                checksum,
            )
            .use { input ->
                repeat(20_000) { index ->
                    assertEquals(values[index % values.size], input.readInt())
                    assertEquals(index % 2 == 0, input.readBoolean())
                    assertEquals((-128).toByte(), input.readByte())
                    assertEquals(255, input.readUnsignedByte())
                }
                val restored = ByteArray(bulk.size)
                input.readFully(restored)
                assertArrayEquals(bulk, restored)
                assertEquals(0, input.available())
                assertEquals(-1, input.read())
                assertThrows(EOFException::class.java) { input.readInt() }
            }
        assertEquals(CRC32().apply { update(payload) }.value, checksum.value)
    }

    @Test
    fun stringsPreserveAllLatin1AndExactUtf16CodeUnits() {
        val random = Random(19)
        val values =
            listOf(
                "",
                "plain.example/path^",
                (0..255).map { it.toChar() }.joinToString(""),
                "\u0100\u0800\u20ac\uffff",
                "emoji\ud83d\ude00",
                "unpaired\ud800middle\udc00end",
                "a".repeat(CacheStringWriter.MAX_LENGTH),
                "\ud83d\ude00".repeat(CacheStringWriter.MAX_LENGTH / 2),
            ) +
                List(1_000) {
                    CharArray(random.nextInt(257)) {
                            random.nextInt(65536).toChar()
                        }
                        .concatToString()
                }
        val bytes = ByteArrayOutputStream()
        val writer = CacheStringWriter(DataOutputStream(bytes))
        values.forEach(writer::write)
        val payload = bytes.toByteArray()
        CacheDataInput(ByteArrayInputStream(payload), payload.size.toLong(), CRC32())
            .use { input ->
                values.forEach { assertEquals(it, input.readString()) }
                assertEquals(-1, input.read())
            }
    }

    @Test
    fun stringWireFormatUsesLatin1OrBigEndianUtf16() {
        val bytes = ByteArrayOutputStream()
        val writer = CacheStringWriter(DataOutputStream(bytes))
        writer.write("\u0000\u00e9\u00ff")
        writer.write("\u0100\ud83d\ude00")
        val expected =
            byteArrayOf(
                0,
                3,
                0,
                0xe9.toByte(),
                0xff.toByte(),
                0x80.toByte(),
                3,
                1,
                0,
                0xd8.toByte(),
                0x3d,
                0xde.toByte(),
                0,
            )
        assertArrayEquals(expected, bytes.toByteArray())
        CacheDataInput(
                ByteArrayInputStream(expected),
                expected.size.toLong(),
                CRC32(),
            )
            .use { input ->
                assertEquals("\u0000\u00e9\u00ff", input.readString())
                assertEquals("\u0100\ud83d\ude00", input.readString())
                assertEquals(-1, input.read())
            }
    }

    @Test
    fun splitStringHeadersAndBodiesKeepChecksumAndFooterIntact() {
        for (padding in listOf(65_532, 65_533, 65_534, 65_535, 65_536, 131_071)) {
            for (value in listOf("host.example", "\u4e16\u754c\ud83d\ude00")) {
                val bytes = ByteArrayOutputStream()
                val output = DataOutputStream(bytes)
                val prefix = ByteArray(padding) { (it * 31).toByte() }
                output.write(prefix)
                CacheStringWriter(output).write(value)
                val payload = bytes.toByteArray()
                val expected = CRC32().apply { update(payload) }.value
                output.writeLong(expected)
                val source = ByteArrayInputStream(bytes.toByteArray())
                val checksum = CRC32()
                CacheDataInput(source, payload.size.toLong(), checksum).use { input
                    ->
                    assertEquals(payload.size, input.available())
                    assertArrayEquals(
                        prefix,
                        ByteArray(padding).also(input::readFully),
                    )
                    assertEquals(value, input.readString())
                    assertEquals(-1, input.read())
                    assertEquals(expected, checksum.value)
                    assertEquals(expected, DataInputStream(source).readLong())
                    assertEquals(-1, source.read())
                }
            }
        }
    }

    @Test
    fun shortAndZeroLengthSourceReadsMakeProgressWithoutLosingBytes() {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        val value = "scope.example/\u4e16\u754c"
        output.writeInt(Int.MIN_VALUE)
        CacheStringWriter(output).write(value)
        val payload = bytes.toByteArray()
        val source =
            object : ByteArrayInputStream(payload) {
                var calls = 0

                override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                    if (calls++ % 3 == 0) {
                        0
                    } else {
                        super.read(bytes, offset, minOf(3, length))
                    }
            }
        val checksum = CRC32()
        CacheDataInput(source, payload.size.toLong(), checksum).use { input ->
            assertEquals(Int.MIN_VALUE, input.readInt())
            assertEquals(value, input.readString())
            assertEquals(-1, input.read())
        }
        assertEquals(CRC32().apply { update(payload) }.value, checksum.value)
    }

    @Test
    fun invalidLengthsAndTruncatedStringsAreRejected() {
        for (tag in listOf(8193, 0x8000 or 8193, 0x7fff, 0xffff)) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).writeShort(tag)
            CacheDataInput(ByteArrayInputStream(bytes.toByteArray()), 2, CRC32())
                .use {
                    assertThrows(IllegalArgumentException::class.java) {
                        it.readString()
                    }
                }
        }
        for (bytes in
            listOf(
                byteArrayOf(),
                byteArrayOf(0),
                byteArrayOf(0, 1),
                byteArrayOf(0x80.toByte(), 1, 0x20),
            )) {
            CacheDataInput(ByteArrayInputStream(bytes), bytes.size.toLong(), CRC32())
                .use {
                    assertThrows(EOFException::class.java) { it.readString() }
                }
        }
        val bytes = ByteArrayOutputStream()
        val writer = CacheStringWriter(DataOutputStream(bytes))
        assertThrows(IOException::class.java) {
            writer.write("a".repeat(CacheStringWriter.MAX_LENGTH + 1))
        }
        assertEquals(0, bytes.size())
    }
}
