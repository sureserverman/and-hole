package org.pihole.android.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.pihole.android.App
import org.pihole.android.core.designsystem.components.AhNavBar
import org.pihole.android.core.designsystem.components.AhNavItem
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.data.runtime.DefaultDnsControlRepository
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
import org.pihole.android.service.DnsForegroundService

private sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : TopLevelDestination("home", "Home", AhIcons.Home)
    data object Rules : TopLevelDestination("rules", "Rules", AhIcons.Rules)
    data object Logs : TopLevelDestination("logs", "Logs", AhIcons.Search)
    data object Lists : TopLevelDestination("lists", "Lists", AhIcons.ListIcon)
    data object Settings : TopLevelDestination("settings", "Settings", AhIcons.Settings)
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
        TopLevelDestination.Logs,
        TopLevelDestination.Lists,
        TopLevelDestination.Rules,
        TopLevelDestination.Settings,
    )
    val navItems = destinations.map { AhNavItem(it.route, it.label, it.icon) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selectedKey = destinations.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }?.route ?: TopLevelDestination.Home.route

    CompositionLocalProvider(LocalDnsListenerActions provides dnsListenerActions) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AhNavBar(
                    items = navItems,
                    selectedKey = selectedKey,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    itemModifier = { route -> Modifier.testTag("bottom_nav_$route") },
                )
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
