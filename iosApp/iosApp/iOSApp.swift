import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Note: the bridge slot is still called `mlKit` for symmetry with Android,
        // but iOS routes to Apple Vision because Google ML Kit's SPM distribution
        // doesn't ship a working face-detection model bundle.
        FaceDetectorBridge.shared.mlKit = VisionFaceDetectorImpl()
        FaceDetectorBridge.shared.mediaPipe = MediaPipeFaceDetectorImpl()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
