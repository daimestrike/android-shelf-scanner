package ru.shelfscanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.data.model.ActiveSessionDraft

@Database(
    entities = [ScanSession::class, ActiveSessionDraft::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSessionDao(): ScanSessionDao
}
