package com.salmix.pillbox.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.salmix.pillbox.data.AppDatabase
import com.salmix.pillbox.receiver.AlarmReceiver
import com.salmix.pillbox.service.AlarmSoundService
import com.salmix.pillbox.util.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmActivity : ComponentActivity() {
    private var medId: Long = -1L
    private var medName: String = "داروی شما"
    private var medDetails: String = "زمان مصرف دارو رسیده است"
    private var alarmSlot: String = "morning"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Unconditionally keep screen on during alarm on all Android versions
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Stop alarm cleanly on system back button or swipe back gesture
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopAlarmService()
                finish()
            }
        })

        medId = intent.getLongExtra("MED_ID", -1L)
        medName = intent.getStringExtra(AlarmSoundService.EXTRA_MED_NAME) ?: "داروی شما"
        medDetails = intent.getStringExtra(AlarmSoundService.EXTRA_MED_DETAILS) ?: "زمان مصرف دارو رسیده است"
        alarmSlot = intent.getStringExtra("ALARM_SLOT") ?: "morning"

        setContent {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bell_scale"
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B1329))
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF152243)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .scale(scale)
                                    .background(Color(0xFFFBBF24).copy(alpha = 0.18f), CircleShape)
                                    .border(2.dp, Color(0xFFFBBF24), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "آلارم",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "وقت مصرف دارو رسید!",
                                color = Color(0xFFFBBF24),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                medName,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                medDetails,
                                color = Color(0xFFB0BEC5),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(26.dp))
                            Button(
                                onClick = {
                                    stopAlarmService()
                                    if (medId != -1L) {
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val cal = Calendar.getInstance()
                                                val timeNow = AppUtils.formatTime(
                                                    cal.get(Calendar.HOUR_OF_DAY),
                                                    cal.get(Calendar.MINUTE)
                                                )
                                                val todayDate = AppUtils.getTodayDateString()
                                                val db = AppDatabase.getDatabase(applicationContext)
                                                db.medicineDao().toggleSlotDoseAtomic(
                                                    medId, alarmSlot, true, timeNow, todayDate
                                                )
                                            }
                                            finish()
                                        }
                                    } else {
                                        finish()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                            ) {
                                Text("همین الان مصرف کردم ✓", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    stopAlarmService()
                                    snoozeAlarm()
                                    finish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("۵ دقیقه بعد یادآوری کن", color = Color(0xFFE2E8F0))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            stopAlarmService()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stopAlarmService() {
        try {
            stopService(Intent(this, AlarmSoundService::class.java))
        } catch (_: Exception) {}
    }

    private fun snoozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DETAILS", medDetails)
            putExtra("ALARM_SLOT", alarmSlot)
        }
        val reqCode = ((medId.coerceAtLeast(0L) % 100_000L).toInt() * 10) + 9
        val pendingIntent = PendingIntent.getBroadcast(
            this, reqCode, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtElapsed = SystemClock.elapsedRealtime() + 5 * 60 * 1000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
        }
    }
}
