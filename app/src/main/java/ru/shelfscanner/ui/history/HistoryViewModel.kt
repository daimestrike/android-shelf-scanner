package ru.shelfscanner.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.shelfscanner.data.model.ScanSession
import ru.shelfscanner.domain.ScanRepository
import ru.shelfscanner.utils.CsvExporter

class HistoryViewModel(
    repository: ScanRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {
    val sessions = repository.sessions
    private val _selected = MutableStateFlow<ScanSession?>(null)
    val selected = _selected.asStateFlow()

    fun select(session: ScanSession?) {
        _selected.value = session
    }

    fun exportCsv(sessions: List<ScanSession>): String = csvExporter.export(sessions)
}
