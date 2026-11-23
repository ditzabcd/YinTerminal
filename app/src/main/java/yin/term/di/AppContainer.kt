package com.yin.term.di

import android.content.Context
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.ShellManager
import com.yin.term.data.repository.CommandExecutorImpl
import com.yin.term.data.repository.SettingsRepositoryImpl
import com.yin.term.data.repository.TerminalLogRepositoryImpl
import com.yin.term.domain.repository.CommandExecutor
import com.yin.term.domain.repository.SettingsRepository
import com.yin.term.domain.repository.TerminalLogRepository

class AppContainer(
    private val context: Context,
    private val dispatchers: AppDispatchers = YinTermApp.instance.dispatchers
) {
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(context)
    }

    val terminalLogRepository: TerminalLogRepository by lazy {
        TerminalLogRepositoryImpl()
    }

    val shellManager: ShellManager by lazy {
        ShellManager(
            settingsRepository = settingsRepository,
            terminalLogRepository = terminalLogRepository,
            dispatchers = dispatchers
        )
    }

    val commandExecutor: CommandExecutor by lazy {
        CommandExecutorImpl(
            shellManager = shellManager,
            dispatchers = dispatchers
        )
    }
}