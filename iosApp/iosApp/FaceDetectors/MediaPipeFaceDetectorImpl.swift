import CoreMedia
import Foundation
import MediaPipeTasksVision
import Shared
import UIKit

final class MediaPipeFaceDetectorImpl: NSObject, SwiftFaceDetector {

    private let landmarker: FaceLandmarker
    private var lockedOrientation: UIImage.Orientation?
    private var loggedFirst = false
    private let candidates: [UIImage.Orientation] = [
        .up, .right, .left, .down, .upMirrored, .rightMirrored, .leftMirrored, .downMirrored,
    ]

    override init() {
        guard let modelPath = Bundle.main.path(forResource: "face_landmarker", ofType: "task") else {
            fatalError("face_landmarker.task missing from app bundle — add it to the iosApp target's resources.")
        }
        let options = FaceLandmarkerOptions()
        options.baseOptions.modelAssetPath = modelPath
        options.runningMode = .image
        options.numFaces = 1
        options.outputFaceBlendshapes = true
        do {
            self.landmarker = try FaceLandmarker(options: options)
        } catch {
            fatalError("MediaPipe FaceLandmarker init failed: \(error)")
        }
        super.init()
    }

    func analyzeFrame(frame: ImageFrame) -> FaceAnalysisState? {
        guard let image = frame.uiImage() else {
            if !loggedFirst {
                loggedFirst = true
                print("MediaPipe[iOS]: uiImage() returned nil")
            }
            return nil
        }

        if !loggedFirst {
            loggedFirst = true
            print("MediaPipe[iOS]: first UIImage size=\(image.size)")
        }

        if let locked = lockedOrientation {
            return runDetection(image: image, orientation: locked)
        }

        for orientation in candidates {
            if let state = runDetection(image: image, orientation: orientation, isProbe: true) {
                lockedOrientation = orientation
                print("MediaPipe[iOS]: locked orientation = \(orientation.rawValue)")
                return state
            }
        }
        return nil
    }

    private func runDetection(
        image: UIImage,
        orientation: UIImage.Orientation,
        isProbe: Bool = false,
    ) -> FaceAnalysisState? {
        do {
            let mpImage = try MPImage(uiImage: image, orientation: orientation)
            let result = try landmarker.detect(image: mpImage)
            guard let blendshapes = result.faceBlendshapes.first else { return nil }

            var scores: [String: Float] = [:]
            for category in blendshapes.categories {
                scores[category.categoryName ?? ""] = category.score
            }

            let eyeBlinkLeft = scores["eyeBlinkLeft"] ?? 0
            let eyeBlinkRight = scores["eyeBlinkRight"] ?? 0
            let smileLeft = scores["mouthSmileLeft"] ?? 0
            let smileRight = scores["mouthSmileRight"] ?? 0
            let jawOpen = scores["jawOpen"] ?? 0
            let browInnerUp = scores["browInnerUp"] ?? 0
            let mouthFunnel = scores["mouthFunnel"] ?? 0

            return FaceAnalysisState(
                isSmiling: (smileLeft + smileRight) / 2 > 0.4,
                leftEyeOpen: eyeBlinkLeft < 0.5,
                rightEyeOpen: eyeBlinkRight < 0.5,
                headTiltDegrees: 0,
                jawOpen: jawOpen > 0.4,
                browRaised: browInnerUp > 0.4,
                mouthOpen: mouthFunnel > 0.4,
                winkLeft: eyeBlinkLeft > 0.6 && eyeBlinkRight < 0.4,
                winkRight: eyeBlinkRight > 0.6 && eyeBlinkLeft < 0.4
            )
        } catch {
            if !isProbe {
                print("MediaPipe[iOS]: detection threw \(error)")
            }
            return nil
        }
    }

    func close() {}
}
