package ly.tahaben.ai_face_detector_kmp.face

actual object FaceDetectorFactory {
    actual fun create(kind: DetectorKind): FaceDetector {
        val swift = when (kind) {
            DetectorKind.ML_KIT -> requireNotNull(FaceDetectorBridge.mlKit) {
                "ML Kit detector not registered. Call FaceDetectorBridge.mlKit = MLKitFaceDetectorImpl() from iOSApp."
            }
            DetectorKind.MEDIAPIPE -> requireNotNull(FaceDetectorBridge.mediaPipe) {
                "MediaPipe detector not registered. Call FaceDetectorBridge.mediaPipe = MediaPipeFaceDetectorImpl() from iOSApp."
            }
        }
        return SwiftBackedFaceDetector(swift)
    }
}

private class SwiftBackedFaceDetector(private val swift: SwiftFaceDetector) : FaceDetector {
    override fun analyze(frame: ImageFrame): FaceAnalysisState? = swift.analyzeFrame(frame)
    override fun close() = swift.close()
}
