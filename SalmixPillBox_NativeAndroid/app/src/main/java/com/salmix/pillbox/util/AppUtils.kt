package com.salmix.pillbox.util

import java.util.Calendar

object AppUtils {
    /**
     * Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII (0-9).
     */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Safely parses an integer from user input supporting Persian/Arabic digits,
     * clamping to [min, max].
     */
    fun parseSafeInt(input: String, defaultValue: Int, min: Int = 0, max: Int = 10000): Int {
        val normalized = normalizeDigits(input).trim()
        val parsed = normalized.toIntOrNull() ?: return defaultValue
        return parsed.coerceIn(min, max)
    }

    /**
     * Computes water intake percentage cleanly (8 cups = 100%).
     */
    fun calculateWaterPercent(cups: Int, targetCups: Int = 8): Int {
        if (targetCups <= 0) return 0
        return ((cups.toFloat() / targetCups.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    /**
     * Generates a positive, collision-free PendingIntent requestCode for a medicine and slot.
     */
    fun alarmRequestCode(medId: Long, slot: String): Int {
        val slotIndex = when (slot) {
            "morning" -> 1
            "noon" -> 2
            "night" -> 3
            "bed" -> 4
            "custom" -> 5
            else -> 6
        }
        val safeId = (medId.coerceAtLeast(0L) % 100_000L).toInt()
        return safeId * 10 + slotIndex
    }

    /**
     * Parses "HH:mm" safely into Pair(hour, minute).
     */
    fun parseTime(timeStr: String, defaultHour: Int = 8, defaultMinute: Int = 30): Pair<Int, Int> {
        val normalized = normalizeDigits(timeStr).trim()
        val parts = normalized.split(":")
        if (parts.size != 2) return defaultHour to defaultMinute
        val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
        val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute
        return h to m
    }

    /**
     * Formats hour and minute as "HH:mm".
     */
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    /**
     * Returns today's date string in yyyy-MM-dd format for automatic midnight dose reset.
     */
    fun getTodayDateString(): String {
        val c = Calendar.getInstance()
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * Checks if a specific slot of a medicine was taken today (resets automatically at midnight).
     */
    fun isSlotTakenToday(lastTakenDate: String, takenSlotsToday: String, slotKey: String, todayDate: String = getTodayDateString()): Boolean {
        if (lastTakenDate != todayDate) return false
        return takenSlotsToday.split(",").map { it.trim() }.contains(slotKey)
    }

    /**
     * Estimates how many days the current stock will last based on daily slots.
     */
    fun estimateDaysRemaining(stock: Int, timeSlots: String, frequencyType: String): Int {
        if (stock <= 0 || frequencyType == "PRN") return 0
        val slotsCount = timeSlots.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size.coerceAtLeast(1)
        return if (frequencyType == "WEEKLY") {
            stock * 7
        } else {
            stock / slotsCount
        }
    }

    /**
     * Returns today's day code matching our weekly schedule tags: SAT, SUN, MON, TUE, WED, THU, FRI.
     */
    fun getTodayDayCode(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            else -> "SAT"
        }
    }
}
