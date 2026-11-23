package com.yin.term.di

import android.content.Context
import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.ShellManager
import com.yin.term.data.local.YinLocalStorage
import com.yin.term.data.repository.CommandExecutorImpl
import com.yin.term.data.repository.HistoryRepositoryImpl
import com.yin.term.data.repository.ScriptRepositoryImpl
import com.yin.term.data.repository.SettingsRepositoryImpl
import com.yin.term.data.repository.TerminalLogRepositoryImpl
import com.yin.term.domain.repository.CommandExecutor
import com.yin.term.domain.repository.HistoryRepository
import com.yin.term.domain.repository.ScriptRepository
import com.yin.term.domain.repository.SettingsRepository
import com.yin.term.domain.repository.TerminalLogRepository

class AppContainer(
    context: Context,
    val dispatchers: AppDispatchers = AppDispatchers()
) {
    private val appContext = context.applicationContext

    private val localStorage: YinLocalStorage by lazy {
        YinLocalStorage(appContext, dispatchers)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(localStorage, dispatchers)
    }

    val terminalLogRepository: TerminalLogRepository by lazy {
        TerminalLogRepositoryImpl(localStorage)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepositoryImpl(localStorage, dispatchers)
    }

    val scriptRepository: ScriptRepository by lazy {
        ScriptRepositoryImpl(localStorage, dispatchers)
    }

    val shellManager: ShellManager by lazy {
        ShellManager(settingsRepository, terminalLogRepository, dispatchers)
    }

    val commandExecutor: CommandExecutor by lazy {
        CommandExecutorImpl(
            shellManager = shellManager,
            dispatchers = dispatchers
        )
    }
}