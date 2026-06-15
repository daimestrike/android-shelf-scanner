package ru.shelfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.shelfscanner.ui.AppViewModelFactory
import ru.shelfscanner.ui.ShelfScannerShell
import ru.shelfscanner.ui.connection.ConnectionScreen
import ru.shelfscanner.ui.connection.ConnectionViewModel
import ru.shelfscanner.ui.history.HistoryScreen
import ru.shelfscanner.ui.history.HistoryViewModel
import ru.shelfscanner.ui.scanning.ScanningScreen
import ru.shelfscanner.ui.scanning.ScanningViewModel
import ru.shelfscanner.ui.theme.ShelfScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = AppViewModelFactory((application as ShelfScannerApplication).container)
        setContent {
            ShelfScannerTheme {
                val navController = rememberNavController()
                val backStackEntry = navController.currentBackStackEntryAsState().value
                val route = backStackEntry?.destination?.route ?: "connection"
                ShelfScannerShell(
                    selectedRoute = route,
                    onNavigate = { destination ->
                        navController.navigate(destination) {
                            launchSingleTop = true
                            popUpTo("connection") { saveState = true }
                            restoreState = true
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "connection",
                    ) {
                        composable("connection") {
                            val vm: ConnectionViewModel = viewModel(factory = factory)
                            ConnectionScreen(vm, padding)
                        }
                        composable("scanning") {
                            val vm: ScanningViewModel = viewModel(factory = factory)
                            ScanningScreen(vm, padding)
                        }
                        composable("history") {
                            val vm: HistoryViewModel = viewModel(factory = factory)
                            HistoryScreen(vm, padding)
                        }
                    }
                }
            }
        }
    }
}
