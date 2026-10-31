package com.mohamedref.floatshot

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class StreamingPngTest {
    @Test fun `writes lossless correct RGB and alpha without composing a giant bitmap`() {
        val width = 19
        val height = 1024
        val bytes = ByteArrayOutputStream()
        StreamingPng(bytes, width, height).use { png ->
            repeat(height) { y ->
                png.writeRow(IntArray(width) { x ->
                    ((x + y) % 255 shl 24) or ((x * 13 and 0xff) shl 16) or
                        ((y * 7 and 0xff) shl 8) or ((x xor y) and 0xff)
                })
            }
            png.finish()
        }
        val decoded = ImageIO.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        for (x in 0 until width) for (y in 0 until height step 23) {
            val expected = ((x + y) % 255 shl 24) or ((x * 13 and 0xff) shl 16) or
                ((y * 7 and 0xff) shl 8) or ((x xor y) and 0xff)
            assertEquals("pixel $x,$y", expected, decoded.getRGB(x, y))
        }
    }
}
