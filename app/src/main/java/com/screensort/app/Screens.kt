package com.screensort.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

private fun tomorrowAt(hour: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun pickDateTime(context: Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, day, hour, minute, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                false
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun loadBitmap(context: Context, uriString: String, maxSize: Int): Bitmap? = try {
    val uri = Uri.parse(uriString)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
} catch (e: Exception) {
    null
}

@Composable
fun ScreenshotImage(
    uriString: String,
    modifier: Modifier = Modifier,
    maxSize: Int = 300,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) { loadBitmap(context, uriString, maxSize) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Screenshot",
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No preview", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ListScreen(
    savedItems: List<SavedItem>,
    monitoring: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    onOpen: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text("ScreenSort", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-save screenshots", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (monitoring) "On, works in the background" else "Paused",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = monitoring, onCheckedChange = onToggleMonitoring)
        }
        Spacer(Modifier.height(8.dp))
        Text("${savedItems.size} saved", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        if (savedItems.isEmpty()) {
            Text("Nothing saved yet. Take a screenshot and it will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedItems, key = { it.id }) { item ->
                    ItemCard(item, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: SavedItem, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item.id) }
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            ScreenshotImage(item.imageUri, Modifier.size(80.dp), maxSize = 200)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(item.savedAt), style = MaterialTheme.typography.labelMedium)
                val who = listOfNotNull(item.sourceApp, item.authorName).joinToString(" · ")
                if (who.isNotBlank()) {
                    Text(who, style = MaterialTheme.typography.labelMedium)
                }
                if (item.remindAt != null) {
                    Text(
                        "Reminder: ${formatDate(item.remindAt)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (item.note.isNotBlank()) {
                    Text(
                        item.note,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    item.ocrText.ifBlank { "(no text found)" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.link != null) {
                    Text(
                        item.link,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    item: SavedItem,
    onBack: () -> Unit,
    onSave: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onSetReminder: (Long?) -> Unit
) {
    val context = LocalContext.current
    var note by remember(item.id) { mutableStateOf(item.note) }
    var link by remember(item.id) { mutableStateOf(item.link ?: "") }
    var author by remember(item.id) { mutableStateOf(item.authorName ?: "") }
    var source by remember(item.id) { mutableStateOf(item.sourceApp ?: "") }
    var postDate by remember(item.id) { mutableStateOf(item.postDate ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text("Saved ${formatDate(item.savedAt)}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))

        ScreenshotImage(
            item.imageUri,
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            maxSize = 1200,
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(16.dp))

        Text("Reminder", style = MaterialTheme.typography.titleMedium)
        Text(
            if (item.remindAt != null) "Set for ${formatDate(item.remindAt)}"
            else "No reminder set"
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSetReminder(System.currentTimeMillis() + 60_000L) },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("In 1 min (test)") }
            OutlinedButton(
                onClick = { onSetReminder(System.currentTimeMillis() + 3_600_000L) },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("In 1 hour") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSetReminder(tomorrowAt(9)) },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Tomorrow 9 AM") }
            Button(
                onClick = { pickDateTime(context) { time -> onSetReminder(time) } },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Pick date & time") }
        }
        if (item.remindAt != null) {
            TextButton(onClick = { onSetReminder(null) }) { Text("Clear reminder") }
        }
        if (!ReminderScheduler.canScheduleExact(context)) {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }) { Text("Allow exact reminders (recommended)") }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("Note") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = link, onValueChange = { link = it },
            label = { Text("Link") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = author, onValueChange = { author = it },
            label = { Text("Posted by") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = source, onValueChange = { source = it },
            label = { Text("Source app") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = postDate, onValueChange = { postDate = it },
            label = { Text("Post date") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val info = LinkParser.parse(link.trim())
                onSave(
                    item.copy(
                        note = note.trim(),
                        link = link.trim().ifBlank { null },
                        authorName = author.trim().ifBlank { info.handle },
                        sourceApp = source.trim().ifBlank { info.sourceApp },
                        postDate = postDate.trim().ifBlank { null }
                    )
                )
                onBack()
            }) { Text("Save changes") }

            OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }

        Spacer(Modifier.height(24.dp))
        Text("Text found", style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(item.ocrText.ifBlank { "(no text found)" })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = {
                Text("This removes it from ScreenSort. The original screenshot stays in your gallery.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(item)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}