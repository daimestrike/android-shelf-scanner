package ru.shelfscanner.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothConnectionService(
    private val scope: CoroutineScope,
) {
    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val writeMutex = Mutex()

    @SuppressLint("MissingPermission")
    suspend fun connect(
        device: BluetoothDevice,
        onLine: suspend (String) -> Unit,
    ) {
        disconnect()
        val newSocket = withContext(Dispatchers.IO) {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        }
        socket = newSocket
        readerJob = scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(newSocket.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onLine(line)
                }
            }
        }
    }

    suspend fun disconnect() {
        readerJob?.cancelAndJoin()
        readerJob = null
        withContext(Dispatchers.IO) {
            runCatching { socket?.close() }
        }
        socket = null
    }

    suspend fun sendLine(line: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            writeMutex.withLock {
                val activeSocket = socket ?: error("Bluetooth не подключен")
                activeSocket.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
                activeSocket.outputStream.flush()
            }
        }
    }

    companion object {
        // Standard Serial Port Profile UUID; Raspberry Pi should expose an RFCOMM service.
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
