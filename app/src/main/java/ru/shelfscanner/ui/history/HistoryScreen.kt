package ru.shelfscanner.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.shelfscanner.data.model.ScanSession

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCsv by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingCsv.toByteArray(Charsets.UTF_8))
                } ?: error("Не удалось открыть файл")
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) "CSV сохранён" else "Ошибка сохранения CSV",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val export: (List<ScanSession>, String) -> Unit = { items, fileName ->
        pendingCsv = viewModel.exportCsv(items)
        exportLauncher.launch(fileName)
    }

    if (selected != null) {
        SessionDetail(
            session = selected!!,
            onBack = { viewModel.select(null) },
            onExport = {
                export(
                    listOf(selected!!),
                    "scan_${safeFileName(selected!!.sessionId)}.csv",
                )
            },
            contentPadding = contentPadding,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("История сессий", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = { export(sessions, "scan_sessions.csv") },
                enabled = sessions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Экспортировать всю историю в CSV")
            }
        }
        if (sessions.isEmpty()) {
            item { Text("Сохраненных сессий пока нет") }
        }
        items(sessions, key = { it.sessionId }) { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.select(session) },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.sessionId, style = MaterialTheme.typography.titleMedium)
                    Text(session.finishedAt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Уникальных: ${session.uniqueCount}")
                        Text("Дубли: ${session.duplicates}")
                    }
                    Text("${session.status} · ${formatDuration(session.durationMillis)}")
                }
            }
        }
    }
}

@Composable
private fun SessionDetail(
    session: ScanSession,
    onBack: () -> Unit,
    onExport: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Назад") }
        Text("Детали сессии", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.sessionId, style = MaterialTheme.typography.titleLarge)
                Text("Статус: ${session.status}")
                Text("Начало: ${session.startedAt}")
                Text("Завершение: ${session.finishedAt}")
                Text("Длительность: ${formatDuration(session.durationMillis)}")
                Text("Обнаружено: ${session.totalDetected}")
                Text("Уникальных: ${session.uniqueCount}")
                Text("Дубли: ${session.duplicates}")
                Text("Ошибки: ${session.errors}")
                Text("Средняя confidence: ${session.confidenceAvg ?: "—"}")
                Text("Фактически товаров: ${session.actualCount ?: "—"}")
                Text(
                    "Detection Rate: ${
                        session.detectionRate?.let { String.format("%.1f%%", it) } ?: "—"
                    }",
                )
            }
        }
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Экспортировать сессию в CSV")
        }
        Text("Снимок кодов сохранен локально (${session.uniqueCount} шт.)")
    }
}

private fun safeFileName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}м ${seconds}с"
}
