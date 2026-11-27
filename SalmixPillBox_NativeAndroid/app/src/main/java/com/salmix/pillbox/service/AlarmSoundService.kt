package com.salmix.pillbox.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.salmix.pillbox.R
import com.salmix.pillbox.ui.AlarmActivity

class AlarmSoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val activeMeds = mutableListOf<String>()
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        stopAlarm()
        stopSelf()
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Respect active phone calls or higher-priority audio
                try {
                    if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                    if (mediaPlayer != null && mediaPlayer?.isPlaying == false) mediaPlayer?.start()
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "salmix_alarm_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val EXTRA_MED_NAME = "EXTRA_MED_NAME"
        const val EXTRA_MED_DETAILS = "EXTRA_MED_DETAILS"
        private const val AUTO_TIMEOUT_MS = 90_000L // 90 seconds max ring
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SalmixPillBox:AlarmWakeLock"
        )
        wakeLock?.acquire(2 * 60 * 1000L) // Max 2 mins
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val medId = intent?.getLongExtra("MED_ID", -1L) ?: -1L
        val alarmSlot = intent?.getStringExtra("ALARM_SLOT") ?: "morning"
        val medName = intent?.getStringExtra(EXTRA_MED_NAME) ?: "داروی شما"
        val medDetails = intent?.getStringExtra(EXTRA_MED_DETAILS) ?: "زمان مصرف دارو رسیده است"

        if (!activeMeds.contains(medName)) {
            activeMeds.add(medName)
        }

        val combinedNames = activeMeds.joinToString("، ")

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("MED_ID", medId)
            putExtra("ALARM_SLOT", alarmSlot)
            putExtra(EXTRA_MED_NAME, combinedNames)
            putExtra(EXTRA_MED_DETAILS, medDetails)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 99, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("وقت مصرف دارو: $combinedNames")
            .setContentText(medDetails)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$combinedNames\n$medDetails"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف زنگ", stopPendingIntent)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

        startAudioPlayback()
        startVibration()

        // Reset 90-second auto-timeout
        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, AUTO_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    private fun startAudioPlayback() {
        if (mediaPlayer != null) return

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAttrs)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = req
            audioManager?.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        if (!focusGranted) {
            // Do not blast audio if phone call or higher priority audio holds focus
            return
        }

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.salmix_alarm)
        } catch (e: Exception) {
            mediaPlayer = null
        }

        // Fallback to system alarm ringtone if custom raw file fails
        if (mediaPlayer == null) {
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, fallbackUri)
                    setAudioAttributes(alarmAttrs)
                    prepare()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mediaPlayer = null
            }
        }

        try {
            mediaPlayer?.apply {
                setAudioAttributes(alarmAttrs)
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 600, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        activeMeds.clear()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusListener)
        }

        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (_: Exception) {}
        }
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
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
