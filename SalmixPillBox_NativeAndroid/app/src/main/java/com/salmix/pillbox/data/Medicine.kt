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
    val timeSlots: String,
    val mealInstruction: String,
    val stock: Int,
    val minStock: Int,
    val notes: String = "",
    val isTakenToday: Boolean = false,
    val lastTakenTime: String = ""
)
