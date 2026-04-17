package org.pihole.android.feature.logs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.pihole.android.data.db.entity.QueryLogEntity
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class LogsExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    Csv(label = "CSV", extension = "csv", mimeType = "text/csv"),
    Jsonl(label = "JSONL", extension = "jsonl", mimeType = "application/x-ndjson"),
}

data class LogsExport(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
)

object LogsExportWriter {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun write(
        context: Context,
        format: LogsExportFormat,
        rows: List<QueryLogEntity>,
    ): LogsExport {
        val bytes = formatRows(rows, format).toByteArray(Charsets.UTF_8)

        val ts = timestampFormatter.format(Instant.now())
        val fileName = "pihole-query-log-$ts.${format.extension}"

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportsDir, fileName)
        outFile.writeBytes(bytes)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, outFile)

        return LogsExport(uri = uri, mimeType = format.mimeType, fileName = fileName)
    }

    fun buildShareIntent(export: LogsExport): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = export.mimeType
            putExtra(Intent.EXTRA_STREAM, export.uri)
            putExtra(Intent.EXTRA_SUBJECT, export.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    internal fun formatRows(rows: List<QueryLogEntity>, format: LogsExportFormat): String =
        when (format) {
            LogsExportFormat.Csv -> renderCsv(rows)
            LogsExportFormat.Jsonl -> renderJsonl(rows)
        }

    private fun renderJsonl(rows: List<QueryLogEntity>): String =
        buildString {
            for (row in rows) {
                append(jsonLine(row))
                append('\n')
            }
        }

    private fun renderCsv(rows: List<QueryLogEntity>): String {
        val header =
            listOf(
                "id",
                "timestamp",
                "qname",
                "qtype",
                "decision",
                "matchedRuleId",
                "matchedSourceId",
                "responseCode",
                "latencyMs",
                "answeredFromCache",
            ).joinToString(",")
        return buildString {
            append(header)
            append('\n')
            for (row in rows) {
                append(
                    listOf(
                        row.id.toString(),
                        row.timestamp.toString(),
                        csvCell(row.qname),
                        row.qtype.toString(),
                        csvCell(row.decision),
                        row.matchedRuleId?.toString() ?: "",
                        row.matchedSourceId?.toString() ?: "",
                        row.responseCode.toString(),
                        row.latencyMs.toString(),
                        row.answeredFromCache.toString(),
                    ).joinToString(","),
                )
                append('\n')
            }
        }
    }

    private fun csvCell(value: String): String {
        val mustQuote =
            value.any { ch ->
                ch == ',' || ch == '"' || ch == '\n' || ch == '\r'
            }
        if (!mustQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun jsonLine(row: QueryLogEntity): String =
        buildString {
            append('{')
            appendJsonLong("id", row.id); append(',')
            appendJsonLong("timestamp", row.timestamp); append(',')
            appendJsonString("qname", row.qname); append(',')
            appendJsonInt("qtype", row.qtype); append(',')
            appendJsonString("decision", row.decision); append(',')
            appendJsonNullableLong("matchedRuleId", row.matchedRuleId); append(',')
            appendJsonNullableLong("matchedSourceId", row.matchedSourceId); append(',')
            appendJsonInt("responseCode", row.responseCode); append(',')
            appendJsonLong("latencyMs", row.latencyMs); append(',')
            appendJsonBoolean("answeredFromCache", row.answeredFromCache)
            append('}')
        }

    private fun StringBuilder.appendJsonString(key: String, value: String) {
        append('"'); append(escapeJson(key)); append("\":\""); append(escapeJson(value)); append('"')
    }

    private fun StringBuilder.appendJsonInt(key: String, value: Int) {
        append('"'); append(escapeJson(key)); append("\":"); append(value)
    }

    private fun StringBuilder.appendJsonLong(key: String, value: Long) {
        append('"'); append(escapeJson(key)); append("\":"); append(value)
    }

    private fun StringBuilder.appendJsonNullableLong(key: String, value: Long?) {
        append('"'); append(escapeJson(key)); append("\":")
        if (value == null) append("null") else append(value)
    }

    private fun StringBuilder.appendJsonBoolean(key: String, value: Boolean) {
        append('"'); append(escapeJson(key)); append("\":"); append(if (value) "true" else "false")
    }

    private fun escapeJson(s: String): String =
        buildString(s.length + 16) {
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
}

