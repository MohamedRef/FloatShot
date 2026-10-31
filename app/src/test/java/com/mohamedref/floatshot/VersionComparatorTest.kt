package com.mohamedref.floatshot

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionComparatorTest {
    @Test fun detectsNewerPatch() {
        assertEquals(true, VersionComparator.isNewer("v0.2.1", "0.2.0"))
    }

    @Test fun comparesNumbersRatherThanAlphabetically() {
        assertEquals(true, VersionComparator.isNewer("v0.10.0", "0.9.9"))
        assertEquals(false, VersionComparator.isNewer("1.2.9", "1.10.0"))
    }

    @Test fun handlesOptionalPrefixAndBuildMetadata() {
        assertEquals(false, VersionComparator.isNewer("v1.2.0+build.9", "1.2"))
    }

    @Test fun stableReleaseBeatsPrerelease() {
        assertEquals(true, VersionComparator.isNewer("1.2.0", "1.2.0-rc.2"))
        assertEquals(false, VersionComparator.isNewer("1.2.0-beta.1", "1.2.0"))
    }

    @Test fun comparesPrereleaseIdentifiers() {
        assertEquals(true, VersionComparator.isNewer("2.0.0-beta.10", "2.0.0-beta.2"))
        assertEquals(false, VersionComparator.isNewer("2.0.0-alpha", "2.0.0-beta"))
    }

    @Test fun rejectsInvalidReleaseTags() {
        assertEquals(null, VersionComparator.isNewer("release-next", "0.2.0"))
        assertEquals(null, VersionComparator.isNewer("v1.2.3", "nightly"))
    }

    @Test fun handlesVeryLargeVersionNumbers() {
        assertEquals(true, VersionComparator.isNewer("99999999999999999.1.0", "9999999999999999.9.9"))
    }
}
