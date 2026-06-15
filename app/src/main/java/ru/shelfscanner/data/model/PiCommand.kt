package ru.shelfscanner.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PiCommand(
    val command: String,
    @SerialName("session_id") val sessionId: String?,
    val timestamp: String,
    @SerialName("actual_count") val actualCount: Int? = null,
)

object PiCommands {
    const val START = "start_session"
    const val FINISH = "finish_session"
    const val CLEAR = "clear_session"
}
