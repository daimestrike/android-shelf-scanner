package ru.shelfscanner.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidBluetoothController(
    private val context: Context,
) : BluetoothController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val connectionService = BluetoothConnectionService(scope)

    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val devices = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState())
    override val connectionState = _connectionState.asStateFlow()

    private val _rawMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val rawMessages = _rawMessages.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDevice() ?: return
                    addDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_connectionState.value.status == ConnectionStatus.SEARCHING) {
                        _connectionState.value = ConnectionState(ConnectionStatus.DISCONNECTED)
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            },
        )
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (!hasBluetoothPermission()) {
            _connectionState.value = ConnectionState(
                ConnectionStatus.ERROR,
                error = "Нет разрешения Bluetooth",
            )
            return
        }
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState(
                ConnectionStatus.ERROR,
                error = "Bluetooth выключен или недоступен",
            )
            return
        }
        _devices.value = bluetoothAdapter.bondedDevices.orEmpty().map { it.toInfo(true) }
        bluetoothAdapter.cancelDiscovery()
        _connectionState.value = ConnectionState(ConnectionStatus.SEARCHING)
        if (!bluetoothAdapter.startDiscovery()) {
            _connectionState.value = ConnectionState(
                ConnectionStatus.ERROR,
                error = "Не удалось запустить поиск",
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        if (hasBluetoothPermission()) adapter?.cancelDiscovery()
        if (_connectionState.value.status == ConnectionStatus.SEARCHING) {
            _connectionState.value = ConnectionState(ConnectionStatus.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(address: String) {
        if (!hasBluetoothPermission()) {
            _connectionState.value = ConnectionState(
                ConnectionStatus.ERROR,
                error = "Нет разрешения Bluetooth",
            )
            return
        }
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _connectionState.value = ConnectionState(
                ConnectionStatus.ERROR,
                error = "Устройство не найдено",
            )
            return
        }
        adapter?.cancelDiscovery()
        _connectionState.value = ConnectionState(ConnectionStatus.CONNECTING, device.safeName())
        scope.launch {
            runCatching {
                connectionService.connect(device) { _rawMessages.emit(it) }
            }.onSuccess {
                _connectionState.value =
                    ConnectionState(ConnectionStatus.CONNECTED, device.safeName())
            }.onFailure {
                connectionService.disconnect()
                _connectionState.value = ConnectionState(
                    ConnectionStatus.ERROR,
                    device.safeName(),
                    it.message ?: "Ошибка подключения",
                )
            }
        }
    }

    override fun disconnect() {
        scope.launch {
            connectionService.disconnect()
            _connectionState.value = ConnectionState(ConnectionStatus.DISCONNECTED)
        }
    }

    override suspend fun sendLine(line: String): Result<Unit> {
        if (_connectionState.value.status != ConnectionStatus.CONNECTED) {
            return Result.failure(IllegalStateException("Raspberry Pi не подключен"))
        }
        return connectionService.sendLine(line)
    }

    override fun setDemoMode(enabled: Boolean) {
        if (enabled) {
            scope.launch { connectionService.disconnect() }
        }
        _connectionState.value = ConnectionState(
            if (enabled) ConnectionStatus.DEMO else ConnectionStatus.DISCONNECTED,
            deviceName = if (enabled) "Demo Mode" else null,
        )
    }

    override fun close() {
        runCatching { context.unregisterReceiver(receiver) }
        scope.cancel()
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        val info = device.toInfo(device.bondState == BluetoothDevice.BOND_BONDED)
        _devices.value = (_devices.value.filterNot { it.address == info.address } + info)
            .sortedWith(compareByDescending<BluetoothDeviceInfo> { it.isBonded }.thenBy { it.name })
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toInfo(bonded: Boolean) =
        BluetoothDeviceInfo(safeName(), address, bonded)

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String = name ?: "Без имени"

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
