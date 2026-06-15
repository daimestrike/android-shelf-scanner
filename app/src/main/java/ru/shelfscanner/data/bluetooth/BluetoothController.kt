package ru.shelfscanner.data.bluetooth

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean,
)

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    ERROR,
    DEMO,
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceName: String? = null,
    val error: String? = null,
)

interface BluetoothController {
    val devices: StateFlow<List<BluetoothDeviceInfo>>
    val connectionState: StateFlow<ConnectionState>
    val rawMessages: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()
    fun connect(address: String)
    fun disconnect()
    suspend fun sendLine(line: String): Result<Unit>
    fun setDemoMode(enabled: Boolean)
    fun close()
}
