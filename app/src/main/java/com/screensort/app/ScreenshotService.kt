package com.screensort.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object ServicePrefs {
    private const val PREFS = "screensort_prefs"
    private const val KEY = "monitoring_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}

class ScreenshotService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ScreenshotObserver? = null
    private var recognizer: TextRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        createServiceChannel()

        val notification = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }

        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer = textRecognizer

        val screenshotObserver = ScreenshotObserver(contentResolver) { uri, _ ->
            handleScreenshot(textRecognizer, uri)
        }
        observer = screenshotObserver
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        recognizer?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun handleScreenshot(textRecognizer: TextRecognizer, uri: Uri) {
        val uriString = uri.toString()
        try {
            val image = InputImage.fromFilePath(applicationContext, uri)
            textRecognizer.process(image)
                .addOnSuccessListener { result -> save(uriString, result.text) }
                .addOnFailureListener { save(uriString, "") }
        } catch (e: Exception) {
            save(uriString, "")
        }
    }

    private fun save(imageUri: String, text: String) {
        scope.launch {
            val id = AppDatabase.get(applicationContext).savedItemDao()
                .insert(SavedItem(imageUri = imageUri, ocrText = text))
            NotificationHelper.showSaved(applicationContext, id, imageUri)
        }
    }

    private fun createServiceChannel() {
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "ScreenSort status",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Shows that ScreenSort is watching for screenshots"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildServiceNotification() = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("ScreenSort is on")
        .setContentText("Watching for screenshots")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        const val SERVICE_CHANNEL_ID = "screensort_service"
        const val SERVICE_NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ScreenshotService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotService::class.java))
        }
    }
}