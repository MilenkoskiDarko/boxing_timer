package com.milenkoski.boxingtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "boxing_timer_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.milenkoski.boxingtimer.action.START"
        const val ACTION_PAUSE = "com.milenkoski.boxingtimer.action.PAUSE"
        const val ACTION_RESUME = "com.milenkoski.boxingtimer.action.RESUME"
        const val ACTION_STOP = "com.milenkoski.boxingtimer.action.STOP"
        const val ACTION_ADJUST_TIME = "com.milenkoski.boxingtimer.action.ADJUST_TIME"

        const val EXTRA_DELTA_SECONDS = "extra_delta_seconds"

        const val EXTRA_ROUNDS = "extra_rounds"
        const val EXTRA_ROUND_SECONDS = "extra_round_seconds"
        const val EXTRA_REST_SECONDS = "extra_rest_seconds"

        const val BROADCAST_TICK = "com.milenkoski.boxingtimer.broadcast.TICK"
        const val EXTRA_SECONDS_LEFT = "extra_seconds_left"
        const val EXTRA_PHASE = "extra_phase"
        const val EXTRA_CURRENT_ROUND = "extra_current_round"
        const val EXTRA_TOTAL_ROUNDS = "extra_total_rounds"

        const val BROADCAST_FINISHED = "com.milenkoski.boxingtimer.broadcast.FINISHED"
    }

    private var soundPool: SoundPool? = null
    private var soundBell = 0
    private var soundRest = 0
    private var soundFinish = 0
    private var soundWarning = 0
    private var warningStreamId = 0
    private var soundPrepare = 0
    private var hasPlayedWarningThisPhase = false

    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var totalRounds = 0
    private var roundSeconds = 0
    private var restSeconds = 0

    private var currentRound = 1
    private var phase = TimerPhase.IDLE
    private var millisRemainingInPhase = 0L
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupSoundPool()
    }

    private fun setupSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundBell = soundPool!!.load(this, R.raw.bell, 1)
        soundRest = soundPool!!.load(this, R.raw.bell, 1)
        soundFinish = soundPool!!.load(this, R.raw.bell, 1)
        soundWarning = soundPool!!.load(this, R.raw.five_seconds, 1)
//        soundPrepare = soundPool!!.load(this, R.raw.get_ready, 1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                totalRounds = intent.getIntExtra(EXTRA_ROUNDS, 1)
                roundSeconds = intent.getIntExtra(EXTRA_ROUND_SECONDS, 180)
                restSeconds = intent.getIntExtra(EXTRA_REST_SECONDS, 60)
                currentRound = 1
                startForeground(NOTIFICATION_ID, buildNotification("Get ready\u2026"))
                acquireWakeLock()
                startPhase(TimerPhase.PREPARE, 3000L)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopEverything()
            ACTION_ADJUST_TIME -> adjustTime(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
        }
        return START_NOT_STICKY
    }

    private fun startPhase(newPhase: TimerPhase, durationMillis: Long) {
        phase = newPhase
        isPaused = false
        // Don't re-trigger the warning beep if we're resuming mid-phase past the 5s mark already passed.
        hasPlayedWarningThisPhase = durationMillis <= 5000L

        soundPool?.stop(warningStreamId)
        playPhaseSound(newPhase)
        vibrate()
        runCountdown(durationMillis)
    }

    private fun runCountdown(durationMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                millisRemainingInPhase = millisUntilFinished
                if (!hasPlayedWarningThisPhase && millisUntilFinished in 1..5000L) {
                    hasPlayedWarningThisPhase = true
                    warningStreamId = soundPool?.play(soundWarning, 1f, 1f, 1, 0, 1f) ?: 0
                }
                broadcastTick()
            }

            override fun onFinish() {
                millisRemainingInPhase = 0
                broadcastTick()
                advancePhase()
            }
        }.start()
    }

    private fun adjustTime(deltaSeconds: Int) {
        if (phase == TimerPhase.IDLE || phase == TimerPhase.FINISHED) return
        val newMillis = (millisRemainingInPhase + deltaSeconds * 1000L).coerceAtLeast(0L)
        millisRemainingInPhase = newMillis
        // Re-arm the 5s warning if the new time is back above the threshold; suppress it
        // if we've landed inside the warning window so it doesn't fire again immediately.
        val stillInWarningWindow = newMillis <= 5000L
        if (!stillInWarningWindow && hasPlayedWarningThisPhase) {

            soundPool?.stop(warningStreamId)
        }
        hasPlayedWarningThisPhase = stillInWarningWindow

        if (newMillis == 0L) {
            broadcastTick()
            if (!isPaused) advancePhase()
            return
        }

        broadcastTick()
        if (!isPaused) {
            runCountdown(newMillis)
        }
    }

    private fun advancePhase() {
        when (phase) {
            TimerPhase.PREPARE -> {
                updateNotification("Round 1/$totalRounds")
                startPhase(TimerPhase.ROUND, roundSeconds * 1000L)
            }
            TimerPhase.ROUND -> {
                if (currentRound >= totalRounds) {
                    finishWorkout()
                } else {
                    updateNotification("Rest — before round ${currentRound + 1}/$totalRounds")
                    startPhase(TimerPhase.REST, restSeconds * 1000L)
                }
            }
            TimerPhase.REST -> {
                currentRound += 1
                updateNotification("Round $currentRound/$totalRounds")
                startPhase(TimerPhase.ROUND, roundSeconds * 1000L)
            }
            else -> { /* no-op */ }
        }
    }

    private fun finishWorkout() {
        phase = TimerPhase.FINISHED
        soundPool?.stop(warningStreamId)   // To stop the 5-seconds sound after workot ends
        soundPool?.play(soundFinish, 1f, 1f, 1, 0, 1f)
        val intent = Intent(BROADCAST_FINISHED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        // Don't stop immediately: play() is async, and stopEverything() releases the
        // SoundPool — stopping right away can cut the sound off before it's heard.
        android.os.Handler(mainLooper).postDelayed({ stopEverything() }, 1500L)
    }

    private fun pauseTimer() {
        if (phase == TimerPhase.IDLE || phase == TimerPhase.FINISHED) return
        countDownTimer?.cancel()
        soundPool?.stop(warningStreamId)
        isPaused = true
    }

    private fun resumeTimer() {
        if (!isPaused) return
        isPaused = false
        if (millisRemainingInPhase in 1..5000L) {
            warningStreamId = soundPool?.play(soundWarning, 1f, 1f, 1, 0, 1f) ?: 0
        }
        runCountdown(millisRemainingInPhase)
    }

    private fun stopEverything() {
        countDownTimer?.cancel()
        releaseWakeLock()
        phase = TimerPhase.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playPhaseSound(phase: TimerPhase) {
        when (phase) {
            TimerPhase.PREPARE -> soundPool?.play(soundPrepare, 1f, 1f, 1, 0, 1f)
            TimerPhase.ROUND -> soundPool?.play(soundBell, 1f, 1f, 1, 0, 1f)
            TimerPhase.REST -> soundPool?.play(soundRest, 1f, 1f, 1, 0, 1f)
            else -> {}
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    private fun broadcastTick() {
        val intent = Intent(BROADCAST_TICK).apply {
            putExtra(EXTRA_SECONDS_LEFT, (millisRemainingInPhase / 1000L).toInt())
            putExtra(EXTRA_PHASE, phase.name)
            putExtra(EXTRA_CURRENT_ROUND, currentRound)
            putExtra(EXTRA_TOTAL_ROUNDS, totalRounds)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoxingTimer::TimerWakeLock")
        wakeLock?.acquire(2 * 60 * 60 * 1000L) // safety cap: 2 hours max
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        soundPool?.release()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
