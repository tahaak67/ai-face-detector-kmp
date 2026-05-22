package ly.tahaben.ai_face_detector_kmp.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

class MediaPipeFaceDetectorAndroid(context: Context) : FaceDetector {

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context.applicationContext,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageFrame): FaceAnalysisState? {
        val bitmap = frame.imageProxy.toBitmap()
        val rotation = frame.imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation == 0) bitmap else bitmap.rotated(rotation)

        val mpImage = BitmapImageBuilder(rotated).build()
        val result = landmarker.detect(mpImage)

        val blendshapesList = result.faceBlendshapes()
        if (!blendshapesList.isPresent || blendshapesList.get().isEmpty()) return null
        val scores = blendshapesList.get().first().associate { it.categoryName() to it.score() }

        val eyeBlinkLeft = scores["eyeBlinkLeft"] ?: 0f
        val eyeBlinkRight = scores["eyeBlinkRight"] ?: 0f
        val smileLeft = scores["mouthSmileLeft"] ?: 0f
        val smileRight = scores["mouthSmileRight"] ?: 0f
        val jawOpen = scores["jawOpen"] ?: 0f
        val browInnerUp = scores["browInnerUp"] ?: 0f
        val mouthFunnel = scores["mouthFunnel"] ?: 0f

        return FaceAnalysisState(
            isSmiling = (smileLeft + smileRight) / 2f > 0.4f,
            leftEyeOpen = eyeBlinkLeft < 0.5f,
            rightEyeOpen = eyeBlinkRight < 0.5f,
            headTiltDegrees = 0f,
            jawOpen = jawOpen > 0.4f,
            browRaised = browInnerUp > 0.4f,
            mouthOpen = mouthFunnel > 0.4f,
            winkLeft = eyeBlinkLeft > 0.6f && eyeBlinkRight < 0.4f,
            winkRight = eyeBlinkRight > 0.6f && eyeBlinkLeft < 0.4f,
        )
    }

    override fun close() {
        landmarker.close()
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
