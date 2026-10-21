package com.animationapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * 2D Lip-Sync Phoneme Mouth Chart & Real-Time Audio Scrubbing Synthesizer (Phase 9).
 *
 * In 2D animation (Preston Blair / Toon Boom / Adobe Animate standard), animators match
 * character mouth drawings to 8 core Phoneme shapes (`A/I`, `E`, `O`, `U`, `M/B/P`, `F/V`, `L/Th`, `Rest`).
 *
 * This engine:
 * 1. Stores per-frame Lip-Sync Phoneme Mouth Markers and Audio Waveform peak amplitudes.
 * 2. Synthesizes ultra-low-latency formant audio slices on scrub/playback using Android's native
 *    `AudioTrack` (`MODE_STATIC` / `MODE_STREAM`) so animators can hear beat/phoneme timing frame-by-frame
 *    with zero external audio library bloat!
 */
enum class LipSyncPhoneme(
    val code: String,
    val description: String,
    val formantFreqHz: Double,
    val defaultPeakAmplitude: Float
) {
    NONE("", "Silent / No Marker", 0.0, 0.05f),
    A_I("A / I", "Wide Open Mouth (Ah, Eye)", 730.0, 0.92f),
    E("E", "Stretched Smile Teeth (Eh, Ee)", 530.0, 0.78f),
    O("O", "Round Open Circle (Oh)", 500.0, 0.88f),
    U("U / W", "Small Tight Puckered (Oo, W)", 320.0, 0.65f),
    M_B_P("M / B / P", "Closed Pressed Lips (Mmm, B, P)", 220.0, 0.45f),
    F_V("F / V", "Top Teeth on Lower Lip (Fff, V)", 620.0, 0.55f),
    L_TH("L / D / T", "Tongue Behind Top Teeth (La, Th)", 580.0, 0.70f),
    BEAT_CLICK("🥁 Beat", "Action / Music Accent Beat", 880.0, 1.0f)
}

object LipSyncAudioEngine {

    private const val SAMPLE_RATE = 22050
    var isAudioScrubEnabled: Boolean = true

    /**
     * Plays a crisp 45ms audio scrub slice corresponding to the frame's phoneme or beat marker
     * so the animator hears timing feedback when scrubbing or playing the timeline.
     */
    fun playFrameScrubSlice(phoneme: LipSyncPhoneme) {
        if (!isAudioScrubEnabled || phoneme == LipSyncPhoneme.NONE) return

        val durationMs = 45
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcmBuffer = ShortArray(numSamples)
        val freq = phoneme.formantFreqHz

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Smooth Hann window envelope to prevent speaker clicks
            val envelope = 0.5 * (1.0 - kotlin.math.cos((2.0 * PI * i) / (numSamples - 1)))
            val wave = (sin(2.0 * PI * freq * t) + 0.35 * sin(4.0 * PI * freq * t)) * 0.65
            pcmBuffer[i] = (wave * envelope * phoneme.defaultPeakAmplitude * Short.MAX_VALUE).toInt().toShort()
        }

        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcmBuffer.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmBuffer, 0, pcmBuffer.size)
            track.play()
        } catch (_: Throwable) {
            // Ignore if device audio output is muted/unavailable
        }
    }
}

