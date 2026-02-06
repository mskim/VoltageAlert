package com.voltagealert.alert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Generates two-tone siren sound programmatically.
 *
 * Pattern: 1200Hz (500ms) → 800Hz (500ms) → repeat
 * Uses USAGE_ALARM stream to bypass Do Not Disturb.
 */
class AlertSoundGenerator {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val TAG = "AlertSoundGenerator"
        private const val SAMPLE_RATE = 44100
        private const val TONE_1_FREQ = 1200.0  // Hz
        private const val TONE_2_FREQ = 800.0   // Hz
        private const val TONE_DURATION_MS = 500
    }

    /**
     * Start playing the alert sound.
     */
    fun start() {
        if (audioTrack != null) {
            Log.w(TAG, "Sound already playing")
            return
        }

        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioTrack?.play()

            playbackJob = scope.launch {
                try {
                    while (isActive) {
                        // Generate and play tone 1 (1200Hz)
                        val tone1 = generateTone(TONE_1_FREQ, TONE_DURATION_MS)
                        audioTrack?.write(tone1, 0, tone1.size)

                        // Generate and play tone 2 (800Hz)
                        val tone2 = generateTone(TONE_2_FREQ, TONE_DURATION_MS)
                        audioTrack?.write(tone2, 0, tone2.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Playback error", e)
                }
            }

            Log.d(TAG, "Alert sound started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alert sound", e)
            stop()
        }
    }

    /**
     * Stop playing the alert sound.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null

        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null

        Log.d(TAG, "Alert sound stopped")
    }

    /**
     * Check if sound is currently playing.
     */
    fun isPlaying(): Boolean {
        return audioTrack != null && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    /**
     * Generate a pure tone at specified frequency and duration.
     *
     * @param frequencyHz Frequency in Hz
     * @param durationMs Duration in milliseconds
     * @return PCM audio samples
     */
    private fun generateTone(frequencyHz: Double, durationMs: Int): ShortArray {
        val numSamples = (durationMs * SAMPLE_RATE) / 1000
        val samples = ShortArray(numSamples)
        val angularFrequency = 2.0 * Math.PI * frequencyHz / SAMPLE_RATE

        for (i in samples.indices) {
            val sample = (sin(angularFrequency * i) * Short.MAX_VALUE * 0.8).toInt()
            samples[i] = sample.toShort()
        }

        return samples
    }
}
