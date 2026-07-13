package com.pannu.balloonblaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandTrackingController(
    context: Context,
    private val listener: Listener
) : AutoCloseable {

    private var handLandmarker: HandLandmarker? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.48f)
                .setMinHandPresenceConfidence(0.48f)
                .setMinTrackingConfidence(0.45f)
                .setNumHands(1)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::onResult)
                .setErrorListener(this::onError)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            listener.onStatus("Hand sensor ready")
        } catch (error: Exception) {
            listener.onError("Hand sensor failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun process(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            bitmapBuffer.copyPixelsFromBuffer(buffer)
            imageProxy.close()

            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
            )
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (error: Exception) {
            try { imageProxy.close() } catch (_: Exception) { }
            listener.onError("Camera frame error: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun onResult(result: HandLandmarkerResult, input: MPImage) {
        val hands = result.landmarks().map { hand ->
            hand.map { landmark -> NormalizedPoint(landmark.x(), landmark.y()) }
        }
        listener.onHands(hands, result.timestampMs())
    }

    private fun onError(error: RuntimeException) {
        listener.onError(error.message ?: "Unknown MediaPipe error")
    }

    override fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }

    interface Listener {
        fun onHands(hands: List<List<NormalizedPoint>>, timestampMs: Long)
        fun onStatus(message: String)
        fun onError(message: String)
    }

    companion object {
        const val MODEL_NAME = "hand_landmarker.task"
    }
}
