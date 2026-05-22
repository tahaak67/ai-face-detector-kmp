import CoreGraphics
import Foundation
import Shared
import UIKit
import Vision

// Apple Vision framework — the iOS-native counterpart to Google ML Kit Face Detection.
// Built into iOS, fully on-device, no third-party dependency, no model bundle to ship.
// Vision returns raw face landmarks; we derive smile / eye-open / head-tilt from them.
final class VisionFaceDetectorImpl: NSObject, SwiftFaceDetector {

    private var loggedFirst = false

    func analyzeFrame(frame: ImageFrame) -> FaceAnalysisState? {
        guard let image = frame.uiImage(), let cgImage = image.cgImage else { return nil }

        if !loggedFirst {
            loggedFirst = true
            print("Vision[iOS]: first UIImage size=\(image.size)")
        }

        let request = VNDetectFaceLandmarksRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
        do {
            try handler.perform([request])
        } catch {
            print("Vision[iOS]: perform threw \(error)")
            return nil
        }

        guard let results = request.results, let face = results.first else { return nil }

        let tiltDegrees = (face.roll?.doubleValue ?? 0) * 180.0 / .pi

        let leftEyeOpen = isEyeOpen(face.landmarks?.leftEye)
        let rightEyeOpen = isEyeOpen(face.landmarks?.rightEye)
        let smiling = isSmiling(face.landmarks?.outerLips)
        let mouthOpen = isMouthOpen(face.landmarks?.innerLips)

        return FaceAnalysisState(
            isSmiling: smiling,
            leftEyeOpen: leftEyeOpen,
            rightEyeOpen: rightEyeOpen,
            headTiltDegrees: Float(tiltDegrees),
            jawOpen: false,
            browRaised: false,
            mouthOpen: mouthOpen,
            winkLeft: !leftEyeOpen && rightEyeOpen,
            winkRight: !rightEyeOpen && leftEyeOpen
        )
    }

    func close() {}

    // Eye-open from eye-contour aspect ratio: a closed eye is a thin horizontal line.
    private func isEyeOpen(_ region: VNFaceLandmarkRegion2D?) -> Bool {
        guard let region = region, region.pointCount > 0 else { return true }
        let points = region.normalizedPoints
        let xs = points.map { $0.x }
        let ys = points.map { $0.y }
        let width = (xs.max() ?? 0) - (xs.min() ?? 0)
        let height = (ys.max() ?? 0) - (ys.min() ?? 0)
        guard width > 0 else { return true }
        return (height / width) > 0.20
    }

    // Smile heuristic: in Vision's bottom-left origin coords, smiling pulls mouth
    // corners up, so corner-Y exceeds the mean Y of the outer-lip contour.
    private func isSmiling(_ region: VNFaceLandmarkRegion2D?) -> Bool {
        guard let region = region, region.pointCount >= 6 else { return false }
        let points = region.normalizedPoints
        guard let leftCorner = points.min(by: { $0.x < $1.x }),
              let rightCorner = points.max(by: { $0.x < $1.x }) else { return false }
        let cornerAvgY = (leftCorner.y + rightCorner.y) / 2
        let centerY = points.map { $0.y }.reduce(0, +) / CGFloat(points.count)
        return cornerAvgY > centerY
    }

    private func isMouthOpen(_ region: VNFaceLandmarkRegion2D?) -> Bool {
        guard let region = region, region.pointCount > 0 else { return false }
        let points = region.normalizedPoints
        let xs = points.map { $0.x }
        let ys = points.map { $0.y }
        let width = (xs.max() ?? 0) - (xs.min() ?? 0)
        let height = (ys.max() ?? 0) - (ys.min() ?? 0)
        guard width > 0 else { return false }
        return (height / width) > 0.25
    }
}
