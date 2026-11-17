package com.screensort.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    var monitoring by remember { mutableStateOf(ServicePrefs.isEnabled(context)) }

    LaunchedEffect(monitoring) {
        if (monitoring) ScreenshotService.start(context) else ScreenshotService.stop(context)
    }

    val selected = savedItems.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected == null) {
        ListScreen(
            savedItems = savedItems,
            monitoring = monitoring,
            onToggleMonitoring = { enabled ->
                monitoring = enabled
                ServicePrefs.setEnabled(context, enabled)
            },
            onOpen = { id -> selectedId = id }
        )
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