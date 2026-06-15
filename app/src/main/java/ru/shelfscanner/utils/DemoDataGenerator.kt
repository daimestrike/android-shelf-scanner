package ru.shelfscanner.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.shelfscanner.data.model.ScanMessage
import ru.shelfscanner.data.model.ScanStatus
import ru.shelfscanner.data.model.ScannedCode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class DemoDataGenerator(
    private val parser: JsonParser,
) {
    fun messages(sessionId: String): Flow<String> = flow {
        val codes = mutableListOf<ScannedCode>()
        var duplicates = 0
        repeat(30) { index ->
            delay(1_000)
            val isDuplicate = index > 2 && Random.nextInt(4) == 0
            if (isDuplicate) {
                duplicates++
            } else {
                codes += ScannedCode(
                    value = "010460123456789021DEMO${(index + 1).toString().padStart(3, '0')}",
                    cameraId = "cam_${index % 3 + 1}",
                    confidence = Random.nextDouble(0.82, 0.99),
                    firstSeen = now(),
                )
            }
            emit(
                parser.encodeScanMessage(
                    ScanMessage(
                        sessionId = sessionId,
                        timestamp = now(),
                        status = if (index == 29) ScanStatus.FINISHED else ScanStatus.SCANNING,
                        totalDetected = codes.size + duplicates,
                        uniqueCount = codes.size,
                        duplicates = duplicates,
                        errors = 0,
                        cameraId = "cam_${index % 3 + 1}",
                        confidenceAvg = codes.mapNotNull { it.confidence }.average(),
                        codes = if (isDuplicate) listOf(codes.random()) else listOf(codes.last()),
                    ),
                ),
            )
        }
    }

    private fun now(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
