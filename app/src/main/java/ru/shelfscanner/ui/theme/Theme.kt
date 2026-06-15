package ru.shelfscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IndustrialColors = lightColorScheme(
    primary = Color(0xFF176B45),
    onPrimary = Color.White,
    secondary = Color(0xFF475B52),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF4F6F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4E9E6),
)

@Composable
fun ShelfScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColors,
        content = content,
    )
}
