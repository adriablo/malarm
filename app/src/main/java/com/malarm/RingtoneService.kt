package com.malarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

class RingtoneService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ringing")
            .apply { acquire(RING_MAX_DURATION_MS) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        val alarm = if (id < 0) null else AlarmStore(this).get(id)
        if (alarm == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (player == null) {
            ServiceCompat.startForeground(
                this,
                AlarmNotifier.NOTIFICATION_ID,
                AlarmNotifier.build(this, alarm),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            startRinging(alarm)
            stopHandler = Handler(Looper.getMainLooper())
            stopHandler?.postDelayed({ stopSelf() }, RING_MAX_DURATION_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopHandler?.removeCallbacksAndMessages(null)
        stopHandler = null
        stopRinging()
        AlarmNotifier.cancel(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRinging(alarm: Alarm) {
        if (alarm.ringtone != RINGTONE_SILENT) {
            val uri = alarm.ringtone
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?.takeIf { it != Uri.EMPTY }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setWakeMode(this@RingtoneService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                runCatching {
                    setDataSource(this@RingtoneService, uri)
                    prepare()
                    start()
                }
            }
        }
        vibrate()
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 500, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopRinging() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        runCatching { wakeLock?.release() }
    }

    companion object {
        private const val RING_MAX_DURATION_MS = 5 * 60 * 1000L
        const val RINGTONE_SILENT = "silent://"

        fun intent(context: Context, alarm: Alarm): Intent =
            Intent(context, RingtoneService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
    }
}
