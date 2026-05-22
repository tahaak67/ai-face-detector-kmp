package ly.tahaben.ai_face_detector_kmp.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashif.cameraK.compose.CameraKScreen
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.state.CameraConfiguration

@Composable
fun FaceScannerScreen(modifier: Modifier = Modifier) {
    var detectorKind by remember { mutableStateOf(DetectorKind.ML_KIT) }
    var latest by remember { mutableStateOf<FaceAnalysisState?>(null) }

    val cameraState by rememberCameraKState(
        config = CameraConfiguration(cameraLens = CameraLens.FRONT),
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        CameraKScreen(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
        ) { ready ->
            // Attach the analyzer exactly once per camera controller; the AVCaptureVideoDataOutput
            // (iOS) and ImageAnalysis.Analyzer (Android) are reused across detector swaps.
            val handle = remember(ready.controller) {
                attachFaceAnalyzer(ready.controller) { result -> latest = result }
            }
            DisposableEffect(handle) {
                onDispose { handle.detach() }
            }

            // Swap the detector behind the analyzer when the toggle changes.
            DisposableEffect(handle, detectorKind) {
                val detector = FaceDetectorFactory.create(detectorKind)
                handle.setDetector(detector)
                onDispose {
                    handle.setDetector(null)
                    detector.close()
                    latest = null
                }
            }
        }

        FaceLabelOverlay(
            state = latest,
            detectorKind = detectorKind,
            onToggle = { detectorKind = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

@Composable
private fun FaceLabelOverlay(
    state: FaceAnalysisState?,
    detectorKind: DetectorKind,
    onToggle: (DetectorKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetectorToggle(current = detectorKind, onToggle = onToggle)
        if (state == null) {
            Text("No face detected", color = Color.White)
        } else {
            Text("Smiling: ${if (state.isSmiling) "yes" else "no"}", color = Color.White)
            Text(
                "Left eye: ${if (state.leftEyeOpen) "open" else "closed"}",
                color = Color.White,
            )
            Text(
                "Right eye: ${if (state.rightEyeOpen) "open" else "closed"}",
                color = Color.White,
            )
            Text("Head tilt: ${state.headTiltDegrees.toInt()}°", color = Color.White)

            val expressions = buildList {
                if (state.jawOpen) add("jaw open")
                if (state.browRaised) add("brow raised")
                if (state.mouthOpen) add("mouth open")
                if (state.winkLeft) add("wink left")
                if (state.winkRight) add("wink right")
            }
            if (expressions.isNotEmpty()) {
                Text("Expressions: ${expressions.joinToString(", ")}", color = Color.White)
            }
        }
    }
}

@Composable
private fun DetectorToggle(
    current: DetectorKind,
    onToggle: (DetectorKind) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetectorKind.entries.forEach { kind ->
            val selected = kind == current
            val label = when (kind) {
                DetectorKind.ML_KIT -> nativeDetectorLabel
                DetectorKind.MEDIAPIPE -> "MediaPipe"
            }
            if (selected) {
                Button(
                    onClick = { onToggle(kind) },
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { onToggle(kind) }) {
                    Text(label, color = Color.White)
                }
            }
        }
    }
}
