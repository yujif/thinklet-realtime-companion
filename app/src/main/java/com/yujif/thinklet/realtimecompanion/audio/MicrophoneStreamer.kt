package com.yujif.thinklet.realtimecompanion.audio

import com.yujif.thinklet.realtimecompanion.session.SessionRecorder
import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MicrophoneStreamer(
    private val recorderProvider: () -> SessionRecorder?,
    private val onAudioChunk: (ByteArray) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var audioRecord: AudioRecord? = null
    private var recordingTask: Future<*>? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        audioRecord = record
        record.startRecording()
        recordingTask = executor.submit {
            val buffer = ByteArray(bufferSize)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    recorderProvider()?.appendMicPcm(chunk)
                    onAudioChunk(chunk)
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        // record.stop() unblocks a pending read() on the executor thread; release() must
        // wait for that thread to actually exit read() first, or it can crash/corrupt state.
        audioRecord?.runCatching { stop() }
        recordingTask?.runCatching { get(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        audioRecord?.runCatching { release() }
        audioRecord = null
        recordingTask = null
        executor.shutdown()
    }

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val STOP_TIMEOUT_MS = 500L
    }
}
