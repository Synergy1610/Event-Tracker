package com.eventtracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object EventReminderScheduler {
    private const val KEY_EVENT_ID = "event_id"
    private const val KEY_EVENT_NAME = "event_name"
    private const val KEY_REMINDER_KIND = "reminder_kind"
    private const val KEY_REMINDER_DAY = "reminder_day"
    private const val CHANNEL_ID = "event_reminders"

    fun sync(context: Context, events: List<TrackedEvent>, notificationsEnabled: Boolean) {
        events.forEach { event ->
            scheduleReminder(
                context,
                event,
                kind = "event",
                enabled = notificationsEnabled,
                dateValue = event.eventDate,
            )
            scheduleReminder(
                context,
                event,
                kind = "deadline",
                enabled = notificationsEnabled,
                dateValue = event.registrationDeadline,
            )
        }
    }

    fun cancel(context: Context, eventId: String) {
        val workManager = WorkManager.getInstance(context)
        listOf("event", "deadline").forEach { kind ->
            workManager.cancelUniqueWork("event-tracker:$eventId:$kind") // Cancel reminders from the previous scheduler.
            workManager.cancelUniqueWork(uniqueName(eventId, kind, "before"))
            workManager.cancelUniqueWork(uniqueName(eventId, kind, "day"))
        }
    }

    private fun scheduleReminder(context: Context, event: TrackedEvent, kind: String, enabled: Boolean, dateValue: String) {
        val workManager = WorkManager.getInstance(context)
        val date = parseDate(dateValue)
        if (!enabled || date == null) {
            cancelKind(workManager, event.id, kind)
            return
        }

        listOf("before" to date.minusDays(1).atTime(LocalTime.of(9, 0)), "day" to date.atTime(LocalTime.of(7, 0)))
            .forEach { (reminderDay, reminderDateTime) ->
                val name = uniqueName(event.id, kind, reminderDay)
                val reminderAt = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant()
                val delay = Duration.between(java.time.Instant.now(), reminderAt).toMillis()
                if (delay <= 0L) {
                    workManager.cancelUniqueWork(name)
                } else {
                    val input = Data.Builder()
                        .putString(KEY_EVENT_ID, event.id)
                        .putString(KEY_EVENT_NAME, event.name)
                        .putString(KEY_REMINDER_KIND, kind)
                        .putString(KEY_REMINDER_DAY, reminderDay)
                        .build()
                    val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                        .setInputData(input)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(name)
                        .build()
                    workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
                }
            }
    }

    private fun cancelKind(workManager: WorkManager, eventId: String, kind: String) {
        workManager.cancelUniqueWork("event-tracker:$eventId:$kind")
        workManager.cancelUniqueWork(uniqueName(eventId, kind, "before"))
        workManager.cancelUniqueWork(uniqueName(eventId, kind, "day"))
    }

    private fun uniqueName(eventId: String, kind: String, reminderDay: String) = "event-tracker:$eventId:$kind:$reminderDay"

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
        ?: runCatching { LocalDate.parse(value, DateTimeFormatter.ofPattern("d/M/uuuu", Locale.getDefault())) }.getOrNull()

    internal fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Reminders for saved event dates and registration deadlines" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    internal fun showReminder(context: Context, eventId: String, eventName: String, kind: String, reminderDay: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        createNotificationChannel(context)
        val notificationId = "$eventId:$kind:$reminderDay".hashCode()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val subject = if (kind == "deadline") "Registration deadline" else "Event"
        val whenText = if (reminderDay == "day") "today" else "tomorrow"
        val title = "$subject $whenText"
        val message = if (kind == "deadline") "$eventName registration closes $whenText." else "$eventName is $whenText."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}

class EventReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val eventId = inputData.getString("event_id") ?: return Result.failure()
        val eventName = inputData.getString("event_name") ?: return Result.failure()
        val kind = inputData.getString("reminder_kind") ?: return Result.failure()
        val reminderDay = inputData.getString("reminder_day") ?: return Result.failure()
        EventReminderScheduler.showReminder(applicationContext, eventId, eventName, kind, reminderDay)
        return Result.success()
    }
}
