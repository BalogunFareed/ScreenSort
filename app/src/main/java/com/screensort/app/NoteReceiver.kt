package com.screensort.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(NotificationHelper.EXTRA_ITEM_ID, -1L)
        val note = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_NOTE)
            ?.toString()
            ?.trim()

        if (itemId == -1L || note.isNullOrEmpty()) return

        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(appContext).savedItemDao()
                val item = dao.getById(itemId)
                if (item != null) {
                    val link = Regex("https?://\\S+").find(note)?.value
                    dao.update(item.copy(note = note, link = link ?: item.link))
                }
                NotificationHelper.showNoteSaved(appContext, itemId, note)
            } finally {
                pending.finish()
            }
        }
    }
}