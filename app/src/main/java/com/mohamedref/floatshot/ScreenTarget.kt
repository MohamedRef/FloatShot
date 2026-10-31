package com.mohamedref.floatshot

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max

/** Uses only scrollable nodes' bounds and package name, never their text or content. */
internal object ScreenTarget {
    data class Target(val viewportOnScreen: Rect, val windowId: Int, val rootBounds: Rect, val packageName: String)

    /** Android 15+: a fractional accessibility scroll can prevent inertia and skipped content. */
    fun scrollPrecisely(service: ScreenshotAccessibilityService, target: Target): Boolean {
        if (Build.VERSION.SDK_INT < 35 || target.packageName.isEmpty()) return false
        val root = service.rootInActiveWindow ?: return false
        if (root.packageName?.toString() != target.packageName) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count++ < 350) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.isGranularScrollingSupported) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val expected = target.viewportOnScreen
                if (kotlin.math.abs(bounds.top - expected.top) < expected.height() / 8 &&
                    kotlin.math.abs(bounds.bottom - expected.bottom) < expected.height() / 8 &&
                    node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) {
                    val args = Bundle().apply {
                        putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, 0.53f)
                    }
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, args)) return true
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    fun discover(service: ScreenshotAccessibilityService, width: Int, height: Int): Target? {
        val root = service.rootInActiveWindow ?: return null
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg == service.packageName || pkg.isEmpty()) return null
        var best: Rect? = null
        var bestArea = 0L
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count++ < 350) {
            val node = queue.removeFirst()
            if (node.isScrollable) {
                val box = Rect()
                node.getBoundsInScreen(box)
                if (!box.intersect(0, 0, width, height)) continue
                val area = box.width().toLong() * box.height()
                if (box.width() >= width * 0.83 && box.height() >= height * 0.27 && area > bestArea) {
                    best = box
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        val region = best ?: Rect(0, (height * 0.09).toInt(), width, (height * 0.91).toInt())
        // Keep a small safe margin for the status/navigation areas if no scroll node was found.
        if (region.height() < max(120, height / 4)) return null
        return Target(region, root.windowId, rootBounds, pkg)
    }
}
