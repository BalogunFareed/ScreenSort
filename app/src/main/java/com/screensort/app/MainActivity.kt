package com.screensort.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionScreen()
                }
            }
        }
    }
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ScreenSort", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (granted) {
            var lastName by remember { mutableStateOf("None yet") }
            var ocrText by remember { mutableStateOf("") }

            DisposableEffect(Unit) {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val observer = ScreenshotObserver(context.contentResolver) { uri, name ->
                    lastName = name
                    ocrText = "Reading..."
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        recognizer.process(image)
                            .addOnSuccessListener { result ->
                                ocrText = result.text.ifBlank { "No text found" }
                            }
                            .addOnFailureListener { e ->
                                ocrText = "OCR failed: ${e.message}"
                            }
                    } catch (e: Exception) {
                        ocrText = "Could not open image: ${e.message}"
                    }
                }
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
                )
                onDispose {
                    context.contentResolver.unregisterContentObserver(observer)
                    recognizer.close()
                }
            }

            Text("Permission granted. ScreenSort can read your screenshots.")
            Spacer(Modifier.height(16.dp))
            Text("Last screenshot detected: $lastName")
            Spacer(Modifier.height(16.dp))
            Text("Text found:", style = MaterialTheme.typography.titleMedium)
            Text(ocrText)
        } else {
            Text("ScreenSort needs access to your photos to find your screenshots.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(permission) }) {
                Text("Allow access")
            }
        }
    }
}