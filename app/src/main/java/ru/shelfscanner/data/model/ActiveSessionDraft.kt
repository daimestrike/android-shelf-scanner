package ru.shelfscanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_session_draft")
data class ActiveSessionDraft(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val sessionId: String,
    val status: String,
    val totalDetected: Int,
    val duplicates: Int,
    val errors: Int,
    val confidenceAvg: Double?,
    val codesJson: String,
    val startedAt: String,
    val lastUpdatedAt: String,
    val lastRawMessage: String?,
    val actualCount: Int?,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
