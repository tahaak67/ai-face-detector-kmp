package ly.tahaben.ai_face_detector_kmp.face

import androidx.camera.core.ImageAnalysis
import com.kashif.cameraK.controller.CameraController
import java.util.concurrent.atomic.AtomicReference

actual fun attachFaceAnalyzer(
    controller: CameraController,
    onResult: (FaceAnalysisState?) -> Unit,
): AnalyzerHandle {
    val active = AtomicReference<FaceDetector?>(null)

    val analyzer = ImageAnalysis.Analyzer { proxy ->
        try {
            val detector = active.get()
            if (detector != null) {
                val result = detector.analyze(ImageFrame(proxy))
                onResult(result)
            }
        } catch (t: Throwable) {
            onResult(null)
        } finally {
            proxy.close()
        }
    }
    controller.registerImageAnalyzer(analyzer)

    return object : AnalyzerHandle {
        override fun setDetector(detector: FaceDetector?) {
            active.set(detector)
        }
        override fun detach() {
            active.set(null)
            controller.unregisterImageAnalyzer(analyzer)
        }
    }
}
