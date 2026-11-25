package com.salmix.pillbox.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.receiver.AlarmReceiver
import com.salmix.pillbox.service.AlarmSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {
    private var medId: Long = -1L
    private var medName: String = "داروی شما"
    private var medDetails: String = "زمان مصرف دارو رسیده است"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        medId = intent.getLongExtra("MED_ID", -1L)
        medName = intent.getStringExtra(AlarmSoundService.EXTRA_MED_NAME) ?: "داروی شما"
        medDetails = intent.getStringExtra(AlarmSoundService.EXTRA_MED_DETAILS) ?: "زمان مصرف دارو رسیده است"

        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0B1329)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF152243))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔔 وقت مصرف دارو رسید!", color = Color(0xFFFBBF24), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(medName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(medDetails, color = Color(0xFF94A3B8), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                stopAlarmService()
                                if (medId != -1L) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val db = AppDatabase.getDatabase(applicationContext)
                                        db.medicineDao().updateDoseStatus(medId, true, "الان")
                                        db.medicineDao().decrementStock(medId)
                                    }
                                }
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("همین الان مصرف کردم ✓", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                stopAlarmService()
                                snoozeAlarm()
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("۵ دقیقه بعد یادآوری کن", color = Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
    }

    private fun snoozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DETAILS", medDetails)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, (medId + 9999).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 300000L, pendingIntent
        )
    }
}
