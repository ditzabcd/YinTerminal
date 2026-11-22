package com.yin.term.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yin.term.ui.navigation.AppDestinations
import com.yin.term.ui.navigation.AppNavigation

@Composable
fun Main() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val onNavigate: (AppDestinations) -> Unit = { dest ->
        navController.navigate(dest.name) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            NavigationRail {
                AppDestinations.entries.forEach { dest ->
                    NavigationRailItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.name,
                        onClick = { onNavigate(dest) }
                    )
                }
            }
            AppNavigation(
                navController = navController,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Scaffold(
            modifier = Modifier.imePadding(),
            bottomBar = {
                NavigationBar {
                    AppDestinations.entries.forEach { dest ->
                        NavigationBarItem(
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            selected = currentRoute == dest.name,
                            onClick = { onNavigate(dest) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            AppNavigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}