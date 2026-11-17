package com.screensort.app

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

class ScreenshotObserver(
    private val resolver: ContentResolver,
    private val onScreenshot: (Uri, String) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastId: Long = -1
    private var lastHandledAt: Long = 0

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: ""
                val folder = cursor.getString(2) ?: ""

                val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                        folder.contains("screenshot", ignoreCase = true)

                if (isScreenshot && id != lastId) {
                    lastId = id
                    val now = System.currentTimeMillis()
                    if (now - lastHandledAt < 3000) return@use
                    lastHandledAt = now

                    val imageUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )
                    onScreenshot(imageUri, name)
                }
            }
        }
    }
}