package com.yujif.thinklet.realtimecompanion.camera

import android.Manifest
import android.content.Context
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import com.yujif.thinklet.realtimecompanion.session.DEFAULT_MAX_CAMERA_BYTES

class CameraSessionRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val outputFileProvider: () -> File,
    private val analyzer: ImageAnalysis.Analyzer? = null,
    private val maxFileBytes: Long = DEFAULT_MAX_CAMERA_BYTES,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val recordExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var outputFile: File? = null
    private val recordingFailed = AtomicBoolean(false)

    // ProcessCameraProvider binding is asynchronous; stop() can run before the listener
    // below fires (e.g. a fast start-then-stop, or rollback after a sibling resource
    // fails to start). Without this flag the listener would bind the camera and start
    // recording after stop() already returned, leaking a live recording.
    private val stopped = AtomicBoolean(false)

    val currentOutputBytes: Long
        get() = outputFile?.length() ?: 0L

    val hasRecordingFailed: Boolean
        get() = recordingFailed.get()

    @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
    fun start() {
        if (recording != null || stopped.get()) return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (stopped.get()) return@addListener
            val provider = providerFuture.get()
            val recorder = Recorder.Builder()
                .setExecutor(recordExecutor)
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            val imageAnalysis = analyzer?.let { frameAnalyzer ->
                ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    FRAME_ANALYSIS_SIZE,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }
            }
            val outputFile = outputFileProvider()
            outputFile.parentFile?.mkdirs()
            this.outputFile = outputFile
            val useCases = listOfNotNull(videoCapture, imageAnalysis).toTypedArray()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
            cameraProvider = provider
            val outputOptions = FileOutputOptions.Builder(outputFile)
                .setFileSizeLimit(maxFileBytes)
                .build()
            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(recordExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (event.hasError()) {
                            Log.w(TAG, "Camera recording finalized with error ${event.error}")
                            recordingFailed.set(true)
                        }
                        // The recorder and encoder callbacks keep submitting work to
                        // recordExecutor until Finalize; only now is shutdown safe. An
                        // earlier shutdown rejects the muxer's finalize tasks, leaving
                        // camera.mp4 without a moov atom and crashing the main thread.
                        recordExecutor.shutdown()
                    }
                }
        }, mainExecutor)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainExecutor.execute { stopOnMainThread() }
            return
        }
        stopOnMainThread()
    }

    private fun stopOnMainThread() {
        val activeRecording = recording
        recording = null
        try {
            activeRecording?.stop()
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Recorder executor already shut down before stop", e)
        }
        cameraProvider?.unbindAll()
        cameraProvider = null
        // recordExecutor is shut down in the Finalize event handler once the muxer has
        // finished writing camera.mp4. Shut it down here only when no recording started.
        if (activeRecording == null) {
            recordExecutor.shutdown()
        }
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "CameraSessionRecorder"

        // Frames handed to the analyzer are JPEG-encoded as-is and sent to the Realtime
        // API, so this size bounds image token cost per frame. 640x480 is what CameraX
        // picked on THINKLET by default and what all recorded sessions used; pin it so
        // the app does not silently send larger frames if the CameraX default or the
        // device changes. Prefer a smaller supported size over a larger one on fallback
        // so the cost bound holds.
        val FRAME_ANALYSIS_SIZE = Size(640, 480)
    }
}
