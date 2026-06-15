package ru.shelfscanner.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ScanMessage(
    @SerialName("session_id") val sessionId: String,
    val timestamp: String,
    val status: ScanStatus,
    @SerialName("total_detected") val totalDetected: Int,
    @SerialName("unique_count") val uniqueCount: Int,
    val duplicates: Int,
    val errors: Int,
    @SerialName("camera_id") val cameraId: String? = null,
    @SerialName("confidence_avg") val confidenceAvg: Double? = null,
    val codes: List<ScannedCode> = emptyList(),
)

@Serializable
data class ScannedCode(
    val value: String,
    @SerialName("camera_id") val cameraId: String? = null,
    val confidence: Double? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
)

@Serializable(with = ScanStatusSerializer::class)
enum class ScanStatus {
    IDLE,
    SCANNING,
    PAUSED,
    FINISHED,
    ERROR,
}

object ScanStatusSerializer : KSerializer<ScanStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScanStatus", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ScanStatus {
        val rawStatus = decoder.decodeString()
        return ScanStatus.entries.firstOrNull {
            it.name.equals(rawStatus, ignoreCase = true)
        } ?: ScanStatus.ERROR
    }

    override fun serialize(encoder: Encoder, value: ScanStatus) {
        encoder.encodeString(value.name.lowercase())
    }
}
