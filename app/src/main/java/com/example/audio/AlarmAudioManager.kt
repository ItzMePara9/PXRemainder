package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated alarm audio and vibration engine.
 * Runs independently of UI lifecycles inside AlarmService (and supports short previews in UI).
 */
class AlarmAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var rampJob: Job? = null
    private var previewJob: Job? = null
    private var isAlarmPlaying = false
    private var targetVolume = 0.85f

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(targetVolume * 0.35f, targetVolume * 0.35f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(targetVolume, targetVolume)
                if (isAlarmPlaying && mediaPlayer?.isPlaying == false) {
                    runCatching { mediaPlayer?.start() }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Keep alarm priority; lower temporarily instead of killing alarm permanently
                mediaPlayer?.setVolume(targetVolume * 0.2f, targetVolume * 0.2f)
            }
        }
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun startAlarm(
        soundUri: String,
        volume: Float,
        gradualIncrease: Boolean,
        vibrationEnabled: Boolean,
        playSound: Boolean,
        scope: CoroutineScope
    ) {
        stopAll()
        targetVolume = volume.coerceIn(0.1f, 1.0f)

        if (playSound) {
            requestAlarmAudioFocus()
            val player = createPreparedPlayer(soundUri)
            if (player != null) {
                mediaPlayer = player
                isAlarmPlaying = true
                val initialVol = if (gradualIncrease) (targetVolume * 0.15f).coerceAtLeast(0.08f) else targetVolume
                player.setVolume(initialVol, initialVol)
                player.isLooping = true
                player.start()

                if (gradualIncrease) {
                    rampJob = scope.launch {
                        val steps = 20
                        val stepDelayMs = 1000L
                        for (step in 1..steps) {
                            if (!isActive || !isAlarmPlaying) break
                            val fraction = step / steps.toFloat()
                            val v = initialVol + (targetVolume - initialVol) * fraction
                            mediaPlayer?.setVolume(v, v)
                            delay(stepDelayMs)
                        }
                    }
                }
            }
        }

        if (vibrationEnabled) {
            startVibrationLoop()
        }
    }

    fun startPreview(
        soundUri: String,
        volume: Float,
        scope: CoroutineScope,
        onFinished: () -> Unit = {}
    ) {
        stopAll()
        targetVolume = volume.coerceIn(0.1f, 1.0f)
        requestAlarmAudioFocus()
        val player = createPreparedPlayer(soundUri) ?: run {
            onFinished()
            return
        }
        mediaPlayer = player
        player.setVolume(targetVolume, targetVolume)
        player.isLooping = true
        player.start()

        previewJob = scope.launch {
            delay(5_000L)
            stopAll()
            onFinished()
        }
    }

    fun stopAll() {
        isAlarmPlaying = false
        rampJob?.cancel()
        rampJob = null
        previewJob?.cancel()
        previewJob = null

        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        abandonAlarmAudioFocus()
        stopVibration()
    }

    private fun createPreparedPlayer(soundUri: String): MediaPlayer? {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val rawId = SoundCatalog.resolveRawResId(soundUri)
        if (rawId != null) {
            return createFromRaw(rawId, attrs)
        }

        return try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, Uri.parse(soundUri))
                prepare()
            }
        } catch (e: Exception) {
            Log.w("AlarmAudioManager", "Custom ringtone unavailable ($soundUri), falling back to classic_alarm", e)
            createFromRaw(R.raw.classic_alarm, attrs)
        }
    }

    private fun createFromRaw(rawResId: Int, attrs: AudioAttributes): MediaPlayer? {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId) ?: return null
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
        } catch (e: Exception) {
            Log.e("AlarmAudioManager", "Failed to create MediaPlayer from raw resource", e)
            null
        }
    }

    private fun startVibrationLoop() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val pattern = longArrayOf(0L, 650L, 450L, 650L, 900L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
    }

    private fun requestAlarmAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAlarmAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }
}
