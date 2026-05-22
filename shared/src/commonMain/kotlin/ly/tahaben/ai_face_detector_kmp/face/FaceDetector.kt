package ly.tahaben.ai_face_detector_kmp.face

data class FaceAnalysisState(
    val isSmiling: Boolean,
    val leftEyeOpen: Boolean,
    val rightEyeOpen: Boolean,
    val headTiltDegrees: Float,
    val jawOpen: Boolean = false,
    val browRaised: Boolean = false,
    val mouthOpen: Boolean = false,
    val winkLeft: Boolean = false,
    val winkRight: Boolean = false,
)

enum class DetectorKind { ML_KIT, MEDIAPIPE }

interface FaceDetector {
    fun analyze(frame: ImageFrame): FaceAnalysisState?
    fun close()
}
