package com.mohamedref.floatshot

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Android-independent visual registration. Each frame is a set of full-height RGB columns,
 * sampled from the same scrollable viewport. Alignment is always checked against visible
 * pixels; the swipe distance is NEVER used to guess a stitch boundary.
 */
internal class ColumnFrame(
    val height: Int,
    val columns: Int,
    private val pixels: IntArray
) {
    init {
        require(height > 0 && columns >= 8 && pixels.size == height * columns)
    }

    fun pixel(y: Int, x: Int): Int = pixels[y * columns + x]

    companion object {
        fun sample(height: Int, columns: Int = 64, pixel: (y: Int, column: Int) -> Int): ColumnFrame {
            return ColumnFrame(height, columns, IntArray(height * columns) { index ->
                pixel(index / columns, index % columns)
            })
        }
    }
}

internal sealed interface Alignment {
    data class Scrolled(val pixels: Int, val error: Double) : Alignment
    data object Stationary : Alignment
    data object Uncertain : Alignment
}

/** Conservative correlation matcher. Ambiguous or moving content must never be stitched. */
internal object OverlapMatcher {
    private const val MAX_ACCEPTABLE_ERROR = 16.0
    private const val MIN_DISTINCTIVENESS = 2.0

    fun isStationary(before: ColumnFrame, after: ColumnFrame): Boolean =
        before.height == after.height && before.columns == after.columns &&
            score(before, after, 0, precise = true) < 1.5 &&
            changedFraction(before, after) < 0.006

    fun align(before: ColumnFrame, after: ColumnFrame): Alignment {
        if (before.height != after.height || before.columns != after.columns) return Alignment.Uncertain
        val height = before.height
        if (height < 120) return Alignment.Uncertain

        // Detect the true end of scrolling instead of blindly adding a duplicate viewport.
        if (isStationary(before, after)) return Alignment.Stationary

        val minShift = max(8, height / 150)
        val maxShift = (height * 0.78).toInt()
        if (maxShift <= minShift) return Alignment.Uncertain
        val stride = 1 // every pixel: skipping shifts can miss repeated-pattern ambiguity

        // Keep diverse coarse hypotheses. One-pixel shifts near glyph edges can have a
        // surprisingly high coarse error; taking just one minimum misses real alignments.
        val candidates = (minShift..maxShift step stride)
            .map { shift -> shift to score(before, after, shift, precise = false) }
            .sortedBy { it.second }
        val hypotheses = ArrayList<Int>()
        for ((shift, _) in candidates) {
            if (hypotheses.all { abs(shift - it) > max(12, height / 90) }) hypotheses.add(shift)
            if (hypotheses.size == 12) break
        }
        var refined = -1
        var refinedScore = Double.POSITIVE_INFINITY
        for (candidate in hypotheses) {
            for (shift in max(minShift, candidate - stride)..min(maxShift, candidate + stride)) {
                val value = score(before, after, shift, precise = true)
                if (value < refinedScore) {
                    refined = shift
                    refinedScore = value
                }
            }
        }
        if (refined < 0) return Alignment.Uncertain
        if (refinedScore > MAX_ACCEPTABLE_ERROR) return Alignment.Uncertain
        // Validate the exact last overlapping scanlines: an accurate global score alone
        // can hide a dynamic bottom toolbar, leaving a visible seam in the PNG.
        if (seamScore(before, after, refined) > 15.0) return Alignment.Uncertain

        // Similar-looking/repeated rows can yield a deceptively good but false alignment.
        // Look for a distinct second solution away from the winning peak.
        var alternative = Double.POSITIVE_INFINITY
        for (shift in minShift..maxShift step stride) {
            if (abs(shift - refined) > max(12, height / 90)) {
                alternative = min(alternative, score(before, after, shift, precise = false))
            }
        }
        if (alternative < refinedScore * 1.45 + MIN_DISTINCTIVENESS) return Alignment.Uncertain

        // Require that nonblank structural details actually agree, not just plain wallpaper.
        if (texturedMatchFraction(before, after, refined) < 0.52) return Alignment.Uncertain
        return Alignment.Scrolled(refined, refinedScore)
    }

    private fun score(a: ColumnFrame, b: ColumnFrame, shift: Int, precise: Boolean): Double {
        // Margin avoids sticky section headings and fading edges; both have independent motion.
        val top = max(3, a.height / 17)
        val bottom = a.height - max(3, a.height / 22) - shift
        if (bottom <= top + 12) return Double.POSITIVE_INFINITY
        val sampleLines = if (precise) 105 else 27
        val step = max(1, (bottom - top) / sampleLines)
        val colStep = if (precise) 1 else 2
        var total = 0L
        var count = 0
        var y = top
        while (y < bottom) {
            var x = 0
            while (x < a.columns) {
                total += distance(a.pixel(y + shift, x), b.pixel(y, x)).toLong()
                count++
                x += colStep
            }
            y += step
        }
        return if (count == 0) Double.POSITIVE_INFINITY else total.toDouble() / count
    }

    /** Fraction of visually detailed sample points whose colors still match at the seam. */
    private fun seamScore(a: ColumnFrame, b: ColumnFrame, shift: Int): Double {
        val overlap = a.height - shift
        if (overlap <= 20) return Double.POSITIVE_INFINITY
        var total = 0L
        var count = 0
        for (y in max(0, overlap - 32) until overlap) {
            for (x in 0 until a.columns) {
                total += distance(a.pixel(y + shift, x), b.pixel(y, x)).toLong()
                count++
            }
        }
        return if (count == 0) Double.POSITIVE_INFINITY else total.toDouble() / count
    }

    private fun texturedMatchFraction(a: ColumnFrame, b: ColumnFrame, shift: Int): Double {
        val top = max(16, a.height / 17)
        val bottom = a.height - max(4, a.height / 22) - shift
        val step = max(3, (bottom - top) / 100)
        var detailed = 0
        var matched = 0
        var y = top
        while (y < bottom) {
            for (x in 0 until a.columns) {
                val current = a.pixel(y + shift, x)
                val above = a.pixel(max(0, y + shift - 9), x)
                if (distance(current, above) > 18) {
                    detailed++
                    if (distance(current, b.pixel(y, x)) < 26) matched++
                }
            }
            y += step
        }
        return if (detailed < 35) 0.0 else matched.toDouble() / detailed
    }

    private fun changedFraction(a: ColumnFrame, b: ColumnFrame): Double {
        var changed = 0
        var count = 0
        for (y in 0 until a.height step 9) for (x in 0 until a.columns) {
            if (distance(a.pixel(y, x), b.pixel(y, x)) > 12) changed++
            count++
        }
        return if (count == 0) 1.0 else changed.toDouble() / count
    }

    private fun distance(a: Int, b: Int): Int {
        // Alpha intentionally ignored: real screenshots are opaque.
        val r = abs((a ushr 16 and 0xff) - (b ushr 16 and 0xff))
        val g = abs((a ushr 8 and 0xff) - (b ushr 8 and 0xff))
        val bl = abs((a and 0xff) - (b and 0xff))
        return (r + g + bl) / 3
    }
}
