package com.mohamedref.floatshot

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Constant-memory, lossless RGBA PNG writer. Avoids allocating a potentially 20,000px-high
 * Android Bitmap (which exceeds GPU texture limits on many phones).
 */
internal class StreamingPng(raw: OutputStream, private val width: Int, private val height: Int) : Closeable {
    private val out = BufferedOutputStream(raw, 65536)
    private val zip = Deflater(6)
    private val compressed = ByteArray(32768)
    private val scanline = ByteArray(1 + width * 4)
    private var written = 0
    private var ended = false

    init {
        require(width in 1..10000 && height in 1..150000)
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val ihdr = ByteArray(13)
        put32(ihdr, 0, width)
        put32(ihdr, 4, height)
        ihdr[8] = 8 // 8-bit
        ihdr[9] = 6 // RGBA
        writeChunk("IHDR", ihdr, ihdr.size)
    }

    fun writeRow(argb: IntArray) {
        check(!ended && written < height)
        require(argb.size >= width)
        scanline[0] = 0 // PNG filter 0 - no prediction, exact pixel data
        for (x in 0 until width) {
            val color = argb[x]
            val i = 1 + x * 4
            scanline[i] = (color ushr 16).toByte()
            scanline[i + 1] = (color ushr 8).toByte()
            scanline[i + 2] = color.toByte()
            scanline[i + 3] = (color ushr 24).toByte()
        }
        zip.setInput(scanline)
        while (!zip.needsInput()) pump()
        written++
    }

    fun finish() {
        if (ended) return
        check(written == height) { "PNG rows: $written of $height" }
        zip.finish()
        while (!zip.finished()) pump()
        writeChunk("IEND", byteArrayOf(), 0)
        ended = true
        out.flush()
    }

    private fun pump() {
        val bytes = zip.deflate(compressed)
        if (bytes > 0) writeChunk("IDAT", compressed, bytes)
    }

    private fun writeChunk(type: String, data: ByteArray, count: Int) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val length = ByteArray(4)
        put32(length, 0, count)
        out.write(length)
        out.write(name)
        out.write(data, 0, count)
        val crc = CRC32()
        crc.update(name)
        crc.update(data, 0, count)
        put32(length, 0, crc.value.toInt())
        out.write(length)
    }

    private fun put32(buffer: ByteArray, at: Int, value: Int) {
        buffer[at] = (value ushr 24).toByte()
        buffer[at + 1] = (value ushr 16).toByte()
        buffer[at + 2] = (value ushr 8).toByte()
        buffer[at + 3] = value.toByte()
    }

    override fun close() {
        try {
            if (!ended && written == height) finish()
        } finally {
            zip.end()
            out.close()
        }
    }
}
