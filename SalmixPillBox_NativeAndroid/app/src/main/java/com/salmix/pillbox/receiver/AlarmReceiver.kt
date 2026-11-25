package com.salmix.pillbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salmix.pillbox.service.AlarmSoundService
import com.salmix.pillbox.ui.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra("MED_ID", -1L)
        val medName = intent.getStringExtra("MED_NAME") ?: "داروی شما"
        val medDetails = intent.getStringExtra("MED_DETAILS") ?: "زمان مصرف دارو رسیده است"

        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
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
            putExtra(AlarmSoundService.EXTRA_MED_NAME, medName)
            putExtra(AlarmSoundService.EXTRA_MED_DETAILS, medDetails)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(activityIntent)
    }
}
