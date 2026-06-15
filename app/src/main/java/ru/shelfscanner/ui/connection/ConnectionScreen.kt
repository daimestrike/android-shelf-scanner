package ru.shelfscanner.ui.connection

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.shelfscanner.data.bluetooth.ConnectionStatus

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    contentPadding: PaddingValues,
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.all { it }) viewModel.search()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Подключение к Raspberry Pi", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            StatusCard(state.status, state.deviceName, state.error)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        permissionLauncher.launch(requiredBluetoothPermissions())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Найти устройство")
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = state.status != ConnectionStatus.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Отключиться")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = viewModel::startDemo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Запустить Demo Mode")
            }
        }
        item {
            Text("Доступные устройства", style = MaterialTheme.typography.titleMedium)
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    "Нажмите «Найти устройство». Для Bluetooth Classic Raspberry Pi должен быть видимым или уже сопряженным.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(devices, key = { it.address }) { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedAddress = device.address },
                border = if (selectedAddress == device.address) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedAddress == device.address,
                        onClick = { selectedAddress = device.address },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                        if (device.isBonded) Text("Сопряжено", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { selectedAddress?.let(viewModel::connect) },
                enabled = selectedAddress != null &&
                    state.status != ConnectionStatus.CONNECTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Подключиться")
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: ConnectionStatus,
    deviceName: String?,
    error: String?,
) {
    val color = when (status) {
        ConnectionStatus.CONNECTED, ConnectionStatus.DEMO -> Color(0xFF176B45)
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    val label = when (status) {
        ConnectionStatus.DISCONNECTED -> "Не подключено"
        ConnectionStatus.SEARCHING -> "Поиск устройств"
        ConnectionStatus.CONNECTING -> "Подключение"
        ConnectionStatus.CONNECTED -> "Подключено"
        ConnectionStatus.ERROR -> "Ошибка подключения"
        ConnectionStatus.DEMO -> "Demo Mode"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = color, style = MaterialTheme.typography.titleLarge)
            deviceName?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
