package ru.shelfscanner.ui.connection

import androidx.lifecycle.ViewModel
import ru.shelfscanner.domain.ScanRepository

class ConnectionViewModel(
    private val repository: ScanRepository,
) : ViewModel() {
    val devices = repository.devices
    val connectionState = repository.connectionState

    fun search() = repository.startDiscovery()
    fun connect(address: String) = repository.connect(address)
    fun disconnect() = repository.disconnect()
    fun startDemo() = repository.startDemo()
}
