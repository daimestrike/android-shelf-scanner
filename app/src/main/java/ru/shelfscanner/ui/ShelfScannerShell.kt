package ru.shelfscanner.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

private data class NavItem(val route: String, val label: String, val marker: String)

@Composable
fun ShelfScannerShell(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val items = listOf(
        NavItem("connection", "Подключение", "BT"),
        NavItem("scanning", "Сканирование", "SCAN"),
        NavItem("history", "История", "LOG"),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        icon = { Text(item.marker) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        content = content,
    )
}
