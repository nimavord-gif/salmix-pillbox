package com.salmix.pillbox

import com.salmix.pillbox.util.AppUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillBoxUnitTest {
    @Test
    fun persianDigitsAreNormalizedProperly() {
        assertEquals("50", AppUtils.normalizeDigits("۵۰"))
        assertEquals("08:30", AppUtils.normalizeDigits("۰۸:۳۰"))
        assertEquals(50, AppUtils.parseSafeInt("۵۰", defaultValue = 30))
        assertEquals(0, AppUtils.parseSafeInt("-50", defaultValue = 30, min = 0))
    }

    @Test
    fun waterPercentageReaches100PercentOnEightCups() {
        assertEquals(0, AppUtils.calculateWaterPercent(0, 8))
        assertEquals(50, AppUtils.calculateWaterPercent(4, 8))
        assertEquals(100, AppUtils.calculateWaterPercent(8, 8))
    }

    @Test
    fun alarmRequestCodesArePositiveAndUnique() {
        val c1 = AppUtils.alarmRequestCode(1L, "morning")
        val c2 = AppUtils.alarmRequestCode(1L, "noon")
        val c3 = AppUtils.alarmRequestCode(2L, "morning")
        assertTrue(c1 > 0)
        assertTrue(c2 > 0)
        assertTrue(c3 > 0)
        assertNotEquals(c1, c2)
        assertNotEquals(c1, c3)
    }

    @Test
    fun timeParsingHandlesInvalidInputsSafely() {
        assertEquals(8 to 30, AppUtils.parseTime("۰۸:۳۰"))
        assertEquals(23 to 59, AppUtils.parseTime("23:59"))
        assertEquals(8 to 30, AppUtils.parseTime("invalid"))
    }

    @Test
    fun perSlotTrackingResetsOnNewDateAndIsolatesSlots() {
        assertTrue(AppUtils.isSlotTakenToday("2026-11-27", "morning", "morning", "2026-11-27"))
        assertFalse(AppUtils.isSlotTakenToday("2026-11-27", "morning", "night", "2026-11-27"))
        assertFalse(AppUtils.isSlotTakenToday("2026-11-26", "morning", "morning", "2026-11-27"))
    }

    @Test
    fun estimateDaysRemainingCalculatesProperly() {
        assertEquals(15, AppUtils.estimateDaysRemaining(30, "morning,night", "DAILY"))
        assertEquals(70, AppUtils.estimateDaysRemaining(10, "morning", "WEEKLY"))
        assertEquals(0, AppUtils.estimateDaysRemaining(10, "", "PRN"))
    }
}
