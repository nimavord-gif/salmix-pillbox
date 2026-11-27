package com.salmix.pillbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.service.AlarmSoundService
import com.salmix.pillbox.ui.AlarmActivity
import com.salmix.pillbox.util.AlarmScheduler
import com.salmix.pillbox.util.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra("MED_ID", -1L)
        val alarmSlot = intent.getStringExtra("ALARM_SLOT") ?: "morning"
        val medName = intent.getStringExtra("MED_NAME") ?: "داروی شما"
        val medDetails = intent.getStringExtra("MED_DETAILS") ?: "زمان مصرف دارو رسیده است"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var shouldRing = true
            try {
                if (medId != -1L) {
                    val med = AppDatabase.getDatabase(context).medicineDao().getMedicineById(medId)
                    if (med != null) {
                        if (med.frequencyType == "WEEKLY") {
                            val todayCode = AppUtils.getTodayDayCode()
                            val allowedDays = med.daysOfWeek.split(",").map { it.trim() }
                            if (allowedDays.isNotEmpty() && !allowedDays.contains(todayCode)) {
                                shouldRing = false
                            }
                        }
                        // Reschedule for tomorrow automatically
                        AlarmScheduler.scheduleAlarmsForMedicine(context, med)
                    }
                }
            } finally {
                if (shouldRing) {
                    triggerAlarmUIAndService(context, medId, alarmSlot, medName, medDetails)
                }
                pendingResult.finish()
            }
        }
    }

    private fun triggerAlarmUIAndService(
        context: Context,
        medId: Long,
        alarmSlot: String,
        medName: String,
        medDetails: String
    ) {
        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            putExtra("MED_ID", medId)
            putExtra("ALARM_SLOT", alarmSlot)
            putExtra(AlarmSoundService.EXTRA_MED_NAME, medName)
            putExtra(AlarmSoundService.EXTRA_MED_DETAILS, medDetails)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("MED_ID", medId)
            putExtra("ALARM_SLOT", alarmSlot)
            putExtra(AlarmSoundService.EXTRA_MED_NAME, medName)
            putExtra(AlarmSoundService.EXTRA_MED_DETAILS, medDetails)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
