package com.yujif.thinklet.realtimecompanion.camera

import com.yujif.thinklet.realtimecompanion.session.SessionRecorder
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

class CameraFrameSampler(
    private val cadenceMillis: Long,
    private val recorderProvider: () -> SessionRecorder?,
    private val onFrame: (ByteArray) -> Unit,
) : ImageAnalysis.Analyzer {
    private val lastFrameMillis = AtomicLong(0L)

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            val previous = lastFrameMillis.get()
            if (now - previous < cadenceMillis || !lastFrameMillis.compareAndSet(previous, now)) {
                return
            }
            val bytes = image.toJpegBytes(quality = 72)
            recorderProvider()?.writeFrame(now, bytes)
            onFrame(bytes)
        } finally {
            image.close()
        }
    }

    private fun ImageProxy.toJpegBytes(quality: Int): ByteArray {
        val source = toBitmap()
        val matrix = Matrix().apply {
            setRotate(imageInfo.rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return ByteArrayOutputStream().use { output ->
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
    }
}
