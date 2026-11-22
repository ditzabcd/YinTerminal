package com.yin.term.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.yin.term.ui.history.History
import com.yin.term.ui.script.Script
import com.yin.term.ui.terminal.Terminal

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.TERMINAL.name,
        modifier = modifier
    ) {
        composable(AppDestinations.TERMINAL.name) {
            Terminal(terminalCommandChannel = appViewModel.terminalCommandChannel)
        }
        composable(AppDestinations.HISTORY.name) {
            History(onHistoryItemExecuteClick = { command ->
                appViewModel.onItemExecuteClicked(command)
                navController.navigate(AppDestinations.TERMINAL.name) {
                    popUpTo(AppDestinations.TERMINAL.name) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
        composable(AppDestinations.Scripts.name) {
            Script(onScriptItemExecuteClick = { command ->
                appViewModel.onItemExecuteClicked(command)
                navController.navigate(AppDestinations.TERMINAL.name) {
                    popUpTo(AppDestinations.TERMINAL.name) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
    }
}