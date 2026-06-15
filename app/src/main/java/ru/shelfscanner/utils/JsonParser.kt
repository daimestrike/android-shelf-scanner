package ru.shelfscanner.utils

import kotlinx.serialization.json.Json
import ru.shelfscanner.data.model.ScanMessage

class JsonParser(
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    fun parseScanMessage(raw: String): Result<ScanMessage> = runCatching {
        json.decodeFromString<ScanMessage>(raw)
    }

    fun encodeScanMessage(message: ScanMessage): String = json.encodeToString(message)
}
