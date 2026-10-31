package com.mohamedref.floatshot

/**
 * Compares release versions numerically, including pre-release precedence.
 */
internal object VersionComparator {
    private val pattern = Regex("""^[vV]?([0-9]+(?:\.[0-9]+){0,3})(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z.-]+)?$""")

    private data class Version(val numbers: List<String>, val preRelease: List<String>)

    fun isNewer(candidate: String, installed: String): Boolean? {
        val available = parse(candidate) ?: return null
        val current = parse(installed) ?: return null
        return compare(available, current) > 0
    }

    private fun parse(input: String): Version? {
        if (input.length !in 1..80 || input != input.trim()) return null
        val match = pattern.matchEntire(input) ?: return null
        val numbers = match.groupValues[1].split('.')
        val pre = match.groupValues[2]
        return Version(numbers, if (pre.isEmpty()) emptyList() else pre.split('.'))
    }

    private fun compare(a: Version, b: Version): Int {
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val comparison = compareDigits(a.numbers.getOrElse(i) { "0" }, b.numbers.getOrElse(i) { "0" })
            if (comparison != 0) return comparison
        }
        if (a.preRelease.isEmpty() && b.preRelease.isEmpty()) return 0
        if (a.preRelease.isEmpty()) return 1
        if (b.preRelease.isEmpty()) return -1
        for (i in 0 until minOf(a.preRelease.size, b.preRelease.size)) {
            val left = a.preRelease[i]
            val right = b.preRelease[i]
            val leftIsNumeric = left.all { it in '0'..'9' }
            val rightIsNumeric = right.all { it in '0'..'9' }
            val comparison = when {
                leftIsNumeric && rightIsNumeric -> compareDigits(left, right)
                leftIsNumeric -> -1
                rightIsNumeric -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return a.preRelease.size.compareTo(b.preRelease.size)
    }

    private fun compareDigits(left: String, right: String): Int {
        val a = left.trimStart('0').ifEmpty { "0" }
        val b = right.trimStart('0').ifEmpty { "0" }
        return a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
    }
}
