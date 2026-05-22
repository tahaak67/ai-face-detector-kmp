# ai-face-detector-kmp

A small Kotlin Multiplatform demo for a talk on **building offline AI apps for mobile**.

The front camera streams on both Android and iOS, and the same shared Compose UI overlays live text describing what the on-device model sees on your face:

- `Smiling` / `Not smiling`
- `Left eye: open / closed`, `Right eye: open / closed`, `Wink: left / right`
- `Head tilt: ±X°`
- Expressions like `Jaw open`, `Brow raised`, `Mouth open`

All inference runs on-device. No network calls, no API keys, no cloud round-trip — the entire pipeline (camera frame → ML model → UI label) stays on the phone.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.3.21 (Kotlin Multiplatform) |
| Shared UI | Compose Multiplatform 1.11 |
| Android tooling | AGP 9.0.1, `minSdk 24`, `compileSdk 36`, using the new `com.android.kotlin.multiplatform.library` plugin |
| Camera | **CameraK** — KMP camera library with per-frame image analysis on Android and iOS |
| Native detector (Android) | **Google ML Kit Face Detection** — pre-computed smile / eye-open probabilities and head Euler angles |
| Native detector (iOS) | **Apple Vision** (`VNDetectFaceLandmarksRequest`) — built into iOS; returns face landmark contours from which we derive smile / eye / mouth booleans |
| Cross-platform detector | **MediaPipe Face Landmarker** — single `.task` model on both platforms; 478 landmarks + 52 blendshapes |

Both detector "slots" sit behind a common `FaceDetector` interface in `commonMain`, and the UI exposes a **runtime toggle** to switch between them on the same live camera feed. The toggle chip is platform-aware: it reads **"ML Kit"** on Android and **"Vision Kit"** on iOS, since they share the "platform-native batteries-included" slot.

## Why two detectors

**Native detector slot — ML Kit on Android, Vision on iOS.** Both are platform-blessed, batteries-included, on-device face detection. The talk uses this slot to demonstrate "the easy path: use what the OS gives you for free." Note: Android's ML Kit returns pre-computed `smilingProbability` etc., while Apple Vision only returns landmark *contours* — so on iOS we derive the booleans from landmark geometry (see [iOS Vision heuristics](#ios-vision-heuristics) below).

> Originally this project planned to use Google ML Kit on both platforms. ML Kit's iOS SDK is officially distributed via CocoaPods only; the community Swift Package Manager wrappers (e.g. `d-date/google-mlkit-swiftpm`) don't ship the face-detection model resource bundle — the framework loads, runs, and silently returns zero faces. To stay SPM-only with no CocoaPods, the iOS side now uses Apple's Vision framework instead — same conceptual slot, fully on-device, no third-party dependency.

**MediaPipe Face Landmarker** — the *real ML in your app* option. You ship a single `.task` model file, and on both platforms get back the same 478 face landmarks plus 52 ARKit-style blendshapes (`eyeBlinkLeft`, `mouthSmileLeft`, `jawOpen`, `browInnerUp`, …). Identical numerical output on Android and iOS makes the cross-platform logic trivial, the blendshapes unlock richer expressions for the demo, and the *"here is the model file we ship on-device"* angle makes the offline-AI story concrete on stage.

## iOS integration: Swift bridge + SPM

To keep CocoaPods out of the toolchain entirely, the project uses a **Swift bridge** for the iOS face-detector implementations rather than the Kotlin CocoaPods plugin in `:shared`:

- `:shared` stays pure Kotlin — no `cocoapods { … }` block, no `pod install` step, no Podfile.
- `commonMain` owns the `FaceDetector` interface, the `FaceAnalysisState` data class, the detector toggle, and the Compose camera UI.
- On iOS, the `actual` `FaceDetectorFactory` doesn't call Vision / MediaPipe itself — it returns a thin Kotlin wrapper that delegates to a `SwiftFaceDetector` Kotlin protocol. Swift classes inside `iosApp` conform to that protocol and do the real SDK work.
- Apple Vision is part of iOS, so there's no iOS-side dependency needed for the native slot. **MediaPipeTasksVision** is added via [`paescebu/SwiftTasksVision`](https://github.com/paescebu/SwiftTasksVision) — a community SPM wrapper, auto-updated daily against the upstream pod.

Result: one SPM dependency, no CocoaPods, no Podfile, faster Xcode opens, and `:shared` stays genuinely platform-agnostic.

### Shape of the bridge (as implemented)

**`commonMain`** — what the camera screen talks to (`shared/src/commonMain/.../face/`):

```kotlin
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

expect class ImageFrame           // wraps ImageProxy on Android, CMSampleBufferRef on iOS
expect val nativeDetectorLabel: String   // "ML Kit" on Android, "Vision Kit" on iOS

expect object FaceDetectorFactory {
    fun create(kind: DetectorKind): FaceDetector
}

interface AnalyzerHandle {
    fun setDetector(detector: FaceDetector?)
    fun detach()
}

expect fun attachFaceAnalyzer(
    controller: CameraController,
    onResult: (FaceAnalysisState?) -> Unit,
): AnalyzerHandle
```

The analyzer is attached **once per camera session** (single `AVCaptureVideoDataOutput` on iOS, single `ImageAnalysis.Analyzer` on Android); toggling between detectors just swaps a `@Volatile` reference inside the handle, which avoids re-adding outputs to the AVCaptureSession on each toggle.

**`androidMain`** — Kotlin calls the Android SDKs directly:

- `MlKitFaceDetectorAndroid` — `InputImage.fromBitmap` + async `process()` with `addOnSuccessListener`. Drops frames while a previous detection is in flight to keep the analyzer thread non-blocking.
- `MediaPipeFaceDetectorAndroid` — `toBitmap` → rotate → `BitmapImageBuilder` → `FaceLandmarker.detect`. Reads blendshapes from the result.

**`iosMain`** — bridge protocol + registration point + analyzer:

```kotlin
interface SwiftFaceDetector {                // Swift conforms to this protocol
    fun analyzeFrame(frame: ImageFrame): FaceAnalysisState?
    fun close()
}

object FaceDetectorBridge {                  // populated from iOSApp.swift at startup
    var mlKit: SwiftFaceDetector? = null     // routed to VisionFaceDetectorImpl
    var mediaPipe: SwiftFaceDetector? = null
}

actual object FaceDetectorFactory {
    actual fun create(kind: DetectorKind): FaceDetector = SwiftBackedFaceDetector(
        when (kind) {
            DetectorKind.ML_KIT    -> requireNotNull(FaceDetectorBridge.mlKit)
            DetectorKind.MEDIAPIPE -> requireNotNull(FaceDetectorBridge.mediaPipe)
        }
    )
}
```

`attachFaceAnalyzer.ios.kt` creates an `AVCaptureVideoDataOutput`, adds it to the session via `controller.safeAddOutput { … }`, forces `videoOrientation = .portrait` on the connection so frames arrive upright, and routes each `CMSampleBuffer` to the currently-installed `SwiftFaceDetector`.

**`iosApp`** — Swift implementations registered at app start:

```swift
// iOSApp.swift
@main
struct iOSApp: App {
    init() {
        // The bridge slot is still called `mlKit` for symmetry with Android,
        // but iOS routes it to Apple Vision (built into iOS, no SPM needed).
        FaceDetectorBridge.shared.mlKit     = VisionFaceDetectorImpl()
        FaceDetectorBridge.shared.mediaPipe = MediaPipeFaceDetectorImpl()
    }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

Kotlin/Native exposes the `SwiftFaceDetector` Kotlin interface to Swift as a regular protocol, and `FaceDetectorBridge` becomes a `.shared` singleton in Swift. The Swift impls receive `CMSampleBufferRef` as an `UnsafeMutableRawPointer`; a small `SampleBufferBridge.swift` extension unwraps it back to `CMSampleBuffer`, then converts to `UIImage` (via `CIImage` → `CGImage`) since both Vision (`VNImageRequestHandler(cgImage:)`) and MediaPipe (`MPImage(uiImage:)`) accept that path most reliably across SDK versions.

### iOS Vision heuristics

Apple Vision's `VNDetectFaceLandmarksRequest` returns face landmarks as `VNFaceLandmarkRegion2D` point sets (eyes, lips, eyebrows, contour, etc.) — *not* pre-computed booleans like ML Kit. `VisionFaceDetectorImpl` derives the `FaceAnalysisState` fields from those points:

- **Head tilt** — direct from `face.roll` (radians → degrees).
- **Eye open (left & right)** — bounding-box aspect ratio of the eye's `normalizedPoints`. A closed eye is a thin horizontal slit, so `height / width > 0.20` ⇒ open. Threshold chosen empirically; tune per device if needed.
- **Smile** — Vision's coordinate system has Y increasing upward. Smiling pulls the mouth corners *up*, so we compare the average Y of the two outermost lip points (the corners) to the mean Y of the whole `outerLips` contour. `cornerAvgY > meanY` ⇒ smile. This is a geometric heuristic, not a trained classifier — it picks up most smiles cleanly but is less robust than ML Kit's `smilingProbability` for subtle or partial expressions.
- **Mouth open** — bounding-box aspect ratio of the `innerLips` contour: `height / width > 0.25` ⇒ open. Same idea as eye-open.
- **Jaw open / brow raised** — not derived on iOS (would need more landmark geometry); set to `false`. MediaPipe still surfaces these on iOS via blendshapes.
- **Wink (left / right)** — one eye closed while the other is open.

The intent is to keep the "feature surface" similar between detectors so the demo doesn't suddenly show fewer labels when you switch to **Vision Kit**. The heuristics are transparent and easy to explain on stage; a small CoreML expression classifier on top of the same landmarks would be the natural next step for production-grade accuracy.

## Project structure

```
androidApp/
  src/main/
    AndroidManifest.xml                                       # CAMERA permission + features
    assets/face_landmarker.task                               # MediaPipe model (bundled into APK)
    kotlin/.../MainActivity.kt                                # FaceDetectorFactory.init + camera permission request
iosApp/iosApp/
    iOSApp.swift                                              # registers Swift detectors into FaceDetectorBridge
    Info.plist                                                # NSCameraUsageDescription
    face_landmarker.task                                      # MediaPipe model (must be added to Xcode target)
    FaceDetectors/
        SampleBufferBridge.swift                              # OpaquePointer → CMSampleBuffer → UIImage helper
        VisionFaceDetectorImpl.swift                          # Apple Vision (built into iOS)
        MediaPipeFaceDetectorImpl.swift                       # uses MediaPipeTasksVision (SPM)
shared/src/
    commonMain/.../face/
        FaceDetector.kt                                       # FaceAnalysisState, DetectorKind, FaceDetector
        ImageFrame.kt                                         # expect class
        DetectorLabels.kt                                     # expect val nativeDetectorLabel
        FaceDetectorFactory.kt                                # expect object + AnalyzerHandle + expect attachFaceAnalyzer
        FaceScannerScreen.kt                                  # CameraK preview + detector toggle + label overlay
    androidMain/.../face/
        ImageFrame.android.kt                                 # wraps androidx.camera.core.ImageProxy
        DetectorLabels.android.kt                             # "ML Kit"
        FaceDetectorFactory.android.kt                        # context-aware factory (call .init(app) first)
        AttachFaceAnalyzer.android.kt                         # controller.registerImageAnalyzer(...)
        MlKitFaceDetectorAndroid.kt                           # async InputImage.fromBitmap → ML Kit
        MediaPipeFaceDetectorAndroid.kt                       # toBitmap + rotate → BitmapImageBuilder → FaceLandmarker
    iosMain/.../face/
        ImageFrame.ios.kt                                     # wraps CMSampleBufferRef
        DetectorLabels.ios.kt                                 # "Vision Kit"
        SwiftFaceDetector.kt                                  # Kotlin protocol Swift conforms to
        FaceDetectorFactory.ios.kt                            # delegates to FaceDetectorBridge
        AttachFaceAnalyzer.ios.kt                             # AVCaptureVideoDataOutput → safeAddOutput
```

## Permissions

- **Android** — `android.permission.CAMERA` in `androidApp/src/main/AndroidManifest.xml`, requested at runtime before the preview composable mounts.
- **iOS** — `NSCameraUsageDescription` in `iosApp/iosApp/Info.plist`; the system shows the consent prompt on first launch.

The demo uses the **front camera only**.

## Adding the iOS SPM package (one-time setup)

After cloning, the iOS app needs one Swift Package Manager dependency added in Xcode (it isn't tracked by Gradle — it lives in the Xcode project):

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. **File → Add Package Dependencies…**
3. Add the MediaPipe wrapper:
   - URL: `https://github.com/paescebu/SwiftTasksVision`
   - Dependency rule: **Up to Next Major Version**
   - Product to add to the `iosApp` target: **`MediaPipeTasksVision`**
4. Build once — SPM resolves the upstream binary.

Apple Vision is built into iOS — no SPM, no CocoaPods, no Podfile.

### Adding the MediaPipe model to the iOS target

`iosApp/iosApp/face_landmarker.task` (3.6 MB) needs to be in the iOS app bundle so `MediaPipeFaceDetectorImpl` can find it at `Bundle.main.path(forResource: "face_landmarker", ofType: "task")`.

1. In Xcode's Project Navigator, drag `iosApp/iosApp/face_landmarker.task` into the `iosApp` group.
2. In the dialog, ensure **Copy items if needed** is *unchecked* (it's already in the right folder) and that **Add to targets: iosApp** is checked.
3. Verify it appears under **Build Phases → Copy Bundle Resources** for the `iosApp` target.

On Android the model is already at `androidApp/src/main/assets/face_landmarker.task` and gets bundled into the APK automatically.

## Running

- **Android** — `./gradlew :androidApp:assembleDebug`, then install on a physical device. The emulator's synthetic camera does not produce realistic face data.
- **iOS** — open `iosApp/iosApp.xcodeproj` in Xcode and run on a physical device. SPM resolves `SwiftTasksVision` on first build.

## Tests

- Android (host JVM): `./gradlew :shared:testAndroidHostTest`
- iOS (simulator): `./gradlew :shared:iosSimulatorArm64Test`
