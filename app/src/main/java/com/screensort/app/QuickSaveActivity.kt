package com.screensort.app

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class QuickSaveActivity : ComponentActivity() {

    private var clipboardLink by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val itemId = intent.getLongExtra(NotificationHelper.EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) {
            finish()
            return
        }
        NotificationManagerCompat.from(this).cancel(itemId.toInt())

        setContent {
            MaterialTheme {
                QuickSaveSheet(
                    itemId = itemId,
                    clipboardLink = clipboardLink,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: ""
                val url = Regex("https?://\\S+").find(text)?.value
                if (url != null) clipboardLink = url
            }
        }
    }
}

private fun todayAt(hour: Int): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
fun QuickSaveSheet(itemId: Long, clipboardLink: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).savedItemDao() }
    val item by produceState<SavedItem?>(initialValue = null, itemId) {
        value = withContext(Dispatchers.IO) { dao.getById(itemId) }
    }

    var note by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var remindAt by remember { mutableStateOf<Long?>(null) }
    var choice by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(clipboardLink) {
        if (link.isBlank() && clipboardLink.isNotBlank()) link = clipboardLink
    }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Text("Saved to ScreenSort", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add details, then you're back to what you were doing.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Link") },
                    supportingText = {
                        if (link.isNotBlank() && link == clipboardLink) {
                            Text("Picked up from your clipboard")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Remind me", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = choice == "test",
                        onClick = {
                            remindAt = System.currentTimeMillis() + 60_000L
                            choice = "test"
                        },
                        label = { Text("1 min (test)") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = choice == "hour",
                        onClick = {
                            remindAt = System.currentTimeMillis() + 3_600_000L
                            choice = "hour"
                        },
                        label = { Text("In 1 hour") }
                    )
                    if (System.currentTimeMillis() < todayAt(20)) {
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = choice == "tonight",
                            onClick = {
                                remindAt = todayAt(20)
                                choice = "tonight"
                            },
                            label = { Text("Tonight 8 PM") }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = choice == "tomorrow",
                        onClick = {
                            remindAt = tomorrowAt(9)
                            choice = "tomorrow"
                        },
                        label = { Text("Tomorrow 9 AM") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = choice == "custom",
                        onClick = {
                            pickDateTime(context) { time ->
                                remindAt = time
                                choice = "custom"
                            }
                        },
                        label = { Text("Pick time...") }
                    )
                }
                val chosen = remindAt
                if (chosen != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reminder: ${formatDate(chosen)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val current = item
                        if (current == null) {
                            onClose()
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val noteLink = Regex("https?://\\S+").find(note)?.value
                                        ?.trimEnd('.', ',', ')', ']', '!', '?')
                                    val finalLink = link.trim().ifBlank { null }
                                        ?: noteLink
                                        ?: current.link
                                    val info = LinkParser.parse(finalLink)
                                    val updated = current.copy(
                                        note = note.trim(),
                                        link = finalLink,
                                        sourceApp = current.sourceApp ?: info.sourceApp,
                                        authorName = current.authorName ?: info.handle,
                                        remindAt = remindAt
                                    )
                                    dao.update(updated)
                                    if (remindAt != null) {
                                        ReminderScheduler.schedule(context, updated)
                                    }
                                }
                                onClose()
                            }
                        }
                    }) { Text("Save") }

                    TextButton(onClick = onClose) { Text("Skip") }
                }
            }
        }
    }
}