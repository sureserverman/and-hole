package org.pihole.android.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pihole.android.App
import org.pihole.android.service.DnsForegroundService
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.pihole.android.feature.diagnostics.DiagnosticsScreen
import org.pihole.android.feature.diagnostics.DiagnosticsViewModelFactory
import org.pihole.android.feature.home.HomeScreen
import org.pihole.android.feature.home.HomeViewModelFactory
import org.pihole.android.feature.home.setup.SetupViewModelFactory
import org.pihole.android.feature.lists.ListsScreen
import org.pihole.android.feature.lists.ListsViewModelFactory
import org.pihole.android.feature.logs.LogsScreen
import org.pihole.android.feature.logs.LogsViewModelFactory
import org.pihole.android.feature.rules.RulesScreen
import org.pihole.android.feature.rules.RulesViewModelFactory
import org.pihole.android.feature.settings.DnsListenerActions
import org.pihole.android.feature.settings.LocalDnsListenerActions
import org.pihole.android.feature.settings.SettingsBackupViewModelFactory
import org.pihole.android.feature.settings.SettingsScreen
import org.pihole.android.data.runtime.DefaultDnsControlRepository

private sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : TopLevelDestination("home", "Home", Icons.Filled.Home)
    data object Rules : TopLevelDestination("rules", "Rules", Icons.Filled.Info)
    data object Logs : TopLevelDestination("logs", "Logs", Icons.Filled.Search)
    data object Lists : TopLevelDestination("lists", "Lists", Icons.Filled.List)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val container = app.appContainer
    val controlRepository =
        remember(container, context) {
            DefaultDnsControlRepository(
                context = context.applicationContext,
                db = container.database,
                prefs = container.preferences,
                startCommand = { dnsContext ->
                    ContextCompat.startForegroundService(
                        dnsContext,
                        Intent(dnsContext, DnsForegroundService::class.java),
                    )
                },
                stopCommand = { dnsContext ->
                    dnsContext.stopService(Intent(dnsContext, DnsForegroundService::class.java))
                },
            )
        }
    val dnsListenerActions = DnsListenerActions(
        startListener = {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DnsForegroundService::class.java),
            )
        },
        stopListener = {
            context.stopService(Intent(context, DnsForegroundService::class.java))
        },
    )
    val navController = rememberNavController()
    val destinations = listOf(
        TopLevelDestination.Home,
        TopLevelDestination.Rules,
        TopLevelDestination.Logs,
        TopLevelDestination.Lists,
        TopLevelDestination.Settings,
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    CompositionLocalProvider(LocalDnsListenerActions provides dnsListenerActions) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            modifier = Modifier.testTag("bottom_nav_${destination.route}"),
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TopLevelDestination.Home.route) {
                    HomeScreen(
                        onOpenDiagnostics = { navController.navigate("diagnostics") },
                        viewModel = viewModel(factory = HomeViewModelFactory(controlRepository)),
                        setupViewModel = viewModel(factory = SetupViewModelFactory(controlRepository, container.preferences)),
                    )
                }
                composable(TopLevelDestination.Rules.route) {
                    RulesScreen(
                        vm = viewModel(factory = RulesViewModelFactory(app, container.database)),
                    )
                }
                composable(TopLevelDestination.Logs.route) {
                    LogsScreen(
                        vm = viewModel(factory = LogsViewModelFactory(app, container.database)),
                    )
                }
                composable(TopLevelDestination.Lists.route) {
                    ListsScreen(
                        viewModel = viewModel(factory = ListsViewModelFactory(app, container.database)),
                    )
                }
                composable(TopLevelDestination.Settings.route) {
                    SettingsScreen(
                        prefs = container.preferences,
                        backupVm = viewModel(factory = SettingsBackupViewModelFactory(app, container.database)),
                        onOpenDiagnostics = { navController.navigate("diagnostics") },
                    )
                }
                composable("diagnostics") {
                    DiagnosticsScreen(
                        vm = viewModel(factory = DiagnosticsViewModelFactory(app, container.database, container.preferences)),
                    )
                }
            }
        }
    }
}
