package com.salmix.pillbox.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY id ASC")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getMedicineById(id: Long): Medicine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine): Long

    @Update
    suspend fun updateMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Query("UPDATE medicines SET isTakenToday = :taken, lastTakenTime = :time WHERE id = :id")
    suspend fun updateDoseStatus(id: Long, taken: Boolean, time: String)

    @Query("UPDATE medicines SET stock = stock - 1 WHERE id = :id AND stock > 0")
    suspend fun decrementStock(id: Long)

    @Query("UPDATE medicines SET stock = stock + :amount WHERE id = :id")
    suspend fun refillStock(id: Long, amount: Int)
}
