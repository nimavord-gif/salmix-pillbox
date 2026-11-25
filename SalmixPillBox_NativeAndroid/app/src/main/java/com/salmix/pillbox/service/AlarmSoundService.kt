package com.salmix.pillbox.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.salmix.pillbox.R
import com.salmix.pillbox.ui.AlarmActivity

class AlarmSoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "salmix_alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val EXTRA_MED_NAME = "EXTRA_MED_NAME"
        const val EXTRA_MED_DETAILS = "EXTRA_MED_DETAILS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SalmixPillBox:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val medName = intent?.getStringExtra(EXTRA_MED_NAME) ?: "داروی شما"
        val medDetails = intent?.getStringExtra(EXTRA_MED_DETAILS) ?: "زمان مصرف دارو رسیده است"

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_MED_DETAILS, medDetails)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.salmix_logo)
            .setContentTitle("وقت مصرف داروی: $medName")
            .setContentText(medDetails)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startAudioPlayback()
        return START_STICKY
    }

    private fun startAudioPlayback() {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.salmix_alarm).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_description)
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
