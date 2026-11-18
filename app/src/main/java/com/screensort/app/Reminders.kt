package com.screensort.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UserPrefs {
    private const val PREFS = "screensort_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_ASKED = "asked_name"

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "") ?: ""

    fun hasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED, false)

    fun saveName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name.trim())
            .putBoolean(KEY_ASKED, true)
            .apply()
    }
}

object ReminderScheduler {
    const val EXTRA_ITEM_ID = "reminder_item_id"

    private fun pendingIntent(context: Context, itemId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ITEM_ID, itemId)
        return PendingIntent.getBroadcast(
            context,
            itemId.toInt() + 2_000_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, item: SavedItem) {
        val time = item.remindAt ?: return
        if (time <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, item.id)
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun cancel(context: Context, itemId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, itemId))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(ReminderScheduler.EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return

        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(appContext).savedItemDao()
                val item = dao.getById(itemId)
                if (item != null) {
                    showReminder(appContext, item)
                    dao.update(item.copy(remindAt = null))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildMessage(context: Context, item: SavedItem): String {
        val name = UserPrefs.getName(context)
        val lead = if (name.isNotBlank()) "$name, don't forget" else "Don't forget"
        val author = item.authorName
        val source = item.sourceApp
        return when {
            author != null && source != null -> "$lead to check $author's post on $source."
            source != null -> "$lead to check your saved $source post."
            author != null -> "$lead to check $author's post."
            item.note.isNotBlank() -> "$lead: ${item.note.take(100)}"
            else -> "$lead to check your saved screenshot."
        }
    }

    @SuppressLint("MissingPermission")
    private fun showReminder(context: Context, item: SavedItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        NotificationHelper.createChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            item.id.toInt() + 3_000_000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val link = item.link
        val tapIntent = if (link != null && link.startsWith("http")) {
            PendingIntent.getActivity(
                context,
                item.id.toInt() + 4_000_000,
                Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            openApp
        }

        val message = buildMessage(context, item)
        val bigText = if (item.note.isNotBlank()) {
            "$message\n\nYour note: ${item.note}"
        } else {
            message
        }

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("ScreenSort reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Open in ScreenSort", openApp)
            .build()

        NotificationManagerCompat.from(context)
            .notify(item.id.toInt() + 3_000_000, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(appContext).savedItemDao()
                    .getUpcomingReminders(System.currentTimeMillis())
                    .forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pending.finish()
            }
        }
    }
}

@Composable
fun NameDialog(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone("") },
        title = { Text("What should ScreenSort call you?") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone("") }) { Text("Skip") } }
    )
}