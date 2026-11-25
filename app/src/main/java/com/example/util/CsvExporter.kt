package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

enum class CsvExportType(val title: String, val description: String, val fileNamePrefix: String) {
    FULL_LIST(
        title = "Full Participant List",
        description = "All registered Green House students with sports & cultural selections.",
        fileNamePrefix = "green_house_all_participants"
    ),
    SPORTS_ONLY(
        title = "Sports-Only Participant List",
        description = "Students participating in one or more Sports Week events.",
        fileNamePrefix = "green_house_sports_participants"
    ),
    CULTURAL_ONLY(
        title = "Cultural-Only Participant List",
        description = "Students participating in one or more Cultural Week events.",
        fileNamePrefix = "green_house_cultural_participants"
    ),
    EVENT_SPECIFIC(
        title = "Event-Specific Participant List",
        description = "Filtered roster for a single selected Sports or Cultural event.",
        fileNamePrefix = "green_house_event_roster"
    ),
    ROLL_SORTED(
        title = "Roll-Number-Sorted List",
        description = "Complete roster ordered alphanumerically by normalized roll number.",
        fileNamePrefix = "green_house_roll_sorted"
    )
}

object CsvExporter {
    private const val UTF8_BOM = "\uFEFF"
    private val formulaTriggerPrefixes = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Neutralizes spreadsheet formula injection (CSV Injection) and escapes RFC 4180 delimiters.
     */
    fun sanitizeCell(raw: String): String {
        val cleaned = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val trimmedStart = cleaned.trimStart()
        val safeValue = if (trimmedStart.isNotEmpty() && trimmedStart[0] in formulaTriggerPrefixes) {
            "'$cleaned"
        } else {
            cleaned
        }
        val escaped = safeValue.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun buildCsvContent(
        participants: List<Participant>,
        type: CsvExportType,
        specificEvent: PortalEvent? = null
    ): Pair<String, String> {
        val filtered = when (type) {
            CsvExportType.FULL_LIST ->
                participants.sortedByDescending { it.submittedAt?.seconds ?: 0L }
            CsvExportType.SPORTS_ONLY ->
                participants.filter { it.sportsEventIds.isNotEmpty() }
                    .sortedBy { it.normalizedRollNumber }
            CsvExportType.CULTURAL_ONLY ->
                participants.filter { it.culturalEventIds.isNotEmpty() }
                    .sortedBy { it.normalizedRollNumber }
            CsvExportType.EVENT_SPECIFIC -> {
                val targetId = specificEvent?.id.orEmpty()
                participants.filter {
                    targetId.isNotBlank() &&
                        (targetId in it.sportsEventIds || targetId in it.culturalEventIds)
                }.sortedBy { it.normalizedRollNumber }
            }
            CsvExportType.ROLL_SORTED ->
                participants.sortedBy { it.normalizedRollNumber }
        }

        val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("Student Name,Roll Number,Selected Sports Events,Selected Cultural Events,Submission Timestamp\r\n")

        for (p in filtered) {
            val sportsStr = p.sportsEventNames.joinToString("; ")
            val culturalStr = p.culturalEventNames.joinToString("; ")
            val submittedStr = (p.submittedAt ?: p.createdAt)?.toDate()?.let {
                timestampFormatter.format(it)
            } ?: ""

            sb.append(sanitizeCell(p.studentName)).append(',')
            sb.append(sanitizeCell(p.rollNumber)).append(',')
            sb.append(sanitizeCell(sportsStr)).append(',')
            sb.append(sanitizeCell(culturalStr)).append(',')
            sb.append(sanitizeCell(submittedStr)).append("\r\n")
        }

        val slug = if (type == CsvExportType.EVENT_SPECIFIC && specificEvent != null) {
            val eventSlug = specificEvent.name.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(32)
            "${type.fileNamePrefix}_$eventSlug.csv"
        } else {
            "${type.fileNamePrefix}.csv"
        }

        return slug to sb.toString()
    }

    /**
     * Shares a generated CSV via FileProvider with zero storage permissions.
     */
    fun shareCsv(context: Context, fileName: String, csvContent: String): Result<Unit> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(exportDir, safeFileName)
            file.writeText(csvContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Green House Participation Report — $safeFileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share CSV Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
