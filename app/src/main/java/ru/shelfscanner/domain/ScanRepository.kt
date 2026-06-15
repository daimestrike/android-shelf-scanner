package ru.shelfscanner.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.shelfscanner.data.bluetooth.BluetoothController
import ru.shelfscanner.data.bluetooth.ConnectionStatus
import ru.shelfscanner.data.local.ScanSessionDao
import ru.shelfscanner.data.model.ActiveSessionDraft
import ru.shelfscanner.data.model.PiCommand
import ru.shelfscanner.data.model.PiCommands
import ru.shelfscanner.data.model.ScanMessage
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.data.model.ScanStatus
import ru.shelfscanner.data.model.ScannedCode
import ru.shelfscanner.utils.DemoDataGenerator
import ru.shelfscanner.utils.JsonParser
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ActiveScanSession(
    val sessionId: String? = null,
    val status: ScanStatus = ScanStatus.IDLE,
    val totalDetected: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val confidenceAvg: Double? = null,
    val codes: List<ScannedCode> = emptyList(),
    val startedAt: String? = null,
    val lastUpdatedAt: String? = null,
    val lastRawMessage: String? = null,
    val uiError: String? = null,
    val infoMessage: String? = null,
    val isSaved: Boolean = false,
    val actualCount: Int? = null,
) {
    val uniqueCount: Int get() = codes.size
    val detectionRate: Double?
        get() = actualCount?.takeIf { it > 0 }?.let { uniqueCount * 100.0 / it }
}

class ScanRepository(
    private val bluetoothController: BluetoothController,
    private val dao: ScanSessionDao,
    private val parser: JsonParser,
    private val parseMessage: ParseScanMessageUseCase,
    private val demoDataGenerator: DemoDataGenerator,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val uniqueCodes = linkedMapOf<String, ScannedCode>()
    private val _activeSession = MutableStateFlow(ActiveScanSession())
    val activeSession: StateFlow<ActiveScanSession> = _activeSession.asStateFlow()
    val sessions = dao.observeAll()
    val connectionState = bluetoothController.connectionState
    val devices = bluetoothController.devices
    private var demoJob: Job? = null

    init {
        scope.launch {
            bluetoothController.rawMessages.collect(::processRawMessage)
        }
        scope.launch {
            restoreDraft()
        }
    }

    fun startDiscovery() = bluetoothController.startDiscovery()
    fun stopDiscovery() = bluetoothController.stopDiscovery()
    fun connect(address: String) = bluetoothController.connect(address)

    fun disconnect() {
        stopDemo()
        bluetoothController.disconnect()
    }

    fun startSession() {
        val actualCount = _activeSession.value.actualCount
        demoJob?.cancel()
        uniqueCodes.clear()
        val now = now()
        val session = ActiveScanSession(
            sessionId = "local_${System.currentTimeMillis()}",
            status = ScanStatus.SCANNING,
            startedAt = now,
            lastUpdatedAt = now,
            actualCount = actualCount,
        )
        _activeSession.value = session
        if (connectionState.value.status == ConnectionStatus.DEMO) {
            startDemoStream()
        } else {
            scope.launch {
                mutex.withLock {
                    persistDraftLocked()
                    sendCommandLocked(PiCommands.START)
                }
            }
        }
    }

    fun finishSession() {
        val current = _activeSession.value
        if (current.sessionId == null) return
        demoJob?.cancel()
        _activeSession.value = current.copy(
            status = ScanStatus.FINISHED,
            lastUpdatedAt = now(),
            isSaved = false,
        )
        scope.launch {
            mutex.withLock {
                sendCommandLocked(PiCommands.FINISH)
                saveCurrentLocked()
            }
        }
    }

    fun clear() {
        demoJob?.cancel()
        uniqueCodes.clear()
        _activeSession.value = ActiveScanSession()
        scope.launch {
            mutex.withLock {
                dao.deleteDraft()
                sendCommandLocked(PiCommands.CLEAR, sessionId = null)
            }
        }
    }

    fun startDemo() {
        bluetoothController.setDemoMode(true)
        startSession()
    }

    fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
        if (connectionState.value.status == ConnectionStatus.DEMO) {
            bluetoothController.setDemoMode(false)
        }
    }

    fun saveCurrent() {
        scope.launch {
            mutex.withLock { saveCurrentLocked() }
        }
    }

    fun setActualCount(value: Int?) {
        val normalized = value?.takeIf { it > 0 }
        _activeSession.value = _activeSession.value.copy(
            actualCount = normalized,
            isSaved = false,
        )
        scope.launch {
            mutex.withLock { persistDraftLocked() }
        }
    }

    private fun startDemoStream() {
        demoJob?.cancel()
        val sessionId = "demo_${System.currentTimeMillis()}"
        val started = now()
        _activeSession.value = ActiveScanSession(
            sessionId = sessionId,
            status = ScanStatus.SCANNING,
            startedAt = started,
            lastUpdatedAt = started,
            actualCount = _activeSession.value.actualCount,
        )
        demoJob = scope.launch {
            demoDataGenerator.messages(sessionId).collectLatest(::processRawMessage)
        }
    }

    private suspend fun processRawMessage(raw: String) {
        mutex.withLock {
            parseMessage(raw)
                .onSuccess { applyMessage(it, raw) }
                .onFailure {
                    val current = _activeSession.value
                    _activeSession.value = current.copy(
                        errors = current.errors + 1,
                        lastRawMessage = raw,
                        lastUpdatedAt = now(),
                        uiError = "Некорректный JSON: ${it.message ?: "ошибка разбора"}",
                        infoMessage = null,
                    )
                    persistDraftLocked()
                }
        }
    }

    private suspend fun applyMessage(message: ScanMessage, raw: String) {
        val previous = _activeSession.value
        if (previous.sessionId != null && previous.sessionId != message.sessionId) {
            uniqueCodes.clear()
        }
        message.codes.forEach { code ->
            val existing = uniqueCodes[code.value]
            uniqueCodes[code.value] = when {
                existing == null -> code
                (code.confidence ?: 0.0) > (existing.confidence ?: 0.0) -> code
                else -> existing
            }
        }
        val firstTimestamp = if (previous.sessionId == message.sessionId) {
            previous.startedAt ?: message.timestamp
        } else {
            message.timestamp
        }
        _activeSession.value = ActiveScanSession(
            sessionId = message.sessionId,
            status = message.status,
            totalDetected = maxOf(message.totalDetected, uniqueCodes.size),
            duplicates = message.duplicates,
            errors = message.errors,
            confidenceAvg = message.confidenceAvg,
            codes = uniqueCodes.values.toList(),
            startedAt = firstTimestamp,
            lastUpdatedAt = message.timestamp,
            lastRawMessage = raw,
            uiError = null,
            actualCount = previous.actualCount,
        )
        if (message.status == ScanStatus.FINISHED) {
            saveCurrentLocked()
        } else {
            persistDraftLocked()
        }
    }

    private suspend fun saveCurrentLocked() {
        val current = _activeSession.value
        val id = current.sessionId ?: return
        val started = current.startedAt ?: return
        val finished = current.lastUpdatedAt ?: now()
        dao.upsert(
            ScanSession(
                sessionId = id,
                startedAt = started,
                finishedAt = finished,
                status = current.status.name,
                totalDetected = current.totalDetected,
                uniqueCount = current.uniqueCount,
                duplicates = current.duplicates,
                errors = current.errors,
                confidenceAvg = current.confidenceAvg,
                actualCount = current.actualCount,
                detectionRate = current.detectionRate,
                codesJson = parser.json.encodeToString(current.codes),
                durationMillis = durationMillis(started, finished),
            ),
        )
        _activeSession.value = current.copy(isSaved = true)
        if (current.status == ScanStatus.FINISHED) {
            dao.deleteDraft()
        } else {
            persistDraftLocked()
        }
    }

    private suspend fun restoreDraft() {
        mutex.withLock {
            val draft = dao.getDraft() ?: return
            if (_activeSession.value.sessionId != null) return
            val codes = runCatching {
                parser.json.decodeFromString<List<ScannedCode>>(draft.codesJson)
            }.getOrDefault(emptyList())
            uniqueCodes.clear()
            codes.forEach { uniqueCodes[it.value] = it }
            _activeSession.value = ActiveScanSession(
                sessionId = draft.sessionId,
                status = runCatching { ScanStatus.valueOf(draft.status) }
                    .getOrDefault(ScanStatus.PAUSED),
                totalDetected = draft.totalDetected,
                duplicates = draft.duplicates,
                errors = draft.errors,
                confidenceAvg = draft.confidenceAvg,
                codes = codes,
                startedAt = draft.startedAt,
                lastUpdatedAt = draft.lastUpdatedAt,
                lastRawMessage = draft.lastRawMessage,
                infoMessage = "Незавершённая сессия восстановлена",
                actualCount = draft.actualCount,
            )
        }
    }

    private suspend fun persistDraftLocked() {
        val current = _activeSession.value
        val sessionId = current.sessionId ?: return
        val startedAt = current.startedAt ?: return
        if (current.status == ScanStatus.FINISHED) {
            dao.deleteDraft()
            return
        }
        dao.upsertDraft(
            ActiveSessionDraft(
                sessionId = sessionId,
                status = current.status.name,
                totalDetected = current.totalDetected,
                duplicates = current.duplicates,
                errors = current.errors,
                confidenceAvg = current.confidenceAvg,
                codesJson = parser.json.encodeToString(current.codes),
                startedAt = startedAt,
                lastUpdatedAt = current.lastUpdatedAt ?: now(),
                lastRawMessage = current.lastRawMessage,
                actualCount = current.actualCount,
            ),
        )
    }

    private suspend fun sendCommandLocked(command: String, sessionId: String? = null) {
        when (connectionState.value.status) {
            ConnectionStatus.DEMO -> return
            ConnectionStatus.CONNECTED -> {
                val current = _activeSession.value
                val resolvedSessionId = sessionId ?: current.sessionId
                val raw = parser.json.encodeToString(
                    PiCommand(
                        command = command,
                        sessionId = resolvedSessionId,
                        timestamp = now(),
                        actualCount = current.actualCount,
                    ),
                )
                bluetoothController.sendLine(raw)
                    .onSuccess {
                        _activeSession.value = current.copy(
                            uiError = null,
                            infoMessage = "Команда $command отправлена на Raspberry Pi",
                        )
                    }
                    .onFailure {
                        _activeSession.value = current.copy(
                            uiError = "Команда не отправлена: ${it.message}",
                            infoMessage = null,
                        )
                    }
            }
            else -> {
                if (command != PiCommands.CLEAR) {
                    _activeSession.value = _activeSession.value.copy(
                        uiError = "Подключите Raspberry Pi перед отправкой команды",
                        infoMessage = null,
                    )
                }
            }
        }
    }

    private fun durationMillis(start: String, end: String): Long = runCatching {
        Duration.between(LocalDateTime.parse(start), LocalDateTime.parse(end)).toMillis()
    }.getOrDefault(0L).coerceAtLeast(0L)

    private fun now(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
