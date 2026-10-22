package com.animationapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.animationapp.core.diagnostics.CrashReporter
import java.io.File

/**
 * Native Zero-Bloat In-App Microphone Voice Recorder (Phase 29).
 *
 * Records character dialogue or Foley directly from the device microphone into a standard AAC/M4A file
 * with zero third-party library dependencies.
 *
 * Features:
 * - Real-time acoustic VU volume level sampling via [MediaRecorder.getMaxAmplitude].
 * - Precision duration tracking for frames-to-time conversion.
 * - Produces an AAC/M4A file that feeds directly into [ExternalAudioImportEngine] for frame-accurate
 *   waveform generation and Preston Blair lip-sync phoneme mapping.
 */
object VoiceRecorderEngine {

    private var activeRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    var isRecording: Boolean = false
        private set

    var recordingStartTimeMs: Long = 0L
        private set

    /**
     * Starts microphone recording, saving output to `project_mic_recording.m4a` inside internal storage.
     *
     * @return true if recording successfully started, false otherwise.
     */
    fun startRecording(context: Context): Boolean {
        if (isRecording) {
            cancelRecording()
        }

        return try {
            val destFile = File(context.filesDir, "project_mic_recording.m4a")
            if (destFile.exists()) {
                destFile.delete()
            }
            currentRecordingFile = destFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(destFile.absolutePath)
                prepare()
                start()
            }

            activeRecorder = recorder
            isRecording = true
            recordingStartTimeMs = SystemClock.uptimeMillis()
            CrashReporter.logInfo("VoiceRecorder", "Started microphone recording -> ${destFile.name}")
            true
        } catch (t: Throwable) {
            CrashReporter.logInfo("VoiceRecorder", "Failed to start recording: ${t.message}")
            cancelRecording()
            false
        }
    }

    /**
     * Returns the elapsed recording time in milliseconds.
     */
    fun getElapsedRecordingMs(): Long {
        if (!isRecording) return 0L
        return SystemClock.uptimeMillis() - recordingStartTimeMs
    }

    /**
     * Samples the instantaneous peak microphone volume, returning a normalized 0.0f..1.0f amplitude.
     */
    fun getNormalizedPeakAmplitude(): Float {
        val recorder = activeRecorder ?: return 0f
        return try {
            val raw = recorder.maxAmplitude
            (raw / 32768f).coerceIn(0f, 1f)
        } catch (_: Throwable) {
            0f
        }
    }

    /**
     * Stops the active recording and returns the finalized audio file.
     * Returns null if no active recording or if recording failed.
     */
    fun stopRecording(): File? {
        if (!isRecording) return null
        val recorder = activeRecorder ?: return null

        return try {
            recorder.stop()
            recorder.release()
            activeRecorder = null
            isRecording = false

            val file = currentRecordingFile
            if (file != null && file.exists() && file.length() > 0) {
                CrashReporter.logInfo("VoiceRecorder", "Recording finished: ${file.length()} bytes")
                file
            } else {
                null
            }
        } catch (t: Throwable) {
            CrashReporter.logInfo("VoiceRecorder", "Error stopping recording: ${t.message}")
            cancelRecording()
            null
        }
    }

    /**
     * Cancels any in-progress recording and discards temporary files.
     */
    fun cancelRecording() {
        try {
            activeRecorder?.stop()
        } catch (_: Throwable) {}
        try {
            activeRecorder?.release()
        } catch (_: Throwable) {}
        activeRecorder = null
        isRecording = false
        currentRecordingFile?.let { if (it.exists()) it.delete() }
        currentRecordingFile = null
    }
}
