package ly.tahaben.ai_face_detector_kmp.face

import android.content.Context

actual object FaceDetectorFactory {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun create(kind: DetectorKind): FaceDetector {
        val ctx = appContext
            ?: error("FaceDetectorFactory.init(applicationContext) must be called before create()")
        return when (kind) {
            DetectorKind.ML_KIT -> MlKitFaceDetectorAndroid()
            DetectorKind.MEDIAPIPE -> MediaPipeFaceDetectorAndroid(ctx)
        }
    }
}
