package com.yin.term.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.ShellManager
import com.yin.term.domain.model.Settings
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.CommandExecutor
import com.yin.term.domain.repository.SettingsRepository
import com.yin.term.domain.repository.TerminalLogRepository
import com.yin.term.ui.theme.ErrorTextColor
import com.yin.term.ui.theme.InfoTextColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _uiState = MutableStateFlow(TerminalUiState(isRoot = true))
    val uiState = _uiState.asStateFlow()

    val currentDirectory: StateFlow<String> = shellManager.currentDirectory
    val isRootActive: StateFlow<Boolean> = shellManager.isRootActive

    init {
        viewModelScope.launch(dispatchers.io) {
            shellManager.initialize(preferRoot = true)
            _uiState.update { it.copy(isRoot = shellManager.isRootActive.value) }

            if (settingsRepository.getSystemSettings().first().isFirstBoot) {
                showWelcomeMessage()
                settingsRepository.setFirstBootCompleted()
            }
        }

        viewModelScope.launch {
            combine(
                terminalLogRepository.getLogs(),
                settingsRepository.getSystemSettings()
            ) { latestLogs, latestSettings ->
                latestLogs to latestSettings
            }
                .conflate()
                .flowOn(dispatchers.io)
                .collect { (latestLogs, latestSettings) ->
                    val renderedText = generateTerminalLogText(latestLogs, latestSettings)
                    _uiState.update {
                        it.copy(
                            settings = latestSettings,
                            logs = latestLogs,
                            terminalLogText = renderedText
                        )
                    }
                }
        }
    }

    fun onEvent(event: TerminalUiEvent) {
        when (event) {
            is TerminalUiEvent.Execute -> execute()
            is TerminalUiEvent.Terminate -> terminate()
            is TerminalUiEvent.ToggleRoot -> toggleRootMode()
            is TerminalUiEvent.ClearOutput -> clearOutput()
            is TerminalUiEvent.OnCommandChange -> _uiState.update { it.copy(command = event.newCommand) }
            is TerminalUiEvent.ToggleToolsMenu -> _uiState.update { it.copy(isToolsMenuOpen = event.show) }
        }
    }

    fun restartSession() {
        viewModelScope.launch(dispatchers.io) {
            val targetRoot = _uiState.value.isRoot
            shellManager.terminalSession.restartSession(preferRoot = targetRoot)
            _uiState.update { it.copy(isRoot = shellManager.isRootActive.value) }
            terminalLogRepository.addLog(
                TerminalLog(TerminalState.Success, "Shell session restarted.\n")
            )
        }
    }

    private fun toggleRootMode() {
        viewModelScope.launch(dispatchers.io) {
            val targetRoot = !_uiState.value.isRoot
            shellManager.terminalSession.restartSession(preferRoot = targetRoot)
            val actualRoot = shellManager.isRootActive.value
            _uiState.update { it.copy(isRoot = actualRoot) }

            if (targetRoot && !actualRoot) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "su: Permission denied (fallback to shell $)\n")
                )
            } else {
                val modeLabel = if (actualRoot) "ROOT (#)" else "SHELL ($)"
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Switched to $modeLabel\n")
                )
            }
        }
    }

    private suspend fun generateTerminalLogText(
        latestLogs: List<TerminalLog>,
        settings: Settings
    ): AnnotatedString = withContext(dispatchers.default) {
        val infoColor = if (settings.logInfoFontColor == -1) InfoTextColor else Color(settings.logInfoFontColor)
        val errorColor = if (settings.logErrorFontColor == -1) ErrorTextColor else Color(settings.logErrorFontColor)
        val successColor = if (settings.logSuccessFontColor == -1) Color.Unspecified else Color(settings.logSuccessFontColor)

        buildAnnotatedString {
            for (i in latestLogs.lastIndex downTo 0) {
                val log = latestLogs[i]
                val spanColor = when (log.state) {
                    TerminalState.Info -> infoColor
                    TerminalState.Error -> errorColor
                    else -> successColor
                }
                withStyle(style = SpanStyle(color = spanColor)) {
                    append(log.message)
                    if (!log.message.endsWith('\n')) append('\n')
                }
            }
        }
    }

    private fun clearOutput() {
        viewModelScope.launch(dispatchers.io) {
            terminalLogRepository.clearLogs()
        }
    }

    private fun execute() {
        val current = _uiState.value
        if (current.command.isBlank() || current.executionState != TerminalState.Idle) return

        val cmd = current.command.trim()
        val rootMode = current.isRoot
        _uiState.update { it.copy(command = "", executionState = TerminalState.Running) }

        viewModelScope.launch {
            try {
                commandExecutor.execute(cmd, rootMode)
            } finally {
                _uiState.update { it.copy(executionState = TerminalState.Idle) }
            }
        }
    }

    private fun terminate() {
        viewModelScope.launch {
            commandExecutor.cancel()
        }
    }

    private suspend fun showWelcomeMessage() {
        val welcomeMessage = """
            __   ___     _____                    
            \ \ / (_)_ _|_   _|__ _ _ _ __        
             \ V /| | ' \ | |/ -_) '_| '  \       
              |_| |_|_||_||_|\___|_| |_|_|_|      

            YinTerm v1.0 — Persistent Shell & Python 3.13
            Type 'help' for built-in commands.
        """.trimIndent()

        terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$welcomeMessage\n"))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = YinTermApp.instance
                val executor = app.container.commandExecutor
                return TerminalViewModel(
                    commandExecutor = executor,
                    dispatchers = app.dispatchers,
                    settingsRepository = app.container.settingsRepository,
                    terminalLogRepository = app.container.terminalLogRepository
                ) as T
            }
        }
    }
}