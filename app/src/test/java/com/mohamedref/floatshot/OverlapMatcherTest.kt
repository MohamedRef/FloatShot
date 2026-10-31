package com.mohamedref.floatshot

import org.junit.Assert.*
import org.junit.Test

class OverlapMatcherTest {
    private val h = 930
    private val cols = 72

    /** Deterministic synthetic page: light backgrounds, repeated article cards, tiny glyphs. */
    private fun article(row: Int, col: Int): Int {
        val card = row / 260
        val localY = row % 260
        val bg = if (card % 2 == 0) 0xfff9f9f9.toInt() else 0xffffffff.toInt()
        // Tiny reproducible characters in text rows, interspersed with blank space.
        if (localY in 40..203 && localY % 22 in 0..11 && col in 7..66) {
            val ch = (row / 6 * 71 + col * 97 + card * 37) xor (col * row)
            if ((ch and 15) < 6) return 0xff202a30.toInt()
        }
        if (localY in 6..24 && col in 6..43) return 0xffb3c7dd.toInt()
        if (localY in 245..251) return 0xffdfdfdf.toInt()
        return bg
    }

    private fun page(scroll: Int, pixel: (Int, Int) -> Int = ::article): ColumnFrame =
        ColumnFrame.sample(h, cols) { y, c -> pixel(y + scroll, c) }

    @Test fun `exact overlap after long scrolling`() {
        val result = OverlapMatcher.align(page(125), page(612))
        assertTrue("got: $result", result is Alignment.Scrolled)
        assertEquals(487, (result as Alignment.Scrolled).pixels)
    }

    @Test fun `small end of page movement retains exact pixels`() {
        val result = OverlapMatcher.align(page(200), page(229))
        assertTrue("got: $result", result is Alignment.Scrolled)
        assertEquals(29, (result as Alignment.Scrolled).pixels)
    }

    @Test fun `stationary final frame is never duplicated`() {
        assertEquals(Alignment.Stationary, OverlapMatcher.align(page(800), page(800)))
    }

    @Test fun `repeated empty rows are ambiguous and rejected`() {
        val blank = ColumnFrame.sample(h, cols) { _, _ -> 0xffffffff.toInt() }
        val noise = ColumnFrame.sample(h, cols) { y, c ->
            if (y in 300..370 && c % 6 == 0) 0xffeeeeee.toInt() else 0xffffffff.toInt()
        }
        assertEquals(Alignment.Uncertain, OverlapMatcher.align(blank, noise))
    }

    @Test fun `changing content at the seam is refused even if most rows align`() {
        val shift = 413
        val after = page(shift) { row, column ->
            val y = row - shift
            if (y in h - shift - 20 until h - shift) 0xffdd0033.toInt()
            else article(row, column)
        }
        assertEquals(Alignment.Uncertain, OverlapMatcher.align(page(0), after))
    }

    @Test fun `unrelated app navigation is not stitched`() {
        val unrelated = page(800) { row, col ->
            if ((row + col * 100) % 37 < 18) 0xffce3030.toInt() else 0xff003344.toInt()
        }
        assertEquals(Alignment.Uncertain, OverlapMatcher.align(page(0), unrelated))
    }

    @Test fun `stitch strips cover a page without duplicated headers or missing content`() {
        val top = 104
        val bottom = 850
        val shift = 413
        // Fixed app chrome lives outside the scroll viewport.
        fun image(offset: Int): Array<IntArray> = Array(h) { y ->
            IntArray(cols) { x ->
                when {
                    y < top -> 0xff112233.toInt()
                    y >= bottom -> 0xff334455.toInt()
                    else -> article(offset + y - top, x)
                }
            }
        }
        val first = image(0)
        val second = image(shift)
        val before = ColumnFrame.sample(bottom - top, cols) { y, x -> first[top + y][x] }
        val after = ColumnFrame.sample(bottom - top, cols) { y, x -> second[top + y][x] }
        val delta = OverlapMatcher.align(before, after)
        assertTrue("got: $delta", delta is Alignment.Scrolled)
        assertEquals(shift, (delta as Alignment.Scrolled).pixels)
        val stitched = first.take(bottom) + second.slice(bottom - shift until bottom) +
            second.drop(bottom)
        assertEquals(h + shift, stitched.size)
        for (y in 0 until stitched.size) for (x in 0 until cols) {
            val expected = when {
                y < top -> 0xff112233.toInt()
                y >= bottom + shift -> 0xff334455.toInt()
                else -> article(y - top, x)
            }
            assertEquals("seam ($x,$y)", expected, stitched[y][x])
        }
    }

    @Test fun `identically repeating cards reject ambiguous scroll distances`() {
        val repeat = { row: Int, col: Int -> article(row % 260, col) }
        assertEquals(Alignment.Uncertain,
            OverlapMatcher.align(page(0, repeat), page(180, repeat)))
    }

    @Test fun `pixel stable snapshot passes settling check`() {
        assertTrue(OverlapMatcher.isStationary(page(200), page(200)))
        assertFalse(OverlapMatcher.isStationary(page(200), page(220)))
    }
}
