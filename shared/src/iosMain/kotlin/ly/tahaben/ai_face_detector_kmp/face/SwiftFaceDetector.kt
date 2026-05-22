package ly.tahaben.ai_face_detector_kmp.face

interface SwiftFaceDetector {
    fun analyzeFrame(frame: ImageFrame): FaceAnalysisState?
    fun close()
}

object FaceDetectorBridge {
    var mlKit: SwiftFaceDetector? = null
    var mediaPipe: SwiftFaceDetector? = null
}
