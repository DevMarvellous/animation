package com.animationapp.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.model.AnimationProject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Real External Audio File Import, Waveform Extractor & Synchronized Playback Engine (Phase 26).
 *
 * Capabilities:
 * - Uses native Android [MediaExtractor] + [MediaCodec] to decode any audio file (.mp3, .wav, .m4a, .aac, .ogg)
 *   into frame-accurate acoustic amplitude envelopes without third-party FFmpeg bloat.
 * - Downsamples audio track into a tiny float array (1 float per frame = ~800 bytes for 200 frames)
 *   so 2GB RAM budget phones never experience memory pressure.
 * - Plugs directly into [AnimationFrame.audioAmplitude] so timeline pills display true voice waveforms (`▁▂▃▅▇`).
 * - Maps acoustic voice peaks to Preston Blair 2D Lip-Sync mouth phonemes automatically.
 * - Synchronizes real-time [MediaPlayer] playback with the canvas animation clock and timeline scrubber.
 */
object ExternalAudioImportEngine {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeMediaPlayer: MediaPlayer? = null
    var cachedAudioFile: File? = null
        private set
    var voiceDurationMs: Long = 0L
        private set
    var voiceVolume: Float = 1.0f
        private set
    var isVoiceMuted: Boolean = false
        private set

    // Track 2: Background Music (BGM) & Foley Sound Effects (SFX)
    private var bgmMediaPlayer: MediaPlayer? = null
    var cachedBgmFile: File? = null
        private set
    var bgmDurationMs: Long = 0L
        private set
    var bgmVolume: Float = 0.8f
        private set
    var isBgmMuted: Boolean = false
        private set

    val audioDurationMs: Long
        get() = maxOf(voiceDurationMs, bgmDurationMs)

    fun hasVoiceTrack(): Boolean = cachedAudioFile != null && cachedAudioFile!!.exists()

    fun hasBgmTrack(): Boolean = cachedBgmFile != null && cachedBgmFile!!.exists()

    fun hasAudio(): Boolean = hasVoiceTrack() || hasBgmTrack()

    /**
     * Imports an external audio file URI, copies it to local storage, decodes its PCM samples,
     * downsamples to per-frame amplitudes, and updates [project] frames.
     */
    fun importAudioFileAsync(
        context: Context,
        audioUri: Uri,
        project: AnimationProject,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                mainHandler.post { onProgress("Caching audio file...") }

                // 1. Copy audio file into app internal storage
                val destFile = File(appContext.filesDir, "project_audio_track.mp3")
                appContext.contentResolver.openInputStream(audioUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open selected audio file")

                cachedAudioFile = destFile
                processAndDecodeAudioFile(destFile, project, onProgress, onComplete)

            } catch (t: Throwable) {
                CrashReporter.logInfo("AudioEngine", "Audio import failed: ${t.message}")
                mainHandler.post {
                    onComplete(false, t.message ?: "Failed to decode audio track")
                }
            }
        }
    }

    /**
     * Imports a direct audio [File] (e.g. from [VoiceRecorderEngine] microphone recording),
     * decodes its PCM samples, downsamples to per-frame amplitudes, and updates [project] frames.
     */
    fun importAudioFileDirectAsync(
        context: Context,
        audioSourceFile: File,
        project: AnimationProject,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                mainHandler.post { onProgress("Processing recorded audio track...") }

                val destFile = File(appContext.filesDir, "project_audio_track.m4a")
                if (audioSourceFile.absolutePath != destFile.absolutePath) {
                    audioSourceFile.copyTo(destFile, overwrite = true)
                }

                cachedAudioFile = destFile
                processAndDecodeAudioFile(destFile, project, onProgress, onComplete)

            } catch (t: Throwable) {
                CrashReporter.logInfo("AudioEngine", "Direct audio import failed: ${t.message}")
                mainHandler.post {
                    onComplete(false, t.message ?: "Failed to process audio file")
                }
            }
        }
    }

    private fun processAndDecodeAudioFile(
        destFile: File,
        project: AnimationProject,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        // 2. Extract Duration
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(destFile.absolutePath)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L
        voiceDurationMs = durationMs
        retriever.release()

        mainHandler.post { onProgress("Decoding acoustic waveform...") }

        // 3. Decode Audio into per-frame peak amplitudes via MediaExtractor & MediaCodec
        val amplitudes = decodeAudioEnvelope(destFile, project.fps, project.frames.size)

        // 4. Update Project Frames
        val frames = project.frames
        val count = minOf(frames.size, amplitudes.size)
        for (i in 0 until count) {
            val amp = amplitudes[i].coerceIn(0f, 1f)
            frames[i].audioAmplitude = amp

            // Auto-assign Preston Blair phoneme mouth markers based on acoustic volume
            frames[i].phonemeMarker = when {
                amp < 0.08f -> LipSyncPhoneme.NONE
                amp < 0.22f -> LipSyncPhoneme.M_B_P
                amp < 0.45f -> if (i % 2 == 0) LipSyncPhoneme.F_V else LipSyncPhoneme.L_TH
                amp < 0.65f -> if (i % 2 == 0) LipSyncPhoneme.E else LipSyncPhoneme.U
                amp < 0.85f -> LipSyncPhoneme.O
                else -> LipSyncPhoneme.A_I
            }
        }

        // Initialize Voice Track MediaPlayer
        prepareVoicePlayer(destFile)

        val durSeconds = (durationMs / 1000f)
        CrashReporter.logInfo("AudioEngine", "Decoded ${amplitudes.size} frame envelope (${durSeconds}s)")
        mainHandler.post {
            onComplete(true, "Loaded voice track: ${durSeconds}s synced to ${project.fps} FPS")
        }
    }

    private fun decodeAudioEnvelope(file: File, fps: Int, frameCount: Int): FloatArray {
        val amplitudes = FloatArray(frameCount) { 0f }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                return amplitudes
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isEos = false
            val frameDurationUs = (1_000_000L / fps).coerceAtLeast(10_000L)

            val shortBuffer = ShortArray(4096)

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inIndex)
                    if (inputBuf != null) {
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(info, 5000)
                while (outIndex >= 0) {
                    val outputBuf = codec.getOutputBuffer(outIndex)
                    if (outputBuf != null && info.size > 0) {
                        outputBuf.position(info.offset)
                        outputBuf.limit(info.offset + info.size)
                        outputBuf.order(ByteOrder.LITTLE_ENDIAN)

                        // Sample peak PCM amplitude
                        val shortsAvailable = info.size / 2
                        var maxSample = 0
                        var count = 0
                        while (outputBuf.remaining() >= 2 && count < shortBuffer.size) {
                            val s = abs(outputBuf.getShort().toInt())
                            if (s > maxSample) maxSample = s
                            count++
                        }

                        val normalizedAmp = (maxSample / 32768f).coerceIn(0f, 1f)
                        val frameIdx = (info.presentationTimeUs / frameDurationUs).toInt()
                        if (frameIdx in 0 until frameCount) {
                            amplitudes[frameIdx] = max(amplitudes[frameIdx], normalizedAmp)
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(info, 0)
                }
            }

        } catch (t: Throwable) {
            CrashReporter.logInfo("AudioEngine", "Decoding loop warning: ${t.message}")
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }

        return amplitudes
    }

    private fun prepareVoicePlayer(file: File) {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Throwable) {}
        activeMediaPlayer = null

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                val vol = if (isVoiceMuted) 0f else voiceVolume
                setVolume(vol, vol)
            }
            activeMediaPlayer = player
        } catch (t: Throwable) {
            CrashReporter.logInfo("AudioEngine", "Voice MediaPlayer init error: ${t.message}")
        }
    }

    /**
     * Imports Track 2 (Background Music / Foley SFX) from a URI.
     */
    fun importBgmAudioFileAsync(
        context: Context,
        audioUri: Uri,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                mainHandler.post { onProgress("Caching background music / SFX...") }

                val destFile = File(appContext.filesDir, "project_bgm_track.mp3")
                appContext.contentResolver.openInputStream(audioUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open selected BGM audio file")

                cachedBgmFile = destFile

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(destFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                bgmDurationMs = durationMs
                retriever.release()

                prepareBgmPlayer(destFile)

                val durSeconds = (durationMs / 1000f)
                CrashReporter.logInfo("AudioEngine", "Loaded Track 2 BGM: ${destFile.name} (${durSeconds}s)")
                mainHandler.post {
                    onComplete(true, "Loaded Track 2 BGM: ${String.format("%.1f", durSeconds)}s")
                }

            } catch (t: Throwable) {
                CrashReporter.logInfo("AudioEngine", "BGM import failed: ${t.message}")
                mainHandler.post {
                    onComplete(false, t.message ?: "Failed to import background music")
                }
            }
        }
    }

    private fun prepareBgmPlayer(file: File) {
        try {
            bgmMediaPlayer?.stop()
            bgmMediaPlayer?.release()
        } catch (_: Throwable) {}
        bgmMediaPlayer = null

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                val vol = if (isBgmMuted) 0f else bgmVolume
                setVolume(vol, vol)
            }
            bgmMediaPlayer = player
        } catch (t: Throwable) {
            CrashReporter.logInfo("AudioEngine", "BGM MediaPlayer init error: ${t.message}")
        }
    }

    fun startPlayback(startFrame: Int, fps: Int) {
        val targetMs = (startFrame * 1000L / fps).toInt()
        activeMediaPlayer?.let { player ->
            try {
                val vol = if (isVoiceMuted) 0f else voiceVolume
                player.setVolume(vol, vol)
                player.seekTo(targetMs)
                player.start()
            } catch (_: Throwable) {}
        }
        bgmMediaPlayer?.let { player ->
            try {
                val vol = if (isBgmMuted) 0f else bgmVolume
                player.setVolume(vol, vol)
                player.seekTo(targetMs)
                player.start()
            } catch (_: Throwable) {}
        }
    }

    fun pausePlayback() {
        try {
            if (activeMediaPlayer?.isPlaying == true) activeMediaPlayer?.pause()
        } catch (_: Throwable) {}
        try {
            if (bgmMediaPlayer?.isPlaying == true) bgmMediaPlayer?.pause()
        } catch (_: Throwable) {}
    }

    fun scrubToFrame(frameIndex: Int, fps: Int) {
        val targetMs = (frameIndex * 1000L / fps).toInt()
        val p1 = activeMediaPlayer
        val p2 = bgmMediaPlayer
        try {
            p1?.seekTo(targetMs)
            p2?.seekTo(targetMs)
            p1?.start()
            p2?.start()
            mainHandler.postDelayed({
                try {
                    if (p1?.isPlaying == true) p1.pause()
                    if (p2?.isPlaying == true) p2.pause()
                } catch (_: Throwable) {}
            }, 80L)
        } catch (_: Throwable) {}
    }

    fun setVoiceVolume(vol: Float) {
        voiceVolume = vol.coerceIn(0f, 1f)
        val effective = if (isVoiceMuted) 0f else voiceVolume
        try { activeMediaPlayer?.setVolume(effective, effective) } catch (_: Throwable) {}
    }

    fun setBgmVolume(vol: Float) {
        bgmVolume = vol.coerceIn(0f, 1f)
        val effective = if (isBgmMuted) 0f else bgmVolume
        try { bgmMediaPlayer?.setVolume(effective, effective) } catch (_: Throwable) {}
    }

    fun toggleVoiceMute(): Boolean {
        isVoiceMuted = !isVoiceMuted
        val effective = if (isVoiceMuted) 0f else voiceVolume
        try { activeMediaPlayer?.setVolume(effective, effective) } catch (_: Throwable) {}
        return isVoiceMuted
    }

    fun toggleBgmMute(): Boolean {
        isBgmMuted = !isBgmMuted
        val effective = if (isBgmMuted) 0f else bgmVolume
        try { bgmMediaPlayer?.setVolume(effective, effective) } catch (_: Throwable) {}
        return isBgmMuted
    }

    fun removeVoiceTrack(project: AnimationProject) {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Throwable) {}
        activeMediaPlayer = null
        cachedAudioFile?.let { if (it.exists()) it.delete() }
        cachedAudioFile = null
        voiceDurationMs = 0L
        for (f in project.frames) {
            f.audioAmplitude = 0f
        }
    }

    fun removeBgmTrack() {
        try {
            bgmMediaPlayer?.stop()
            bgmMediaPlayer?.release()
        } catch (_: Throwable) {}
        bgmMediaPlayer = null
        cachedBgmFile?.let { if (it.exists()) it.delete() }
        cachedBgmFile = null
        bgmDurationMs = 0L
    }

    fun removeAudio(project: AnimationProject) {
        removeVoiceTrack(project)
    }

    fun release() {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Throwable) {}
        activeMediaPlayer = null

        try {
            bgmMediaPlayer?.stop()
            bgmMediaPlayer?.release()
        } catch (_: Throwable) {}
        bgmMediaPlayer = null
    }
}
