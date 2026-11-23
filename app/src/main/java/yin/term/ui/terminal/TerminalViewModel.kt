package com.yin.term.ui.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.ShellManager
import com.yin.term.data.datasource.TerminalSession
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.CommandExecutor
import com.yin.term.domain.repository.SettingsRepository
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TerminalViewModel(
    private val commandExecutor: CommandExecutor,
    private val dispatchers: AppDispatchers,
    private val settingsRepository: SettingsRepository,
    private val terminalLogRepository: TerminalLogRepository
) : ViewModel() {

    private val shellManager: ShellManager = ShellManager.instance ?: ShellManager(
        settingsRepository = settingsRepository,
        terminalLogRepository = terminalLogRepository,
        dispatchers = dispatchers
    )

    val themeStore = TerminalThemeStore(YinTermApp.instance)

    private val _uiState = MutableStateFlow(TerminalUiState(isRoot = true))
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    val currentDirectory: StateFlow<String> = shellManager.currentDirectory
    val isRootActive: StateFlow<Boolean> = shellManager.isRootActive
    val activeShellName: StateFlow<String> = shellManager.terminalSession.activeShellName

    init {
        viewModelScope.launch(dispatchers.io) {
            reportPreviousCrashIfAny()
            shellManager.initialize(preferRoot = true)

            if (settingsRepository.getSystemSettings().first().isFirstBoot) {
                showCleanShellHeader(
                    isRoot = shellManager.isRootActive.value,
                    shellName = shellManager.terminalSession.activeShellName.value
                )
                settingsRepository.setFirstBootCompleted()
            }
        }

        viewModelScope.launch {
            shellManager.isRootActive.collect { activeRoot ->
                _uiState.update { it.copy(isRoot = activeRoot) }
            }
        }

        viewModelScope.launch {
            combine(
                terminalLogRepository.getLogs(),
                settingsRepository.getSystemSettings()
            ) { latestLogs, latestSettings ->
                latestLogs.toList() to latestSettings
            }
                .conflate()
                .flowOn(dispatchers.io)
                .collect { (snapshotLogs, latestSettings) ->
                    _uiState.update {
                        it.copy(
                            settings = latestSettings,
                            logs = snapshotLogs
                        )
                    }
                }
        }
    }

    fun onEvent(event: TerminalUiEvent) {
        when (event) {
            is TerminalUiEvent.Execute -> execute(null)
            is TerminalUiEvent.Terminate -> terminate()
            is TerminalUiEvent.ToggleRoot -> toggleRootMode()
            is TerminalUiEvent.ClearOutput -> clearOutput()
            is TerminalUiEvent.OnCommandChange -> _uiState.update { it.copy(command = event.newCommand) }
            is TerminalUiEvent.ToggleToolsMenu -> _uiState.update { it.copy(isToolsMenuOpen = event.show) }
        }
    }

    fun updateTerminalSize(rows: Int, cols: Int) {
        shellManager.terminalSession.resizeTerminal(rows, cols)
    }

    fun executeWithContext(context: Context) {
        execute(context)
    }

    fun exitAndKillApp(context: Context) {
        viewModelScope.launch {
            shellManager.terminalSession.shutdownAndKillApp(context)
        }
    }

    fun restartSession() {
        viewModelScope.launch(dispatchers.io) {
            val targetRoot = _uiState.value.isRoot
            shellManager.terminalSession.restartSession(preferRoot = targetRoot)
            val actualRoot = shellManager.isRootActive.value
            _uiState.update { it.copy(isRoot = actualRoot, executionState = TerminalState.Idle) }
            val modeTag = if (actualRoot) "uid=0(root)" else "uid=2000(shell)"
            terminalLogRepository.addLog(
                TerminalLog(TerminalState.Success, "Session restarted [$modeTag]\n")
            )
        }
    }

    private fun terminate() {
        viewModelScope.launch(dispatchers.io) {
            shellManager.terminalSession.sendInterrupt()
            _uiState.update { it.copy(executionState = TerminalState.Idle) }
        }
    }

    private suspend fun reportPreviousCrashIfAny() {
        runCatching {
            val crashFile = File(YinTermApp.instance.filesDir, TerminalSession.CRASH_FILE_NAME)
            if (crashFile.exists()) {
                val trace = crashFile.readText().take(1800)
                crashFile.delete()
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "[CRASH LOG]\n$trace\n")
                )
            }
        }
    }

    private fun toggleRootMode() {
        viewModelScope.launch(dispatchers.io) {
            val targetRoot = !_uiState.value.isRoot
            shellManager.terminalSession.restartSession(preferRoot = targetRoot)
            val actualRoot = shellManager.isRootActive.value
            _uiState.update { it.copy(isRoot = actualRoot, executionState = TerminalState.Idle) }

            if (targetRoot && !actualRoot) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "su: permission denied (using shell $)\n")
                )
            } else {
                val modeLabel = if (actualRoot) "root (#)" else "user ($)"
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Switched to $modeLabel\n")
                )
            }
        }
    }

    private fun clearOutput() {
        viewModelScope.launch(dispatchers.io) {
            terminalLogRepository.clearLogs()
        }
    }

    private fun execute(context: Context?) {
        val current = _uiState.value
        val cmd = current.command.trim()
        if (cmd.isEmpty()) return

        if (shellManager.terminalSession.isExitCommand(cmd)) {
            _uiState.update { it.copy(command = "") }
            viewModelScope.launch {
                shellManager.terminalSession.shutdownAndKillApp(context)
            }
            return
        }

        if (cmd == "clear") {
            _uiState.update { it.copy(command = "") }
            clearOutput()
            return
        }

        val rootMode = current.isRoot
        val promptChar = if (rootMode) "#" else "$"
        _uiState.update { it.copy(command = "", executionState = TerminalState.Running) }

        viewModelScope.launch {
            try {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Info, "$promptChar $cmd\n")
                )
                commandExecutor.execute(cmd, rootMode)
            } finally {
                _uiState.update { it.copy(executionState = TerminalState.Idle) }
            }
        }
    }

    private suspend fun showCleanShellHeader(isRoot: Boolean, shellName: String) {
        val shellType = if (isRoot) "root" else "user"
        terminalLogRepository.addLog(
            TerminalLog(
                TerminalState.Success,
                "YinTerm [$shellName:$shellType] ready. Long-press anywhere for options.\n"
            )
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = YinTermApp.instance
                return TerminalViewModel(
                    commandExecutor = app.container.commandExecutor,
                    dispatchers = app.dispatchers,
                    settingsRepository = app.container.settingsRepository,
                    terminalLogRepository = app.container.terminalLogRepository
                ) as T
            }
        }
    }
}