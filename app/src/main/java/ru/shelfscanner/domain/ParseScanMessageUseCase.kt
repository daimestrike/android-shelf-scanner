package ru.shelfscanner.domain

import ru.shelfscanner.data.model.ScanMessage
import ru.shelfscanner.utils.JsonParser

class ParseScanMessageUseCase(
    private val parser: JsonParser,
) {
    operator fun invoke(raw: String): Result<ScanMessage> = parser.parseScanMessage(raw)
}
