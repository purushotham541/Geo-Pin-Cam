package com.letscode.geopincam.ui.camera

import android.graphics.PorterDuff
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import com.letscode.geopincam.data.image.PhotoStampProcessor
import com.letscode.geopincam.data.image.StampRenderSpec

/**
 * A CameraX [OverlayEffect] that burns the GPS stamp into the recorded video, the
 * same way [PhotoStampProcessor] burns it into a photo.
 *
 * Only the VIDEO_CAPTURE stream is targeted: photos still go through the still
 * pipeline and the viewfinder keeps its own Compose stamp preview. The draw
 * callback runs on a dedicated thread and reads [spec] without locking, so the
 * clock on a running recording keeps ticking.
 */
class VideoStampEffect(
    private val processor: PhotoStampProcessor = PhotoStampProcessor()
) {

    /** The stamp to draw on every frame, or null to draw nothing. Updated freely. */
    @Volatile
    var spec: StampRenderSpec? = null

    private val thread = HandlerThread("VideoStampOverlay").apply { start() }

    val effect: OverlayEffect = OverlayEffect(
        CameraEffect.VIDEO_CAPTURE,
        QUEUE_DEPTH,
        Handler(thread.looper)
    ) { error -> Log.w(TAG, "Video stamp overlay error", error) }

    init {
        effect.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            spec?.let { current ->
                runCatching {
                    processor.drawStampOnCanvas(
                        canvas = canvas,
                        bufferWidth = frame.size.width,
                        bufferHeight = frame.size.height,
                        rotationDegrees = frame.rotationDegrees,
                        mirrored = frame.isMirroring,
                        spec = current
                    )
                }.onFailure { Log.w(TAG, "Stamp draw failed", it) }
            }
            true
        }
    }

    fun release() {
        runCatching { effect.close() }
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "VideoStampEffect"

        /** No buffering: the draw is a few text lines and needs the lowest latency. */
        const val QUEUE_DEPTH = 0
    }
}
