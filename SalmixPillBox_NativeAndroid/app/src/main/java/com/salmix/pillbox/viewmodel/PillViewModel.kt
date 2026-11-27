package com.salmix.pillbox.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.data.Medicine
import com.salmix.pillbox.service.AlarmSoundService
import com.salmix.pillbox.util.AlarmScheduler
import com.salmix.pillbox.util.AlarmSlotTimes
import com.salmix.pillbox.util.AppUtils
import com.salmix.pillbox.util.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class PillViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.medicineDao()

    val allMedicines: StateFlow<List<Medicine>> = dao.getAllMedicines()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _waterCups = MutableStateFlow(PreferencesHelper.getWaterCups(application))
    val waterCups: StateFlow<Int> = _waterCups.asStateFlow()

    private val _streakDays = MutableStateFlow(PreferencesHelper.getStreakDays(application))
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _alarmTimes = MutableStateFlow(PreferencesHelper.getAlarmTimes(application))
    val alarmTimes: StateFlow<AlarmSlotTimes> = _alarmTimes.asStateFlow()

    private val _doctorInfo = MutableStateFlow(PreferencesHelper.getDoctorInfo(application))
    val doctorInfo: StateFlow<Pair<String, String>> = _doctorInfo.asStateFlow()

    fun addMedicine(
        name: String,
        type: String,
        color: String,
        dosage: String,
        dosageUnit: String,
        timeSlots: List<String>,
        customTime: String,
        frequencyType: String,
        daysOfWeek: String,
        mealInstruction: String,
        stock: Int,
        minStock: Int,
        expiryDate: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanSlots = if (frequencyType == "PRN") emptyList() else timeSlots
            val med = Medicine(
                name = name.trim().take(60),
                type = type,
                color = color,
                dosage = dosage.trim().take(30),
                dosageUnit = dosageUnit,
                timeSlots = cleanSlots.joinToString(","),
                customTime = customTime,
                frequencyType = frequencyType,
                daysOfWeek = daysOfWeek,
                mealInstruction = mealInstruction,
                stock = stock.coerceIn(0, 10000),
                minStock = minStock.coerceIn(0, 1000),
                expiryDate = expiryDate.trim().take(15),
                notes = notes.trim().take(120)
            )
            val newId = dao.insertMedicine(med)
            AlarmScheduler.scheduleAlarmsForMedicine(getApplication(), med.copy(id = newId))
        }
    }

    fun toggleSlotDose(med: Medicine, slotKey: String, isTaken: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val timeNow = AppUtils.formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val todayDate = AppUtils.getTodayDateString()
            dao.toggleSlotDoseAtomic(med.id, slotKey, isTaken, timeNow, todayDate)
        }
    }

    fun toggleMedicineActive(med: Medicine) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextActive = !med.isActive
            dao.setActiveStatus(med.id, nextActive)
            val updated = med.copy(isActive = nextActive)
            if (nextActive) {
                AlarmScheduler.scheduleAlarmsForMedicine(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarmsForMedicine(getApplication(), updated)
            }
        }
    }

    fun resetWaterCups() {
        _waterCups.value = 0
        PreferencesHelper.saveWaterCups(getApplication(), 0)
    }

    fun deleteMedicine(med: Medicine) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancelAlarmsForMedicine(getApplication(), med)
            dao.deleteMedicine(med)
        }
    }

    fun refillStock(medId: Long, amount: Int = 30) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.refillStock(medId, amount.coerceAtLeast(1))
        }
    }

    fun updateAlarmTime(slot: String, hour: Int, minute: Int) {
        val formatted = AppUtils.formatTime(hour, minute)
        PreferencesHelper.saveAlarmTime(getApplication(), slot, formatted)
        _alarmTimes.value = PreferencesHelper.getAlarmTimes(getApplication())

        // Reschedule all existing medicines with new slot time
        viewModelScope.launch(Dispatchers.IO) {
            val meds = dao.getAllMedicinesOnce()
            meds.forEach { med ->
                AlarmScheduler.cancelAlarmsForMedicine(getApplication(), med)
                AlarmScheduler.scheduleAlarmsForMedicine(getApplication(), med)
            }
        }
    }

    fun addWaterCup() {
        val next = (_waterCups.value + 1).coerceAtMost(8)
        _waterCups.value = next
        PreferencesHelper.saveWaterCups(getApplication(), next)
    }

    fun removeWaterCup() {
        val next = (_waterCups.value - 1).coerceAtLeast(0)
        _waterCups.value = next
        PreferencesHelper.saveWaterCups(getApplication(), next)
    }

    fun saveDoctorInfo(name: String, phone: String) {
        PreferencesHelper.saveDoctorInfo(getApplication(), name, phone)
        _doctorInfo.value = PreferencesHelper.getDoctorInfo(getApplication())
    }

    fun triggerTestAlarm() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AlarmSoundService::class.java).apply {
            putExtra("MED_ID", -1L)
            putExtra(AlarmSoundService.EXTRA_MED_NAME, "تست صدای زنگ سالمیکس")
            putExtra(AlarmSoundService.EXTRA_MED_DETAILS, "آزمایش پخش صدا و ویبره در زمان مصرف دارو")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun stopTestAlarm() {
        val ctx = getApplication<Application>()
        try {
            ctx.stopService(Intent(ctx, AlarmSoundService::class.java))
        } catch (_: Exception) {}
    }
}
