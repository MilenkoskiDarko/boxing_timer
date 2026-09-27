package com.milenkoski.boxingtimer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.milenkoski.boxingtimer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = false
    private var isPaused = false

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                TimerService.BROADCAST_TICK -> {
                    val secondsLeft = intent.getIntExtra(TimerService.EXTRA_SECONDS_LEFT, 0)
                    val phaseName = intent.getStringExtra(TimerService.EXTRA_PHASE) ?: TimerPhase.IDLE.name
                    val currentRound = intent.getIntExtra(TimerService.EXTRA_CURRENT_ROUND, 1)
                    val totalRounds = intent.getIntExtra(TimerService.EXTRA_TOTAL_ROUNDS, 1)
                    updateDisplay(secondsLeft, TimerPhase.valueOf(phaseName), currentRound, totalRounds)
                }
                TimerService.BROADCAST_FINISHED -> {
                    binding.textPhase.text = getString(R.string.phase_finished)
                    binding.textTime.text = "00:00"
                    setRunningState(false)
                    applyPhaseColors(TimerPhase.FINISHED)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        binding.buttonStart.setOnClickListener { onStartClicked() }
        binding.buttonPause.setOnClickListener { onPauseResumeClicked() }
        binding.buttonReset.setOnClickListener { onResetClicked() }
        binding.buttonMinus10.setOnClickListener { adjustTime(-10) }
        binding.buttonPlus10.setOnClickListener { adjustTime(10) }

        binding.buttonRoundsMinus.setOnClickListener { stepField(binding.inputRounds, -1, min = 1) }
        binding.buttonRoundsPlus.setOnClickListener { stepField(binding.inputRounds, 1, min = 1) }
        binding.buttonRoundMinutesMinus.setOnClickListener { stepField(binding.inputRoundMinutes, -1, min = 0) }
        binding.buttonRoundMinutesPlus.setOnClickListener { stepField(binding.inputRoundMinutes, 1, min = 0) }
        binding.buttonRoundSecondsMinus.setOnClickListener { stepField(binding.inputRoundSeconds, -5, min = 0) }
        binding.buttonRoundSecondsPlus.setOnClickListener { stepField(binding.inputRoundSeconds, 5, min = 0) }
        binding.buttonRestMinutesMinus.setOnClickListener { stepField(binding.inputRestMinutes, -1, min = 0) }
        binding.buttonRestMinutesPlus.setOnClickListener { stepField(binding.inputRestMinutes, 1, min = 0) }
        binding.buttonRestSecondsMinus.setOnClickListener { stepField(binding.inputRestSeconds, -5, min = 0) }
        binding.buttonRestSecondsPlus.setOnClickListener { stepField(binding.inputRestSeconds, 5, min = 0) }

        setRunningState(false)
        applyPhaseColors(TimerPhase.IDLE)
    }

    private fun stepField(field: android.widget.EditText, delta: Int, min: Int) {
        val current = field.text.toString().toIntOrNull() ?: min
        val newValue = (current + delta).coerceAtLeast(min)
        field.setText(newValue.toString())
    }

    private fun onStartClicked() {
        val rounds = binding.inputRounds.text.toString().toIntOrNull() ?: 1
        val roundMinutes = binding.inputRoundMinutes.text.toString().toIntOrNull() ?: 0
        val roundSecondsPart = binding.inputRoundSeconds.text.toString().toIntOrNull() ?: 0
        val restMinutes = binding.inputRestMinutes.text.toString().toIntOrNull() ?: 0
        val restSecondsPart = binding.inputRestSeconds.text.toString().toIntOrNull() ?: 0

        val roundSeconds = roundMinutes * 60 + roundSecondsPart
        val restSeconds = restMinutes * 60 + restSecondsPart

        if (rounds < 1 || roundSeconds < 1) return

        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_ROUNDS, rounds)
            putExtra(TimerService.EXTRA_ROUND_SECONDS, roundSeconds)
            putExtra(TimerService.EXTRA_REST_SECONDS, restSeconds)
        }
        ContextCompat.startForegroundService(this, intent)
        setRunningState(true)
    }

    private fun onPauseResumeClicked() {
        val action = if (isPaused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE
        startService(Intent(this, TimerService::class.java).apply { this.action = action })
        isPaused = !isPaused
        binding.buttonPause.text = if (isPaused) getString(R.string.action_resume) else getString(R.string.action_pause)
    }

    private fun onResetClicked() {
        startService(Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        })
        setRunningState(false)
        binding.textTime.text = "00:00"
        binding.textPhase.text = getString(R.string.phase_idle)
        binding.textRoundIndicator.text = ""

        binding.inputRounds.setText("0")
        binding.inputRoundMinutes.setText("")
        binding.inputRoundSeconds.setText("")
        binding.inputRestMinutes.setText("")
        binding.inputRestSeconds.setText("")
        applyPhaseColors(TimerPhase.IDLE)
    }

    private fun adjustTime(deltaSeconds: Int) {
        if (!isRunning) return
        startService(Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_ADJUST_TIME
            putExtra(TimerService.EXTRA_DELTA_SECONDS, deltaSeconds)
        })
    }

    private fun setRunningState(running: Boolean) {
        isRunning = running
        isPaused = false
        binding.buttonPause.text = getString(R.string.action_pause)
        binding.buttonStart.isEnabled = !running
        binding.buttonPause.isEnabled = running
        binding.buttonMinus10.isEnabled = running
        binding.buttonPlus10.isEnabled = running
        binding.inputRounds.isEnabled = !running
        binding.inputRoundMinutes.isEnabled = !running
        binding.inputRoundSeconds.isEnabled = !running
        binding.inputRestMinutes.isEnabled = !running
        binding.inputRestSeconds.isEnabled = !running
        binding.buttonRoundsMinus.isEnabled = !running
        binding.buttonRoundsPlus.isEnabled = !running
        binding.buttonRoundMinutesMinus.isEnabled = !running
        binding.buttonRoundMinutesPlus.isEnabled = !running
        binding.buttonRoundSecondsMinus.isEnabled = !running
        binding.buttonRoundSecondsPlus.isEnabled = !running
        binding.buttonRestMinutesMinus.isEnabled = !running
        binding.buttonRestMinutesPlus.isEnabled = !running
        binding.buttonRestSecondsMinus.isEnabled = !running
        binding.buttonRestSecondsPlus.isEnabled = !running
    }

    private fun updateDisplay(secondsLeft: Int, phase: TimerPhase, currentRound: Int, totalRounds: Int) {
        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60
        binding.textTime.text = String.format("%02d:%02d", minutes, seconds)
        binding.textRoundIndicator.text = if (phase == TimerPhase.PREPARE) "" else
            getString(R.string.round_indicator, currentRound, totalRounds)
        binding.textPhase.text = when (phase) {
            TimerPhase.PREPARE -> getString(R.string.phase_prepare)
            TimerPhase.ROUND -> getString(R.string.phase_round)
            TimerPhase.REST -> getString(R.string.phase_rest)
            TimerPhase.FINISHED -> getString(R.string.phase_finished)
            TimerPhase.IDLE -> getString(R.string.phase_idle)
        }
        applyPhaseColors(phase)
    }

    private fun applyPhaseColors(phase: TimerPhase) {
        val bgColorRes = when (phase) {
            TimerPhase.PREPARE -> R.color.phase_prepare_bg
            TimerPhase.ROUND -> R.color.phase_round_bg
            TimerPhase.REST -> R.color.phase_rest_bg
            TimerPhase.FINISHED -> R.color.phase_finished_bg
            TimerPhase.IDLE -> R.color.phase_idle_bg
        }
        val bgColor = ContextCompat.getColor(this, bgColorRes)
        val textColorRes = if (phase == TimerPhase.IDLE) R.color.phase_text_dark else R.color.phase_text_light
        val textColor = ContextCompat.getColor(this, textColorRes)

        binding.root.setBackgroundColor(bgColor)
        binding.textPhase.setTextColor(textColor)
        binding.textTime.setTextColor(textColor)
        binding.textRoundIndicator.setTextColor(textColor)
        binding.textBrand.setTextColor(textColor)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(TimerService.BROADCAST_TICK)
            addAction(TimerService.BROADCAST_FINISHED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(tickReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tickReceiver)
    }
}
