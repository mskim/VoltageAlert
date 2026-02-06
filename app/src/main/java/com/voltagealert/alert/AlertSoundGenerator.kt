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
        println("====== AlertSoundGenerator.start() called ======")
        Log.e(TAG, "====== START CALLED ======")

        if (audioTrack != null) {
            Log.w(TAG, "Sound already playing")
            println("Sound already playing - returning")
            return
        }

        try {
            println("Getting buffer size...")
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            println("Buffer size: $bufferSize")
            Log.e(TAG, "Buffer size: $bufferSize")

            println("Building audio attributes...")
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            println("Creating AudioTrack...")
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

            println("AudioTrack created: ${audioTrack != null}")
            println("AudioTrack state: ${audioTrack?.state}")
            Log.e(TAG, "AudioTrack state: ${audioTrack?.state}")

            println("Starting playback...")
            audioTrack?.play()
            println("Play called. Play state: ${audioTrack?.playState}")
            Log.e(TAG, "Play state: ${audioTrack?.playState}")

            playbackJob = scope.launch {
                try {
                    println("Playback coroutine started")
                    while (isActive) {
                        // Generate and play tone 1 (1200Hz)
                        val tone1 = generateTone(TONE_1_FREQ, TONE_DURATION_MS)
                        val written1 = audioTrack?.write(tone1, 0, tone1.size)
                        println("Wrote tone1: $written1 bytes")

                        // Generate and play tone 2 (800Hz)
                        val tone2 = generateTone(TONE_2_FREQ, TONE_DURATION_MS)
                        val written2 = audioTrack?.write(tone2, 0, tone2.size)
                        println("Wrote tone2: $written2 bytes")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Playback error", e)
                    println("Playback error: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.e(TAG, "====== Alert sound started successfully ======")
            println("====== Alert sound started successfully ======")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alert sound", e)
            println("EXCEPTION: ${e.message}")
            e.printStackTrace()
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
