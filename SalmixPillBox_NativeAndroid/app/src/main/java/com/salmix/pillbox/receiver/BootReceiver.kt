package com.salmix.pillbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val meds = AppDatabase.getDatabase(context).medicineDao().getAllMedicinesOnce()
                    meds.forEach { med ->
                        AlarmScheduler.scheduleAlarmsForMedicine(context, med)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
