package ru.shelfscanner.utils

import kotlinx.serialization.decodeFromString
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.data.model.ScannedCode
import java.util.Locale

class CsvExporter(
    private val parser: JsonParser,
) {
    fun export(sessions: List<ScanSession>): String = buildString {
        // BOM helps Microsoft Excel detect UTF-8 correctly.
        append('\uFEFF')
        appendLine(
            listOf(
                "session_id",
                "status",
                "started_at",
                "finished_at",
                "duration_seconds",
                "total_detected",
                "unique_count",
                "duplicates",
                "errors",
                "confidence_avg",
                "actual_count",
                "detection_rate_percent",
                "code_value",
                "code_camera_id",
                "code_confidence",
                "code_first_seen",
            ).joinToString(SEPARATOR, transform = ::escape),
        )

        sessions.forEach { session ->
            val codes = decodeCodes(session.codesJson)
            if (codes.isEmpty()) {
                appendLine(sessionRow(session, null))
            } else {
                codes.forEach { code -> appendLine(sessionRow(session, code)) }
            }
        }
    }

    private fun decodeCodes(raw: String): List<ScannedCode> =
        runCatching { parser.json.decodeFromString<List<ScannedCode>>(raw) }
            .getOrDefault(emptyList())

    private fun sessionRow(session: ScanSession, code: ScannedCode?): String =
        listOf(
            session.sessionId,
            session.status,
            session.startedAt,
            session.finishedAt,
            formatDecimal(session.durationMillis / 1_000.0),
            session.totalDetected.toString(),
            session.uniqueCount.toString(),
            session.duplicates.toString(),
            session.errors.toString(),
            session.confidenceAvg?.let(::formatDecimal).orEmpty(),
            session.actualCount?.toString().orEmpty(),
            session.detectionRate?.let(::formatDecimal).orEmpty(),
            code?.value.orEmpty(),
            code?.cameraId.orEmpty(),
            code?.confidence?.let(::formatDecimal).orEmpty(),
            code?.firstSeen.orEmpty(),
        ).joinToString(SEPARATOR, transform = ::escape)

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.3f", value)

    private fun escape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private companion object {
        const val SEPARATOR = ";"
    }
}
