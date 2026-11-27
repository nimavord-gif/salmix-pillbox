package com.salmix.pillbox.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salmix.pillbox.data.Medicine
import com.salmix.pillbox.receiver.AlarmReceiver
import java.util.Calendar

data class AlarmSlotTimes(
    val morning: String = "08:30",
    val noon: String = "13:30",
    val night: String = "20:30",
    val bed: String = "23:00"
)

object PreferencesHelper {
    private const val PREF_NAME = "salmix_pillbox_prefs"
    private const val KEY_MORNING = "alarm_morning"
    private const val KEY_NOON = "alarm_noon"
    private const val KEY_NIGHT = "alarm_night"
    private const val KEY_BED = "alarm_bed"
    private const val KEY_WATER_CUPS = "water_cups"
    private const val KEY_WATER_DATE = "water_date"
    private const val KEY_DOCTOR_NAME = "doctor_name"
    private const val KEY_DOCTOR_PHONE = "doctor_phone"
    private const val KEY_STREAK = "streak_days"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAlarmTimes(context: Context): AlarmSlotTimes {
        val p = prefs(context)
        return AlarmSlotTimes(
            morning = p.getString(KEY_MORNING, "08:30") ?: "08:30",
            noon = p.getString(KEY_NOON, "13:30") ?: "13:30",
            night = p.getString(KEY_NIGHT, "20:30") ?: "20:30",
            bed = p.getString(KEY_BED, "23:00") ?: "23:00"
        )
    }

    fun saveAlarmTime(context: Context, slot: String, time: String) {
        val key = when (slot) {
            "morning" -> KEY_MORNING
            "noon" -> KEY_NOON
            "night" -> KEY_NIGHT
            "bed" -> KEY_BED
            else -> return
        }
        prefs(context).edit().putString(key, time).apply()
    }

    fun getWaterCups(context: Context): Int {
        val p = prefs(context)
        val today = getTodayKey()
        val savedDate = p.getString(KEY_WATER_DATE, "")
        return if (savedDate == today) p.getInt(KEY_WATER_CUPS, 0) else 0
    }

    fun saveWaterCups(context: Context, cups: Int) {
        prefs(context).edit()
            .putString(KEY_WATER_DATE, getTodayKey())
            .putInt(KEY_WATER_CUPS, cups.coerceIn(0, 12))
            .apply()
    }

    fun getDoctorInfo(context: Context): Pair<String, String> {
        val p = prefs(context)
        return (p.getString(KEY_DOCTOR_NAME, "") ?: "") to (p.getString(KEY_DOCTOR_PHONE, "") ?: "")
    }

    fun saveDoctorInfo(context: Context, name: String, phone: String) {
        prefs(context).edit()
            .putString(KEY_DOCTOR_NAME, name.take(60))
            .putString(KEY_DOCTOR_PHONE, phone.take(20))
            .apply()
    }

    fun getStreakDays(context: Context): Int = prefs(context).getInt(KEY_STREAK, 1)

    private fun getTodayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }
}

object AlarmScheduler {
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleAlarmsForMedicine(context: Context, med: Medicine) {
        if (!med.isActive || med.frequencyType == "PRN") return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val slotTimes = PreferencesHelper.getAlarmTimes(context)
        val slots = med.timeSlots.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        slots.forEach { slot ->
            val timeStr = when (slot) {
                "morning" -> slotTimes.morning
                "noon" -> slotTimes.noon
                "night" -> slotTimes.night
                "bed" -> slotTimes.bed
                "custom" -> med.customTime.ifBlank { "09:00" }
                else -> return@forEach
            }

            val (hour, minute) = AppUtils.parseTime(timeStr)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("MED_ID", med.id)
                putExtra("MED_NAME", med.name)
                putExtra("MED_DETAILS", "${med.dosage} ${med.dosageUnit} • ${med.mealInstruction}")
                putExtra("ALARM_SLOT", slot)
            }

            val reqCode = AppUtils.alarmRequestCode(med.id, slot)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                }
            } catch (se: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        }
    }

    fun cancelAlarmsForMedicine(context: Context, med: Medicine) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val slots = listOf("morning", "noon", "night", "bed", "custom")
        slots.forEach { slot ->
            val intent = Intent(context, AlarmReceiver::class.java)
            val reqCode = AppUtils.alarmRequestCode(med.id, slot)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
