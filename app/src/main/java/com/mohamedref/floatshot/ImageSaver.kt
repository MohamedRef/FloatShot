package com.mohamedref.floatshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** MediaStore-only, local storage. No legacy storage permissions, network, or trackers. */
internal object ImageSaver {
    data class Tile(val file: File, val width: Int, val height: Int)

    fun writeTile(folder: File, frame: Bitmap, fromY: Int, untilY: Int): Tile {
        require(fromY in 0 until untilY && untilY <= frame.height)
        check(folder.exists() || folder.mkdirs())
        val destination = File(folder, "tile_${System.nanoTime()}.png")
        val partial = Bitmap.createBitmap(frame, 0, fromY, frame.width, untilY - fromY)
        try {
            destination.outputStream().buffered().use { out ->
                check(partial.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            return Tile(destination, partial.width, partial.height)
        } finally {
            partial.recycle()
        }
    }

    fun saveRegular(context: Context, image: Bitmap): Boolean =
        save(context, "Screenshot") { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        }

    fun saveLong(context: Context, tiles: List<Tile>): Boolean {
        require(tiles.isNotEmpty())
        val width = tiles.first().width
        require(tiles.all { it.width == width })
        val height = tiles.sumOf { it.height.toLong() }
        require(height in 1..150000)
        return save(context, "LongShot") { output ->
            StreamingPng(output, width, height.toInt()).use { png ->
                val pixels = IntArray(width)
                for (tile in tiles) {
                    val bitmap = BitmapFactory.decodeFile(tile.file.absolutePath)
                        ?: error("Unreadable screenshot tile")
                    try {
                        check(bitmap.width == width && bitmap.height == tile.height)
                        for (row in 0 until bitmap.height) {
                            bitmap.getPixels(pixels, 0, width, 0, row, width, 1)
                            png.writeRow(pixels)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                png.finish()
            }
        }
    }

    private inline fun save(context: Context, prefix: String, write: (java.io.OutputStream) -> Unit): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,
                "FloatShot_${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FloatShot")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        var uri: android.net.Uri? = null
        return try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri!!)?.use(write) ?: error("Could not open MediaStore")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri!!, done, null, null) > 0)
            true
        } catch (_: Exception) {
            uri?.let { resolver.delete(it, null, null) }
            false
        }
    }
}
