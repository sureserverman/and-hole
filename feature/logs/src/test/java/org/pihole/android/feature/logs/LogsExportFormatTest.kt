package org.pihole.android.feature.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.data.db.entity.QueryLogEntity

class LogsExportFormatTest {

    @Test
    fun csv_escapesCommasAndQuotes() {
        val rows =
            listOf(
                QueryLogEntity(
                    id = 1,
                    timestamp = 123,
                    qname = "a,b\"c",
                    qtype = 1,
                    decision = "allowed",
                    matchedRuleId = null,
                    matchedSourceId = null,
                    responseCode = 0,
                    latencyMs = 5,
                    answeredFromCache = false,
                ),
            )

        val csv = LogsExportWriter.formatRows(rows, LogsExportFormat.Csv)
        assertTrue(csv.startsWith("id,timestamp,qname,"))
        // qname should be quoted and inner quote doubled.
        assertTrue(csv.contains("\"a,b\"\"c\""))
    }

    @Test
    fun jsonl_hasOneJsonObjectPerRow() {
        val rows =
            listOf(
                QueryLogEntity(
                    id = 1,
                    timestamp = 123,
                    qname = "example.com.",
                    qtype = 1,
                    decision = "pass",
                    matchedRuleId = 9,
                    matchedSourceId = 10,
                    responseCode = 0,
                    latencyMs = 5,
                    answeredFromCache = true,
                ),
                QueryLogEntity(
                    id = 2,
                    timestamp = 124,
                    qname = "blocked.com.",
                    qtype = 1,
                    decision = "blocked",
                    matchedRuleId = null,
                    matchedSourceId = null,
                    responseCode = 0,
                    latencyMs = 1,
                    answeredFromCache = false,
                ),
            )

        val jsonl = LogsExportWriter.formatRows(rows, LogsExportFormat.Jsonl).trim()
        val lines = jsonl.split('\n')
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("{"))
        assertTrue(lines[0].contains("\"qname\":\"example.com.\""))
        assertTrue(lines[1].contains("\"decision\":\"blocked\""))
    }
}

