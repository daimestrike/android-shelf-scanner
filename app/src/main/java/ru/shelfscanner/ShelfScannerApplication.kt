package ru.shelfscanner

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.shelfscanner.data.bluetooth.AndroidBluetoothController
import ru.shelfscanner.data.local.AppDatabase
import ru.shelfscanner.domain.ParseScanMessageUseCase
import ru.shelfscanner.domain.ScanRepository
import ru.shelfscanner.utils.DemoDataGenerator
import ru.shelfscanner.utils.CsvExporter
import ru.shelfscanner.utils.JsonParser

class ShelfScannerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "shelf-scanner.db",
    ).addMigrations(MIGRATION_1_2).build()
    private val parser = JsonParser()
    val csvExporter = CsvExporter(parser)
    val bluetoothController = AndroidBluetoothController(application)
    val repository = ScanRepository(
        bluetoothController = bluetoothController,
        dao = database.scanSessionDao(),
        parser = parser,
        parseMessage = ParseScanMessageUseCase(parser),
        demoDataGenerator = DemoDataGenerator(parser),
        scope = applicationScope,
    )
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scan_sessions ADD COLUMN actualCount INTEGER")
        db.execSQL("ALTER TABLE scan_sessions ADD COLUMN detectionRate REAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS active_session_draft (
                id INTEGER NOT NULL,
                sessionId TEXT NOT NULL,
                status TEXT NOT NULL,
                totalDetected INTEGER NOT NULL,
                duplicates INTEGER NOT NULL,
                errors INTEGER NOT NULL,
                confidenceAvg REAL,
                codesJson TEXT NOT NULL,
                startedAt TEXT NOT NULL,
                lastUpdatedAt TEXT NOT NULL,
                lastRawMessage TEXT,
                actualCount INTEGER,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }
}
