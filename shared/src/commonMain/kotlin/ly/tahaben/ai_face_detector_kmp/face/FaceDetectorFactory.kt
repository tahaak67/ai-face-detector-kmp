package ly.tahaben.ai_face_detector_kmp.face

import com.kashif.cameraK.controller.CameraController

expect object FaceDetectorFactory {
    fun create(kind: DetectorKind): FaceDetector
}

interface AnalyzerHandle {
    fun setDetector(detector: FaceDetector?)
    fun detach()
}

expect fun attachFaceAnalyzer(
    controller: CameraController,
    onResult: (FaceAnalysisState?) -> Unit,
): AnalyzerHandle
