package ly.tahaben.ai_face_detector_kmp.face

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMedia.CMSampleBufferRef

@OptIn(ExperimentalForeignApi::class)
actual class ImageFrame(val sampleBuffer: CMSampleBufferRef)
