package com.eventtracker

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AnnouncementSuggestions(
    val name: String = "",
    val eventDate: String = "",
    val eventTime: String = "",
    val registrationDeadline: String = "",
    val deadlineTime: String = "",
    val link: String = "",
)

/** Offline, heuristic extraction only. All returned values are suggestions for the user to review. */
internal object AnnouncementParser {
    private val urlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val youtubeUrlRegex = Regex("(?i)^https?://(?:www\\.)?(?:youtube\\.com|youtu\\.be|m\\.youtube\\.com|music\\.youtube\\.com|youtube-nocookie\\.com)(?:[/#?]|$)")
    private val registrationLinkContext = Regex("(?i)\\b(register|registration|sign\\s*up|apply|application|form|enrol|enroll)\\b")
    private const val MONTHS = "January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec"
    private val dayMonthRegex = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($MONTHS)\\.?[,]?\\s*(\\d{4})?\\b")
    private val monthDayRegex = Regex("(?i)\\b($MONTHS)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?[,]?\\s*(\\d{4})?\\b")
    private val numericDateRegex = Regex("\\b(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2,4}))?\\b")
    private val timeRegex = Regex(
        "(?i)\\b(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b|\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b",
    )
    private val deadlineWords = Regex("(?i)\\b(registration|register|deadline|closes?|closing|last date|last day|apply|application|submission|submit|due)\\b")
    private val eventWords = Regex("(?i)\\b(event|held|happens|takes place|starts?|fest(?:ival)?|hackathon|conference|workshop|seminar|webinar|competition|ceremony)\\b")
    private val dateDisplayFormat = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.getDefault())

    private enum class Kind { EVENT, DEADLINE, UNKNOWN }
    private data class Candidate(
        val date: LocalDate,
        val lineIndex: Int,
        val dateStart: Int,
        val kind: Kind,
        val time: String,
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): AnnouncementSuggestions {
        if (text.isBlank()) return AnnouncementSuggestions()

        val lines = text.lines()
        val candidates = lines.flatMapIndexed { lineIndex, line ->
            findDates(line, today, lineIndex).map { candidate ->
                val neighborKind = labelKind(lines.getOrNull(lineIndex - 1)) ?: labelKind(lines.getOrNull(lineIndex + 1))
                val kind = candidate.kind.takeIf { it != Kind.UNKNOWN } ?: neighborKind ?: Kind.UNKNOWN
                val neighborTime = if (candidate.time.isNotBlank()) candidate.time else sequenceOf(lineIndex + 1, lineIndex - 1)
                    .mapNotNull { adjacentIndex ->
                        val adjacentLine = lines.getOrNull(adjacentIndex) ?: return@mapNotNull null
                        if (containsDate(adjacentLine)) return@mapNotNull null
                        timeRegex.find(adjacentLine)?.let(::parseTime)
                    }
                    .firstOrNull()
                    .orEmpty()
                candidate.copy(kind = kind, time = neighborTime)
            }
        }
        val event = candidates.firstOrNull { it.kind == Kind.EVENT }
        val deadline = candidates.firstOrNull { it.kind == Kind.DEADLINE }
        val unknown = candidates.filter { it.kind == Kind.UNKNOWN }
        val selectedEvent = event ?: unknown.firstOrNull()
        val selectedDeadline = deadline ?: unknown.firstOrNull { it != selectedEvent }

        val links = urlRegex.findAll(text).map { match ->
            match.value.trimEnd('.', ',', ';', ':', ')', ']', '!') to match.range.first
        }.filter { (link, _) -> link.isNotBlank() && !youtubeUrlRegex.containsMatchIn(link) }.toList()
        val link = links.firstOrNull { (_, position) ->
            val lineStart = text.lastIndexOf('\n', position).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', position).let { if (it < 0) text.length else it }
            registrationLinkContext.containsMatchIn(text.substring(lineStart, lineEnd))
        }?.first ?: links.firstOrNull()?.first.orEmpty()
        val title = lines.asSequence()
            .map { it.replace(urlRegex, "").trim().trim('*', '_', '-', ' ', '\t') }
            .firstOrNull { it.isNotBlank() }
            ?.take(100)
            .orEmpty()

        return AnnouncementSuggestions(
            name = title,
            eventDate = selectedEvent?.date?.format(dateDisplayFormat).orEmpty(),
            eventTime = selectedEvent?.time.orEmpty(),
            registrationDeadline = selectedDeadline?.date?.format(dateDisplayFormat).orEmpty(),
            deadlineTime = selectedDeadline?.time.orEmpty(),
            link = link,
        )
    }

    private fun findDates(line: String, today: LocalDate, lineIndex: Int): List<Candidate> {
        val urlRanges = urlRegex.findAll(line).map { it.range }.toList()
        val found = mutableListOf<Candidate>()

        fun addMatch(match: MatchResult, date: LocalDate?) {
            if (date == null || urlRanges.any { match.range.first in it }) return
            val localTime = timeRegex.findAll(line)
                .minByOrNull { kotlin.math.abs(it.range.first - match.range.first) }
                ?.let(::parseTime)
                .orEmpty()
            found += Candidate(date, lineIndex, match.range.first, classify(line, match.range.first), localTime)
        }

        dayMonthRegex.findAll(line).forEach { match ->
            addMatch(match, buildDate(match.groupValues[1], match.groupValues[2], match.groupValues[3], today))
        }
        monthDayRegex.findAll(line).forEach { match ->
            addMatch(match, buildDate(match.groupValues[2], match.groupValues[1], match.groupValues[3], today))
        }
        numericDateRegex.findAll(line).forEach { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@forEach
            val second = match.groupValues[2].toIntOrNull() ?: return@forEach
            val (day, month) = when {
                first > 12 && second <= 12 -> first to second
                second > 12 && first <= 12 -> second to first
                else -> first to second // Ambiguous numeric dates default to day/month; the form remains editable.
            }
            addMatch(match, buildDate(day.toString(), month.toString(), match.groupValues[3], today))
        }
        return found.distinctBy { it.dateStart }.sortedBy { it.dateStart }
    }

    private fun buildDate(dayText: String, monthText: String, yearText: String, today: LocalDate): LocalDate? {
        val day = dayText.toIntOrNull() ?: return null
        val month = monthNumber(monthText) ?: return null
        val year = when (yearText.length) {
            0 -> today.year
            2 -> 2000 + (yearText.toIntOrNull() ?: return null)
            3 -> return null
            4 -> yearText.toIntOrNull() ?: return null
            else -> return null
        }
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun monthNumber(value: String): Int? {
        value.toIntOrNull()?.let { month -> return month.takeIf { it in 1..12 } }
        return when (value.lowercase(Locale.ROOT).take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> null
        }
    }

    private fun classify(line: String, dateStart: Int): Kind {
        val deadlineDistance = deadlineWords.findAll(line)
            .map { kotlin.math.abs(it.range.first - dateStart) }
            .minOrNull()
        val eventDistance = eventWords.findAll(line)
            .map { kotlin.math.abs(it.range.first - dateStart) }
            .minOrNull()
        return when {
            deadlineDistance != null && (eventDistance == null || deadlineDistance < eventDistance) -> Kind.DEADLINE
            eventDistance != null && (deadlineDistance == null || eventDistance < deadlineDistance) -> Kind.EVENT
            else -> Kind.UNKNOWN
        }
    }

    private fun labelKind(line: String?): Kind? {
        if (line == null) return null
        val deadline = deadlineWords.containsMatchIn(line)
        val event = eventWords.containsMatchIn(line)
        return when {
            deadline -> Kind.DEADLINE
            event -> Kind.EVENT
            else -> null
        }
    }

    private fun containsDate(line: String): Boolean =
        dayMonthRegex.containsMatchIn(line) || monthDayRegex.containsMatchIn(line) || numericDateRegex.containsMatchIn(line)

    private fun parseTime(match: MatchResult): String? {
        val hour: Int
        val minute: Int
        if (match.groupValues[1].isNotEmpty()) {
            hour = match.groupValues[1].toIntOrNull() ?: return null
            minute = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
            if (hour !in 1..12) return null
            val meridian = match.groupValues[3].replace(".", "").lowercase(Locale.ROOT)
            val hour24 = (hour % 12) + if (meridian == "pm") 12 else 0
            return "%02d:%02d".format(Locale.ROOT, hour24, minute)
        }
        hour = match.groupValues[4].toIntOrNull() ?: return null
        minute = match.groupValues[5].toIntOrNull() ?: return null
        return "%02d:%02d".format(Locale.ROOT, hour, minute)
    }
}
