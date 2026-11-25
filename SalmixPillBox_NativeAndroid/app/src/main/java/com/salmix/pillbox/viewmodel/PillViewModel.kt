package com.salmix.pillbox.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.data.Medicine
import com.salmix.pillbox.receiver.AlarmReceiver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class PillViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.medicineDao()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val allMedicines: StateFlow<List<Medicine>> = dao.getAllMedicines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _waterCups = MutableStateFlow(0)
    val waterCups: StateFlow<Int> = _waterCups.asStateFlow()

    private val _streakDays = MutableStateFlow(3)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    fun addMedicine(
        name: String, type: String, color: String, dosage: String,
        timeSlots: List<String>, mealInstruction: String, stock: Int, minStock: Int, notes: String
    ) {
        viewModelScope.launch {
            val med = Medicine(
                name = name, type = type, color = color, dosage = dosage,
                timeSlots = timeSlots.joinToString(","), mealInstruction = mealInstruction,
                stock = stock, minStock = minStock, notes = notes
            )
            val newId = dao.insertMedicine(med)
            scheduleAlarmsForMedicine(med.copy(id = newId), timeSlots)
        }
    }

    fun toggleDose(med: Medicine, isTaken: Boolean) {
        viewModelScope.launch {
            dao.updateDoseStatus(med.id, isTaken, if (isTaken) "الان" else "")
            if (isTaken && med.stock > 0) dao.decrementStock(med.id)
            else if (!isTaken) dao.refillStock(med.id, 1)
        }
    }

    fun deleteMedicine(med: Medicine) {
        viewModelScope.launch {
            dao.deleteMedicine(med)
        }
    }

    fun refillStock(medId: Long, amount: Int = 30) {
        viewModelScope.launch {
            dao.refillStock(medId, amount)
        }
    }

    fun addWaterCup() { if (_waterCups.value < 12) _waterCups.value += 1 }
    fun removeWaterCup() { if (_waterCups.value > 0) _waterCups.value -= 1 }

    private fun scheduleAlarmsForMedicine(med: Medicine, slots: List<String>) {
        val context = getApplication<Application>()
        slots.forEach { slot ->
            val cal = Calendar.getInstance()
            when (slot) {
                "morning" -> { cal.set(Calendar.HOUR_OF_DAY, 8); cal.set(Calendar.MINUTE, 30) }
                "noon" -> { cal.set(Calendar.HOUR_OF_DAY, 13); cal.set(Calendar.MINUTE, 30) }
                "night" -> { cal.set(Calendar.HOUR_OF_DAY, 20); cal.set(Calendar.MINUTE, 30) }
                "bed" -> { cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 0) }
            }
            cal.set(Calendar.SECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("MED_ID", med.id)
                putExtra("MED_NAME", med.name)
                putExtra("MED_DETAILS", "${med.dosage} • ${med.mealInstruction}")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, (med.id * 10 + slot.hashCode()).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }
}
