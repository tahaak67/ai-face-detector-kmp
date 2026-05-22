import CoreImage
import CoreMedia
import CoreVideo
import Shared
import UIKit

// Kotlin/Native exports CMSampleBufferRef as UnsafeMutableRawPointer.
// Unwrap it back to the typed CMSampleBuffer and provide a UIImage rendering as well.
extension ImageFrame {
    func cmSampleBuffer() -> CMSampleBuffer {
        return Unmanaged<CMSampleBuffer>.fromOpaque(self.sampleBuffer).takeUnretainedValue()
    }

    func uiImage() -> UIImage? {
        let buffer = self.cmSampleBuffer()
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(buffer) else { return nil }
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = SampleBufferBridge.ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}

private enum SampleBufferBridge {
    static let ciContext = CIContext(options: nil)
}
