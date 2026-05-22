package ly.tahaben.ai_face_detector_kmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import ly.tahaben.ai_face_detector_kmp.face.FaceScannerScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            FaceScannerScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
