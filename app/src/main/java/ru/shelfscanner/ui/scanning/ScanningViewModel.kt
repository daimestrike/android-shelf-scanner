package ru.shelfscanner.ui.scanning

import androidx.lifecycle.ViewModel
import ru.shelfscanner.domain.ScanRepository

class ScanningViewModel(
    private val repository: ScanRepository,
) : ViewModel() {
    val state = repository.activeSession

    fun start() = repository.startSession()
    fun finish() = repository.finishSession()
    fun clear() = repository.clear()
    fun save() = repository.saveCurrent()
    fun setActualCount(value: Int?) = repository.setActualCount(value)
}
