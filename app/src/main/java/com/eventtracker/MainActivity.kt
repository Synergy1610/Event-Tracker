package com.eventtracker

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val Ink = Color(0xFF092328)
private val DeepTeal = Color(0xFF12544F)
private val Green = Color(0xFF2A835F)
private val Sage = Color(0xFF8BBB92)
private val Paper = Color(0xFFF7F9F6)
private val DateFormat = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.getDefault())
private val DisplayDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

data class TrackedEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val eventDate: String = "",
    val eventTime: String = "",
    val registrationDeadline: String = "",
    val deadlineTime: String = "",
    val link: String = "",
    val notes: String = "",
    val sourceText: String = "",
)

private enum class Screen { HOME, EVENTS }
private enum class EventsTab { LIST, CALENDAR }
private enum class EventEditorStep { PASTE, DETAILS, NOTES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val sharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""
        setContent { EventTrackerApp(sharedText) }
    }
}

@Composable
private fun EventTrackerApp(sharedText: String) {
    val context = LocalContext.current
    val events = remember {
        mutableStateListOf<TrackedEvent>().apply { addAll(loadEvents(context)) }
    }
    var notificationsEnabled by remember {
        mutableStateOf(context.getSharedPreferences("events", Context.MODE_PRIVATE).getBoolean("notificationsEnabled", false))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsEnabled = granted
        context.getSharedPreferences("events", Context.MODE_PRIVATE).edit()
            .putBoolean("notificationsEnabled", granted).apply()
        Toast.makeText(
            context,
            if (granted) "Event reminders enabled." else "Notification permission is needed for reminders.",
            Toast.LENGTH_SHORT,
        ).show()
    }
    val onNotificationsChange: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            notificationsEnabled = false
            context.getSharedPreferences("events", Context.MODE_PRIVATE).edit()
                .putBoolean("notificationsEnabled", false).apply()
            Toast.makeText(context, "Event reminders turned off.", Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, "Enable TreeTrack notifications in Android Settings.", Toast.LENGTH_SHORT).show()
        } else {
            notificationsEnabled = true
            context.getSharedPreferences("events", Context.MODE_PRIVATE).edit()
                .putBoolean("notificationsEnabled", true).apply()
            Toast.makeText(context, "Event reminders enabled.", Toast.LENGTH_SHORT).show()
        }
    }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showEditor by remember(sharedText) { mutableStateOf(sharedText.isNotBlank()) }
    var editingEvent by remember { mutableStateOf<TrackedEvent?>(null) }
    var selectedEvent by remember { mutableStateOf<TrackedEvent?>(null) }

    LaunchedEffect(events.toList()) {
        saveEvents(context, events)
        EventReminderScheduler.sync(context, events.toList(), notificationsEnabled)
    }
    BackHandler(enabled = screen == Screen.EVENTS) { screen = Screen.HOME }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DeepTeal,
            onPrimary = Color.White,
            primaryContainer = Sage,
            onPrimaryContainer = Ink,
            secondary = Green,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDCEBE0),
            onSecondaryContainer = Ink,
            tertiary = Sage,
            onTertiary = Ink,
            tertiaryContainer = Color(0xFFDCEBE0),
            onTertiaryContainer = Ink,
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE7EEE8),
            onSurfaceVariant = DeepTeal,
            surfaceDim = Color(0xFFE5ECE6),
            surfaceBright = Paper,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF4F7F3),
            surfaceContainer = Color(0xFFEEF4EF),
            surfaceContainerHigh = Color(0xFFE7EEE8),
            surfaceContainerHighest = Color(0xFFDCEBE0),
            outline = DeepTeal,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                if (screen == Screen.HOME) {
                    HomeScreen(
                        eventCount = events.size,
                        nextEvent = events.sortedBy { parseDate(it.eventDate) }.firstOrNull { parseDate(it.eventDate) != null },
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsChange = onNotificationsChange,
                        onViewEvents = { screen = Screen.EVENTS },
                        onAddEvent = { editingEvent = null; showEditor = true },
                    )
                } else {
                    EventsScreen(
                        events = events,
                        onBack = { screen = Screen.HOME },
                        onEventClick = { selectedEvent = it },
                    )
                }
            }
        }
        if (showEditor) {
            EventEditor(
                initial = editingEvent,
                incomingText = sharedText,
                onDismiss = { showEditor = false },
                onSave = { event ->
                    events.removeAll { it.id == event.id }
                    events.add(event)
                    showEditor = false
                },
            )
        }
        selectedEvent?.let { event ->
            EventDetailsDialog(
                event = event,
                onDismiss = { selectedEvent = null },
                onEdit = {
                    selectedEvent = null
                    editingEvent = event
                    showEditor = true
                },
                onDelete = {
                    EventReminderScheduler.cancel(context, event.id)
                    events.removeAll { it.id == event.id }
                    selectedEvent = null
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    eventCount: Int,
    nextEvent: TrackedEvent?,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onViewEvents: () -> Unit,
    onAddEvent: () -> Unit,
) {
    var showReminderInfo by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Sage),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "TreeTrack logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your campus,\nat a glance.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
            TreeIllustration()
        }
        Spacer(Modifier.height(10.dp))
        Text("Keep every event and registration deadline in one place.", color = DeepTeal, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DeepTeal),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("YOUR EVENTS", color = Sage, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$eventCount Events saved.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (nextEvent != null) {
                    Spacer(Modifier.height(18.dp))
                    HorizontalRule(Color.White.copy(alpha = .2f))
                    Spacer(Modifier.height(14.dp))
                    Text("UP NEXT", color = Sage, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(nextEvent.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(formatDate(nextEvent.eventDate), color = Color.White.copy(alpha = .8f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onViewEvents,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green),
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("View events", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddEvent,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = DeepTeal)
            Spacer(Modifier.width(10.dp))
            Text("Add event", color = DeepTeal, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEBE0)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Event reminders", color = DeepTeal)
                    IconButton(onClick = { showReminderInfo = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Reminder schedule information",
                            tint = DeepTeal.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChange)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showReminderInfo) {
        AlertDialog(
            onDismissRequest = { showReminderInfo = false },
            title = { Text("Reminder schedule", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "With reminders enabled, saved event dates and registration deadlines get a reminder at 9:00 AM the day before and 7:00 AM on the date.",
                    color = Ink,
                )
            },
            confirmButton = { TextButton(onClick = { showReminderInfo = false }) { Text("Got it") } },
            containerColor = Paper,
        )
    }
}

@Composable
private fun TreeIllustration() {
    Canvas(Modifier.size(width = 100.dp, height = 88.dp)) {
        val unit = size.width / 100f
        drawRoundRect(
            color = Sage,
            topLeft = Offset(8f * unit, 80f * unit),
            size = Size(84f * unit, 5f * unit),
            cornerRadius = CornerRadius(3f * unit),
        )
        drawRoundRect(
            color = DeepTeal,
            topLeft = Offset(23f * unit, 53f * unit),
            size = Size(7f * unit, 28f * unit),
            cornerRadius = CornerRadius(3f * unit),
        )
        drawCircle(Green, 19f * unit, Offset(26f * unit, 41f * unit))
        drawCircle(DeepTeal, 13f * unit, Offset(14f * unit, 49f * unit))
        drawCircle(Sage, 13f * unit, Offset(38f * unit, 49f * unit))
        drawRoundRect(
            color = Green,
            topLeft = Offset(68f * unit, 65f * unit),
            size = Size(5f * unit, 17f * unit),
            cornerRadius = CornerRadius(2f * unit),
        )
        drawCircle(Sage, 13f * unit, Offset(70f * unit, 56f * unit))
        drawCircle(DeepTeal, 9f * unit, Offset(61f * unit, 62f * unit))
        drawCircle(Green, 9f * unit, Offset(79f * unit, 62f * unit))
    }
}

@Composable
private fun EventsScreen(events: List<TrackedEvent>, onBack: () -> Unit, onEventClick: (TrackedEvent) -> Unit) {
    var selectedTab by remember { mutableStateOf(EventsTab.LIST) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            Text("Your events", style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabChip("List", selectedTab == EventsTab.LIST) { selectedTab = EventsTab.LIST }
            TabChip("Calendar", selectedTab == EventsTab.CALENDAR) { selectedTab = EventsTab.CALENDAR }
        }
        Spacer(Modifier.height(16.dp))
        when (selectedTab) {
            EventsTab.LIST -> EventList(events, onEventClick, Modifier.weight(1f))
            EventsTab.CALENDAR -> EventCalendar(events, onEventClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) DeepTeal else Color(0xFFE7EEE8),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            color = if (selected) Color.White else DeepTeal, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EventList(events: List<TrackedEvent>, onEventClick: (TrackedEvent) -> Unit, modifier: Modifier = Modifier) {
    if (events.isEmpty()) {
        EmptyEvents(modifier)
    } else {
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(events.sortedWith(compareBy<TrackedEvent> { parseDate(it.registrationDeadline) ?: LocalDate.MAX }.thenBy { it.name }), key = { it.id }) {
                EventCard(it, onClick = { onEventClick(it) })
            }
        }
    }
}

@Composable
private fun EmptyEvents(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text("No events saved yet. Add an event to see it here.", color = DeepTeal, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun EventCard(event: TrackedEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.name, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
            if (event.eventDate.isNotBlank()) DateLine("Event", event.eventDate, event.eventTime, Green)
            if (event.registrationDeadline.isNotBlank()) DateLine("Register by", event.registrationDeadline, event.deadlineTime, DeepTeal)
            if (event.link.isNotBlank()) RegistrationLinkActions(event.link)
            if (event.notes.isNotBlank()) Text(event.notes, color = Ink.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RegistrationLinkActions(link: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val openLink: () -> Unit = {
        val trimmed = link.trim()
        val target = if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = Uri.parse(target)
        if (uri.host.isNullOrBlank()) {
            Toast.makeText(context, "This registration link is not valid.", Toast.LENGTH_SHORT).show()
        } else {
            runCatching { uriHandler.openUri(target) }
                .onFailure { Toast.makeText(context, "Could not open the registration link.", Toast.LENGTH_SHORT).show() }
        }
    }

    val copyLink: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Registration link", link))
        Toast.makeText(context, "Registration link copied.", Toast.LENGTH_SHORT).show()
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = link,
            modifier = Modifier.weight(1f).combinedClickable(onClick = openLink, onLongClick = copyLink),
            color = Green,
            style = MaterialTheme.typography.bodySmall,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
        )
    }
}

@Composable
private fun EventDetailsDialog(
    event: TrackedEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(event.id) { mutableStateOf(false) }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete event?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("${event.name} will be permanently removed from your saved events.", color = Ink) },
            confirmButton = {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            containerColor = Paper,
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(event.name, color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (event.eventDate.isNotBlank()) {
                        DetailSection("Event date", "${formatDate(event.eventDate)} · ${event.eventTime.takeIf { it.isNotBlank() }?.let(::formatTime) ?: "All day"}")
                    }
                    if (event.registrationDeadline.isNotBlank()) {
                        DetailSection("Registration deadline", "${formatDate(event.registrationDeadline)} · ${event.deadlineTime.takeIf { it.isNotBlank() }?.let(::formatTime) ?: "All day"}")
                    }
                    if (event.link.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Registration link", color = DeepTeal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            RegistrationLinkActions(event.link)
                        }
                    }
                    if (event.notes.isNotBlank()) DetailSection("Notes", event.notes)
                    if (event.sourceText.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Original WhatsApp message", color = DeepTeal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color(0xFFE7EEE8), shape = RoundedCornerShape(12.dp)) {
                                SelectionContainer {
                                    Text(event.sourceText, modifier = Modifier.padding(12.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            containerColor = Paper,
        )
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DeepTeal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(value, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DateLine(label: String, date: String, time: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text("$label: ${formatDate(date)}", color = Ink, style = MaterialTheme.typography.bodyMedium)
        if (time.isNotBlank()) Text(" · ${formatTime(time)}", color = Ink.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
        else Text(" · All day", color = Ink.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventCalendar(events: List<TrackedEvent>, onEventClick: (TrackedEvent) -> Unit, modifier: Modifier = Modifier) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value % 7
    val days: List<LocalDate?> = List(firstDayOffset) { null } +
        (1..displayedMonth.lengthOfMonth()).map { displayedMonth.atDay(it) }
    val selectedItems = entriesForDate(events, selectedDate)

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                displayedMonth = displayedMonth.minusMonths(1)
                selectedDate = displayedMonth.atDay(1)
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = DeepTeal)
            }
            Text(
                displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                modifier = Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = {
                displayedMonth = displayedMonth.plusMonths(1)
                selectedDate = displayedMonth.atDay(1)
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = DeepTeal)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, modifier = Modifier.weight(1f).padding(vertical = 6.dp), color = DeepTeal,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(250.dp),
            userScrollEnabled = false,
        ) {
            items(days) { date ->
                if (date == null) {
                    Box(Modifier.aspectRatio(1f))
                } else {
                    val hasEvent = events.any { parseDate(it.eventDate) == date }
                    val hasDeadline = events.any { parseDate(it.registrationDeadline) == date }
                    CalendarDay(
                        date = date,
                        selected = date == selectedDate,
                        isToday = date == LocalDate.now(),
                        hasEvent = hasEvent,
                        hasDeadline = hasDeadline,
                        onClick = { selectedDate = date },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(Green, "Event")
            LegendDot(Ink, "Registration deadline", isDeadline = true)
        }
        Spacer(Modifier.height(16.dp))
        Text(selectedDate.format(DisplayDateFormat), style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (selectedItems.isEmpty()) {
            Text("Nothing scheduled for this day.", color = Ink.copy(alpha = .65f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(selectedItems, key = { it.first.id + it.second }) { (event, kind) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEventClick(event) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(if (kind == "Event") CircleShape else RoundedCornerShape(2.dp))
                                .background(if (kind == "Event") Green else Ink))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(event.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                val time = if (kind == "Event") event.eventTime else event.deadlineTime
                                Text("$kind · ${time.takeIf { it.isNotBlank() }?.let(::formatTime) ?: "All day"}", color = DeepTeal, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, selected: Boolean, isToday: Boolean, hasEvent: Boolean, hasDeadline: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)
            .clip(RoundedCornerShape(12.dp)).background(if (selected) DeepTeal else Color.Transparent)
            .then(if (isToday) Modifier.border(1.dp, if (selected) Sage else Green, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = if (selected) Color.White else Ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(7.dp)) {
            if (hasEvent) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Sage else Green))
            if (hasDeadline) Box(Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(if (selected) Color.White else Ink))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, isDeadline: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(if (isDeadline) RoundedCornerShape(2.dp) else CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Ink.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(
    initial: TrackedEvent?,
    incomingText: String,
    onDismiss: () -> Unit,
    onSave: (TrackedEvent) -> Unit,
) {
    val suggested = remember(initial, incomingText) {
        initial ?: suggestedEvent(incomingText)
    }
    var name by remember(suggested.id) { mutableStateOf(suggested.name) }
    var eventDate by remember(suggested.id) { mutableStateOf(suggested.eventDate) }
    var eventTime by remember(suggested.id) { mutableStateOf(suggested.eventTime) }
    var deadline by remember(suggested.id) { mutableStateOf(suggested.registrationDeadline) }
    var deadlineTime by remember(suggested.id) { mutableStateOf(suggested.deadlineTime) }
    var link by remember(suggested.id) { mutableStateOf(suggested.link) }
    var notes by remember(suggested.id) { mutableStateOf(suggested.notes) }
    var notesDraft by remember(suggested.id) { mutableStateOf(suggested.notes) }
    var source by remember(suggested.id) { mutableStateOf(suggested.sourceText) }
    var clipboardMessage by remember { mutableStateOf("") }
    var step by remember(suggested.id) {
        mutableStateOf(if (initial == null && incomingText.isBlank()) EventEditorStep.PASTE else EventEditorStep.DETAILS)
    }
    val context = LocalContext.current
    val pasteAnnouncement: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val pastedText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        if (pastedText.isBlank()) {
            clipboardMessage = "Copy the WhatsApp message first, then tap here."
        } else {
            source = pastedText
            val suggestions = suggestedEvent(pastedText)
            name = suggestions.name
            link = suggestions.link
            eventDate = suggestions.eventDate
            eventTime = suggestions.eventTime
            deadline = suggestions.registrationDeadline
            deadlineTime = suggestions.deadlineTime
            clipboardMessage = "Message pasted. Review the detected details before saving."
            step = EventEditorStep.DETAILS
        }
    }

    if (step == EventEditorStep.PASTE) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.widthIn(min = 280.dp, max = 340.dp),
                shape = RoundedCornerShape(28.dp),
                color = Paper,
                tonalElevation = 6.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Add an event", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepTeal)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "In WhatsApp, long-press an event announcement and tap Copy.",
                            color = DeepTeal,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = pasteAnnouncement, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Paste WhatsApp message")
                    }
                    if (clipboardMessage.isNotBlank()) {
                        Text(clipboardMessage, color = DeepTeal, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            }
        }
    } else AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    EventEditorStep.PASTE -> "Add an event"
                    EventEditorStep.DETAILS -> if (initial == null) "Review event details" else "Edit event"
                    EventEditorStep.NOTES -> "Event notes"
                },
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when (step) {
                EventEditorStep.PASTE -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepTeal)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "In WhatsApp, long-press an event announcement and tap Copy.",
                            color = DeepTeal,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = pasteAnnouncement,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Paste WhatsApp message")
                    }
                    if (clipboardMessage.isNotBlank()) {
                        Text(clipboardMessage, color = DeepTeal, style = MaterialTheme.typography.bodySmall)
                    }
                }

                EventEditorStep.DETAILS -> Column(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (source.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Original WhatsApp message", color = DeepTeal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color(0xFFE7EEE8), shape = RoundedCornerShape(12.dp)) {
                                SelectionContainer {
                                    Text(source, modifier = Modifier.padding(10.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Event name") }, singleLine = true)
                    DateInput("Event date · optional", eventDate, { eventDate = it }, eventTime, { eventTime = it })
                    DateInput("Registration deadline · optional", deadline, { deadline = it }, deadlineTime, { deadlineTime = it })
                    OutlinedTextField(link, { link = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Registration link") }, singleLine = true)
                    OutlinedButton(
                        onClick = { notesDraft = notes; step = EventEditorStep.NOTES },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (notes.isBlank()) "Add notes" else "Edit notes", color = DeepTeal)
                    }
                    Text("Leave a time blank for an all-day date.", color = DeepTeal, style = MaterialTheme.typography.bodySmall)
                }

                EventEditorStep.NOTES -> Column(
                    Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Add any extra details you want to remember.", color = DeepTeal, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text("Notes") },
                        placeholder = { Text("Other useful details") },
                        minLines = 5,
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                EventEditorStep.PASTE -> TextButton(onClick = onDismiss) { Text("Cancel") }
                EventEditorStep.DETAILS -> Button(
                    onClick = {
                        onSave(suggested.copy(
                            name = name.trim(), eventDate = normalizeDate(eventDate), eventTime = eventTime,
                            registrationDeadline = normalizeDate(deadline), deadlineTime = deadlineTime,
                            link = link.trim(), notes = notes.trim(), sourceText = source,
                        ))
                    },
                    enabled = name.isNotBlank() && (eventDate.isBlank() || parseDate(eventDate) != null) &&
                        (deadline.isBlank() || parseDate(deadline) != null),
                ) { Text("Save event") }
                EventEditorStep.NOTES -> Button(onClick = { notes = notesDraft; step = EventEditorStep.DETAILS }) {
                    Text("Save notes")
                }
            }
        },
        dismissButton = {
            when (step) {
                EventEditorStep.PASTE -> Unit
                EventEditorStep.DETAILS -> TextButton(onClick = onDismiss) { Text("Cancel") }
                EventEditorStep.NOTES -> TextButton(onClick = { step = EventEditorStep.DETAILS }) { Text("Back") }
            }
        },
        containerColor = Paper,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInput(label: String, date: String, onDateChange: (String) -> Unit, time: String, onTimeChange: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = date,
            onValueChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("$label (DD/MM/YYYY)") },
            placeholder = { Text("e.g. 24/10/2026") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Choose $label", tint = DeepTeal)
                }
            },
            supportingText = if (date.isNotBlank() && parseDate(date) == null) ({ Text("Enter a valid date or choose it from the calendar.") }) else null,
        )
        OutlinedButton(onClick = { showTimePicker = true }, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = DeepTeal)
            Spacer(Modifier.width(8.dp))
            Text(if (time.isBlank()) "Add time (optional)" else formatTime(time), color = DeepTeal)
        }
        if (time.isNotBlank()) TextButton(onClick = { onTimeChange("") }) { Text("Clear time · All day") }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = parseDate(date)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateFormat))
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showTimePicker) {
        TimePickerDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { onTimeChange(it); showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialTime: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = initialTime.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose time") },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    periodSelectorBorderColor = DeepTeal,
                    periodSelectorSelectedContainerColor = Color(0xFFDCEBE0),
                    periodSelectorUnselectedContainerColor = Color.White,
                    periodSelectorSelectedContentColor = Ink,
                    periodSelectorUnselectedContentColor = DeepTeal,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Paper,
    )
}

@Composable
private fun HorizontalRule(color: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

private fun parseDate(value: String): LocalDate? = runCatching {
    LocalDate.parse(value.trim(), DateFormat)
}.getOrNull() ?: runCatching { LocalDate.parse(value.trim()) }.getOrNull()

private fun normalizeDate(value: String): String = parseDate(value)?.toString().orEmpty()

private fun formatDate(value: String): String = parseDate(value)?.format(DisplayDateFormat) ?: value

private fun formatTime(value: String): String = runCatching {
    java.time.LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}.getOrDefault(value)

private fun entriesForDate(events: List<TrackedEvent>, date: LocalDate): List<Pair<TrackedEvent, String>> = buildList {
    events.forEach { event ->
        if (parseDate(event.eventDate) == date) add(event to "Event")
        if (parseDate(event.registrationDeadline) == date) add(event to "Registration deadline")
    }
}

private fun suggestedEvent(text: String): TrackedEvent {
    if (text.isBlank()) return TrackedEvent(name = "")
    val suggestions = AnnouncementParser.parse(text)
    return TrackedEvent(
        name = suggestions.name,
        eventDate = suggestions.eventDate,
        eventTime = suggestions.eventTime,
        registrationDeadline = suggestions.registrationDeadline,
        deadlineTime = suggestions.deadlineTime,
        link = suggestions.link,
        sourceText = text,
    )
}

private fun loadEvents(context: Context): List<TrackedEvent> {
    val json = context.getSharedPreferences("events", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
    return runCatching {
        val array = org.json.JSONArray(json)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TrackedEvent(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = item.optString("name"), eventDate = item.optString("eventDate"),
                eventTime = item.optString("eventTime"),
                registrationDeadline = item.optString("registrationDeadline"),
                deadlineTime = item.optString("deadlineTime"), link = item.optString("link"),
                notes = item.optString("notes"), sourceText = item.optString("sourceText"),
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveEvents(context: Context, events: List<TrackedEvent>) {
    val array = org.json.JSONArray()
    events.forEach { event ->
        array.put(org.json.JSONObject().apply {
            put("id", event.id)
            put("name", event.name)
            put("eventDate", event.eventDate)
            put("eventTime", event.eventTime)
            put("registrationDeadline", event.registrationDeadline)
            put("deadlineTime", event.deadlineTime)
            put("link", event.link)
            put("notes", event.notes)
            put("sourceText", event.sourceText)
        })
    }
    context.getSharedPreferences("events", Context.MODE_PRIVATE).edit().putString("items", array.toString()).apply()
}
