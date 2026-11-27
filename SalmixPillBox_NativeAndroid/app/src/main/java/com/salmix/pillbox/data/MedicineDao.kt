package com.salmix.pillbox.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY id ASC")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines ORDER BY id ASC")
    suspend fun getAllMedicinesOnce(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getMedicineById(id: Long): Medicine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine): Long

    @Update
    suspend fun updateMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Query("UPDATE medicines SET stock = stock + :amount WHERE id = :id")
    suspend fun refillStock(id: Long, amount: Int)

    @Query("UPDATE medicines SET isActive = :active WHERE id = :id")
    suspend fun setActiveStatus(id: Long, active: Boolean)

    /**
     * Atomic per-slot dose toggle with automatic midnight date reset and anti-glitch stock tracking.
     */
    @Transaction
    suspend fun toggleSlotDoseAtomic(
        id: Long,
        slotKey: String,
        markTaken: Boolean,
        timeLabel: String,
        todayDate: String
    ) {
        val med = getMedicineById(id) ?: return

        // If the stored date is from a previous day, reset today's taken slots cleanly
        val currentTakenSlots = if (med.lastTakenDate == todayDate) {
            med.takenSlotsToday.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        } else {
            mutableSetOf()
        }

        val wasSlotTaken = currentTakenSlots.contains(slotKey)
        if (wasSlotTaken == markTaken) return

        var newStock = med.stock
        if (markTaken) {
            currentTakenSlots.add(slotKey)
            if (newStock > 0) {
                newStock -= 1
            }
        } else {
            currentTakenSlots.remove(slotKey)
            if (med.lastTakenDate == todayDate && med.stockDecrementedToday) {
                newStock += 1
            }
        }

        val allSlots = med.timeSlots.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val allDoneToday = if (allSlots.isEmpty()) currentTakenSlots.isNotEmpty() else currentTakenSlots.containsAll(allSlots)

        updateMedicine(
            med.copy(
                takenSlotsToday = currentTakenSlots.joinToString(","),
                lastTakenDate = todayDate,
                isTakenToday = allDoneToday,
                lastTakenTime = if (markTaken) timeLabel else med.lastTakenTime,
                stock = newStock.coerceAtLeast(0),
                stockDecrementedToday = if (markTaken) (med.stock > 0) else false
            )
        )
    }
}
