package ly.tahaben.ai_face_detector_kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform