package ru.shelfscanner.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shelfscanner.data.model.ScanStatus
import ru.shelfscanner.data.model.PiCommand
import kotlinx.serialization.encodeToString

class JsonParserTest {
    private val parser = JsonParser()

    @Test
    fun parsesRaspberryPiMessageWithLowercaseStatus() {
        val raw = """
            {
              "session_id": "shelf_001",
              "timestamp": "2026-06-12T10:15:00",
              "status": "scanning",
              "total_detected": 24,
              "unique_count": 21,
              "duplicates": 3,
              "errors": 0,
              "camera_id": "cam_1",
              "confidence_avg": 0.92,
              "unknown_future_field": true,
              "codes": [{
                "value": "010460123456789021ABC123",
                "camera_id": "cam_1",
                "confidence": 0.94,
                "first_seen": "2026-06-12T10:14:55"
              }]
            }
        """.trimIndent()

        val result = parser.parseScanMessage(raw)

        assertTrue(result.isSuccess)
        val message = result.getOrThrow()
        assertEquals("shelf_001", message.sessionId)
        assertEquals(ScanStatus.SCANNING, message.status)
        assertEquals(1, message.codes.size)
    }

    @Test
    fun returnsFailureForBrokenJson() {
        assertTrue(parser.parseScanMessage("""{"session_id":""").isFailure)
    }

    @Test
    fun encodesRaspberryPiCommandContract() {
        val raw = parser.json.encodeToString(
            PiCommand(
                command = "start_session",
                sessionId = "shelf_001",
                timestamp = "2026-06-14T10:00:00",
                actualCount = 24,
            ),
        )

        assertTrue(raw.contains("\"command\":\"start_session\""))
        assertTrue(raw.contains("\"session_id\":\"shelf_001\""))
        assertTrue(raw.contains("\"actual_count\":24"))
    }
}
