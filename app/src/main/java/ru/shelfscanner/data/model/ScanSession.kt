package ru.shelfscanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey val sessionId: String,
    val startedAt: String,
    val finishedAt: String,
    val status: String,
    val totalDetected: Int,
    val uniqueCount: Int,
    val duplicates: Int,
    val errors: Int,
    val confidenceAvg: Double?,
    val actualCount: Int? = null,
    val detectionRate: Double? = null,
    val codesJson: String,
    val durationMillis: Long,
)
