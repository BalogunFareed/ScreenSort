package com.screensort.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val CHANNEL_ID = "screensort_saved"
    const val KEY_NOTE = "key_note"
    const val EXTRA_ITEM_ID = "extra_item_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Saved screenshots",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Lets you add a note to each screenshot"
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    private fun shareAction(
        context: Context,
        itemId: Long,
        imageUri: String,
        note: String
    ): NotificationCompat.Action {
        val uri = Uri.parse(imageUri)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (note.isNotBlank()) putExtra(Intent.EXTRA_TEXT, note)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share screenshot")
        val pendingIntent = PendingIntent.getActivity(
            context,
            itemId.toInt() + 1_000_000,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_share, "Share", pendingIntent
        ).build()
    }

    @SuppressLint("MissingPermission")
    fun showSaved(context: Context, itemId: Long, imageUri: String) {
        if (!canNotify(context)) return

        val remoteInput = RemoteInput.Builder(KEY_NOTE)
            .setLabel("Add a note or paste a link")
            .build()

        val intent = Intent(context, NoteReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            context, itemId.toInt(), intent, flags
        )

        val noteAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Add note", pendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Screenshot saved to ScreenSort")
            .setContentText("Add a note, or share it")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(noteAction)
            .addAction(shareAction(context, itemId, imageUri, ""))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(itemId.toInt(), notification)
    }

    @SuppressLint("MissingPermission")
    fun showNoteSaved(context: Context, itemId: Long, note: String, imageUri: String) {
        if (!canNotify(context)) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Note saved")
            .setContentText(note)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

        if (imageUri.isNotBlank()) {
            builder.addAction(shareAction(context, itemId, imageUri, note))
        }

        NotificationManagerCompat.from(context).notify(itemId.toInt(), builder.build())
    }
}