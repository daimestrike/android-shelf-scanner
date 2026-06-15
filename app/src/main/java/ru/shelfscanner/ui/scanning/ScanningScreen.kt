package ru.shelfscanner.ui.scanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.shelfscanner.data.model.ScanStatus
import ru.shelfscanner.domain.ActiveScanSession
import java.util.Locale

@Composable
fun ScanningScreen(
    viewModel: ScanningViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actualCountText by remember { mutableStateOf("") }
    LaunchedEffect(state.actualCount) {
        actualCountText = state.actualCount?.toString().orEmpty()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Сканирование полки", style = MaterialTheme.typography.headlineSmall)
        }
        item { ScanSummary(state) }
        item {
            OutlinedTextField(
                value = actualCountText,
                onValueChange = { raw ->
                    val filtered = raw.filter(Char::isDigit).take(6)
                    actualCountText = filtered
                    viewModel.setActualCount(filtered.toIntOrNull())
                },
                label = { Text("Фактическое количество товаров на полке") },
                supportingText = {
                    Text("Нужно для расчёта Detection Rate")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.infoMessage?.let { message ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        state.uiError?.let { error ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = viewModel::start, modifier = Modifier.weight(1f)) {
                    Text("Начать сессию")
                }
                Button(
                    onClick = viewModel::finish,
                    enabled = state.sessionId != null && state.status != ScanStatus.FINISHED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Завершить")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) {
                    Text("Очистить")
                }
                OutlinedButton(
                    onClick = viewModel::save,
                    enabled = state.sessionId != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isSaved) "Сохранено" else "Сохранить результат")
                }
            }
        }
        item {
            Text("Найденные Data Matrix", style = MaterialTheme.typography.titleMedium)
        }
        if (state.codes.isEmpty()) {
            item { Text("Коды пока не получены") }
        }
        items(state.codes, key = { it.value }) { code ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(code.value, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            code.cameraId,
                            code.confidence?.let { "confidence ${formatConfidence(it)}" },
                            code.firstSeen,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        state.lastRawMessage?.let { raw ->
            item {
                Text("Последнее сырое сообщение", style = MaterialTheme.typography.titleSmall)
                Text(raw, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScanSummary(state: ActiveScanSession) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("УНИКАЛЬНЫХ КОДОВ", style = MaterialTheme.typography.labelLarge)
            Text(
                state.uniqueCount.toString(),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Статус: ${state.status.name}")
            Text("Session ID: ${state.sessionId ?: "—"}")
            Text("Дубли: ${state.duplicates}  ·  Ошибки: ${state.errors}")
            Text("Средняя confidence: ${state.confidenceAvg?.let(::formatConfidence) ?: "—"}")
            Text("Фактически товаров: ${state.actualCount ?: "—"}")
            Text(
                "Detection Rate: ${
                    state.detectionRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
                }",
                fontWeight = FontWeight.SemiBold,
            )
            Text("Начало: ${state.startedAt ?: "—"}")
            Text("Обновлено: ${state.lastUpdatedAt ?: "—"}")
        }
    }
}

private fun formatConfidence(value: Double): String =
    String.format(Locale.US, "%.2f", value)
