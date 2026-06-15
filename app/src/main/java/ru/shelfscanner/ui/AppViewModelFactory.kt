package ru.shelfscanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.shelfscanner.AppContainer
import ru.shelfscanner.ui.connection.ConnectionViewModel
import ru.shelfscanner.ui.history.HistoryViewModel
import ru.shelfscanner.ui.scanning.ScanningViewModel

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ConnectionViewModel::class.java) ->
            ConnectionViewModel(container.repository)
        modelClass.isAssignableFrom(ScanningViewModel::class.java) ->
            ScanningViewModel(container.repository)
        modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
            HistoryViewModel(container.repository, container.csvExporter)
        else -> error("Unknown ViewModel: ${modelClass.name}")
    } as T
}
