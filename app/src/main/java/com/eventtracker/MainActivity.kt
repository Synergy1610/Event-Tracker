package com.eventtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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

private data class TrackedEvent(
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
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showEditor by remember(sharedText) { mutableStateOf(sharedText.isNotBlank()) }
    var editingEvent by remember { mutableStateOf<TrackedEvent?>(null) }
    var selectedEvent by remember { mutableStateOf<TrackedEvent?>(null) }

    LaunchedEffect(events.toList()) { saveEvents(context, events) }
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
    onViewEvents: () -> Unit,
    onAddEvent: () -> Unit,
) {
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
                contentDescription = "Event Tracker tree logo",
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
                Text("$eventCount", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(if (eventCount == 1) "event saved" else "events saved", color = Color.White.copy(alpha = .8f))
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

        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEBE0)),
        ) {
            Text(
                "Tip: Copy a WhatsApp event message, then tap Add event → Paste WhatsApp message.",
                modifier = Modifier.padding(16.dp),
                color = DeepTeal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(28.dp))
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
            items(events.sortedWith(compareBy<TrackedEvent> { parseDate(it.eventDate) ?: LocalDate.MAX }.thenBy { it.name }), key = { it.id }) {
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
            if (event.link.isNotBlank()) Text(event.link, color = Green, style = MaterialTheme.typography.bodySmall)
            if (event.notes.isNotBlank()) Text(event.notes, color = Ink.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
        }
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
                    if (event.link.isNotBlank()) DetailSection("Registration link", event.link)
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
            LegendDot(DeepTeal, "Registration deadline")
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
                            Box(Modifier.size(9.dp).clip(CircleShape).background(if (kind == "Event") Green else DeepTeal))
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
private fun CalendarDay(date: LocalDate, selected: Boolean, hasEvent: Boolean, hasDeadline: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)
            .clip(RoundedCornerShape(12.dp)).background(if (selected) DeepTeal else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${date.dayOfMonth}", color = if (selected) Color.White else Ink, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(7.dp)) {
            if (hasEvent) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Sage else Green))
            if (hasDeadline) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Color.White else DeepTeal))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
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
    var source by remember(suggested.id) { mutableStateOf(suggested.sourceText) }
    var clipboardMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an event", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Event name") }, singleLine = true)
                OutlinedButton(
                    onClick = {
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
                            if (name.isBlank()) name = suggestions.name
                            if (link.isBlank()) link = suggestions.link
                            clipboardMessage = "Message pasted. Check the suggested details below."
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = DeepTeal)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste WhatsApp message", color = DeepTeal)
                }
                if (clipboardMessage.isNotBlank()) {
                    Text(clipboardMessage, color = DeepTeal, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Announcement text") },
                    placeholder = { Text("Paste the event message here, or use the button above") },
                    minLines = 3,
                )
                DateInput("Event date · optional", eventDate, { eventDate = it }, eventTime, { eventTime = it })
                DateInput("Registration deadline · optional", deadline, { deadline = it }, deadlineTime, { deadlineTime = it })
                OutlinedTextField(link, { link = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Registration link") }, singleLine = true)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    placeholder = { Text("Other useful details") },
                    minLines = 3,
                )
                Text("Leave a time blank for an all-day date.", color = DeepTeal, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
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
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            Text(if (time.isBlank()) "Add time (optional)" else time, color = DeepTeal)
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
    val lines = text.lines().map(String::trim).filter(String::isNotBlank)
    val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']') ?: ""
    val title = lines.firstOrNull { !it.contains(url) }?.take(100).orEmpty()
    return TrackedEvent(name = title, link = url, sourceText = text)
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
