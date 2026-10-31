package com.mohamedref.floatshot

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream

class StreamingPngTest {
    @Test fun writesLosslessCorrectRgbaWithoutComposingGiantBitmap() {
        val width = 19
        val height = 1024
        val bytes = ByteArrayOutputStream()
        StreamingPng(bytes, width, height).use { png ->
            repeat(height) { y ->
                png.writeRow(IntArray(width) { x -> expectedPixel(x, y) })
            }
            png.finish()
        }

        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        val signature = ByteArray(8)
        input.readFully(signature)
        assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)

        var actualWidth = -1
        var actualHeight = -1
        var ended = false
        val compressed = ByteArrayOutputStream()
        while (input.available() > 0) {
            val count = input.readInt()
            assertTrue(count >= 0)
            val name = ByteArray(4)
            input.readFully(name)
            val chunk = ByteArray(count)
            input.readFully(chunk)
            val crc = CRC32().apply {
                update(name)
                update(chunk)
            }
            assertEquals("PNG CRC", crc.value.toInt(), input.readInt())
            when (String(name, Charsets.US_ASCII)) {
                "IHDR" -> {
                    val header = DataInputStream(ByteArrayInputStream(chunk))
                    actualWidth = header.readInt()
                    actualHeight = header.readInt()
                    assertEquals(8, header.readUnsignedByte())
                    assertEquals(6, header.readUnsignedByte())
                }
                "IDAT" -> compressed.write(chunk)
                "IEND" -> ended = true
            }
        }
        assertTrue("PNG is complete", ended)
        assertEquals(width, actualWidth)
        assertEquals(height, actualHeight)

        val decoded = InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { it.readBytes() }
        assertEquals(height * (1 + width * 4), decoded.size)
        repeat(height) { y ->
            val start = y * (1 + width * 4)
            assertEquals("PNG row filter", 0, decoded[start].toInt())
        }
        for (x in 0 until width) for (y in 0 until height step 23) {
            val position = y * (1 + width * 4) + 1 + x * 4
            val argb = ((decoded[position + 3].toInt() and 255) shl 24) or
                ((decoded[position].toInt() and 255) shl 16) or
                ((decoded[position + 1].toInt() and 255) shl 8) or
                (decoded[position + 2].toInt() and 255)
            assertEquals("pixel $x,$y", expectedPixel(x, y), argb)
        }
    }

    private fun expectedPixel(x: Int, y: Int): Int =
        (((x + y) % 255) shl 24) or ((x * 13 and 255) shl 16) or
            ((y * 7 and 255) shl 8) or ((x xor y) and 255)
}
