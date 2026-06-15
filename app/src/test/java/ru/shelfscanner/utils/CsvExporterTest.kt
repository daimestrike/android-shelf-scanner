package ru.shelfscanner.utils

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.data.model.ScannedCode

class CsvExporterTest {
    private val parser = JsonParser()
    private val exporter = CsvExporter(parser)

    @Test
    fun exportsSessionAndIndividualCodesForExcel() {
        val codes = listOf(
            ScannedCode(
                value = "010460123456789021ABC123",
                cameraId = "cam_1",
                confidence = 0.94,
                firstSeen = "2026-06-12T10:14:55",
            ),
        )
        val session = ScanSession(
            sessionId = "shelf_001",
            startedAt = "2026-06-12T10:14:00",
            finishedAt = "2026-06-12T10:15:00",
            status = "FINISHED",
            totalDetected = 2,
            uniqueCount = 1,
            duplicates = 1,
            errors = 0,
            confidenceAvg = 0.94,
            actualCount = 1,
            detectionRate = 100.0,
            codesJson = parser.json.encodeToString(codes),
            durationMillis = 60_000,
        )

        val csv = exporter.export(listOf(session))

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"session_id\";\"status\""))
        assertTrue(csv.contains("\"shelf_001\""))
        assertTrue(csv.contains("\"010460123456789021ABC123\""))
        assertTrue(csv.contains("\"100.000\""))
    }
}
