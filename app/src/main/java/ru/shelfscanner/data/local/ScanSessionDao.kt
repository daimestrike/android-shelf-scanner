package ru.shelfscanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.data.model.ActiveSessionDraft

@Dao
interface ScanSessionDao {
    @Query("SELECT * FROM scan_sessions ORDER BY finishedAt DESC")
    fun observeAll(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeById(sessionId: String): Flow<ScanSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ScanSession)

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM active_session_draft WHERE id = 1 LIMIT 1")
    suspend fun getDraft(): ActiveSessionDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: ActiveSessionDraft)

    @Query("DELETE FROM active_session_draft")
    suspend fun deleteDraft()
}
