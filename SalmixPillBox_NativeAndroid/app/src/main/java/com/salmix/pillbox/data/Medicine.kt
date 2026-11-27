package com.salmix.pillbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val color: String,
    val dosage: String,
    val timeSlots: String,       // comma-separated: "morning,night" or "custom"
    val mealInstruction: String,
    val stock: Int,
    val minStock: Int,
    val notes: String = "",
    val isTakenToday: Boolean = false,
    val lastTakenTime: String = "",
    // V2 fields
    val customTime: String = "",
    val frequencyType: String = "DAILY", // "DAILY", "WEEKLY", "PRN"
    val daysOfWeek: String = "",
    val expiryDate: String = "",
    val dosageUnit: String = "عدد",
    val stockDecrementedToday: Boolean = false,
    // V3 clinical & per-slot tracking fields
    val takenSlotsToday: String = "",    // comma-separated taken slots for today, e.g. "morning"
    val lastTakenDate: String = "",      // yyyy-MM-dd to auto-reset at midnight
    val isActive: Boolean = true,        // pause/archive without deleting
    val createdAt: Long = System.currentTimeMillis()
)
