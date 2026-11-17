package com.screensort.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

private fun saveItem(context: Context, imageUri: String, text: String) {
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        val id = AppDatabase.get(appContext).savedItemDao()
            .insert(SavedItem(imageUri = imageUri, ocrText = text))
        NotificationHelper.showSaved(appContext, id, imageUri)
    }
}

@Composable
fun AppRoot() {
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

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(granted) {
        if (granted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (granted) {
        MainContent()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ScreenSort", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("ScreenSort needs access to your photos to find your screenshots.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(permission) }) {
                Text("Allow access")
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).savedItemDao() }
    val savedItems by remember { dao.getAll() }.collectAsState(initial = emptyList())
    var selectedId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val observer = ScreenshotObserver(context.contentResolver) { uri, _ ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        saveItem(context, uri.toString(), result.text)
                    }
                    .addOnFailureListener {
                        saveItem(context, uri.toString(), "")
                    }
            } catch (e: Exception) {
                saveItem(context, uri.toString(), "")
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

    val selected = savedItems.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected == null) {
        ListScreen(savedItems) { id -> selectedId = id }
    } else {
        DetailScreen(
            item = selected,
            onBack = { selectedId = null },
            onSave = { updated -> scope.launch(Dispatchers.IO) { dao.update(updated) } },
            onDelete = { item ->
                scope.launch(Dispatchers.IO) { dao.delete(item) }
                selectedId = null
            }
        )
    }
}