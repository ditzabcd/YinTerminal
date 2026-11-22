package com.yin.term.data.repository

import com.yin.term.data.datasource.PythonManager
import com.yin.term.data.datasource.ShellManager
import com.yin.term.domain.model.History
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.CommandExecutor
import com.yin.term.domain.repository.HistoryRepository
import com.yin.term.domain.repository.TerminalLogRepository
import java.util.Locale
import kotlin.concurrent.Volatile

enum class ExecutorType { IDLE, SHELL, PYTHON }

class CommandExecutorImpl(
    private val terminalLogRepository: TerminalLogRepository,
    private val historyRepository: HistoryRepository,
    private val shellManager: ShellManager,
    private val pythonManager: PythonManager
) : CommandExecutor {

    @Volatile
    private var currentExecutor = ExecutorType.IDLE

    override suspend fun execute(command: String, isRoot: Boolean) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return

        historyRepository.addHistory(History(trimmedCommand))
        try {
            val lines = trimmedCommand.lines()
            val firstLine = lines.firstOrNull()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (firstLine == "py") {
                currentExecutor = ExecutorType.PYTHON
                val pythonCode = lines.drop(1).joinToString("\n").trim()
                terminalLogRepository.addLog(
                    TerminalLog(
                        TerminalState.Info,
                        "└─> py\n\n$pythonCode\n"
                    )
                )
                pythonManager.execute(pythonCode)
            } else {
                currentExecutor = ExecutorType.SHELL
                terminalLogRepository.addLog(
                    TerminalLog(
                        TerminalState.Info,
                        if (isRoot) "└─> #\n\n$trimmedCommand\n" else "└─> $\n\n$trimmedCommand\n"
                    )
                )
                shellManager.execute(trimmedCommand, isRoot)
            }
        } finally {
            currentExecutor = ExecutorType.IDLE
        }
    }

    override suspend fun cancel() {
        when (currentExecutor) {
            ExecutorType.SHELL -> shellManager.cancel()
            ExecutorType.PYTHON -> pythonManager.cancel()
            ExecutorType.IDLE -> terminalLogRepository.addLog(
                TerminalLog(
                    TerminalState.Error,
                    "There is no active process to terminate.\n"
                )
            )
        }
    }
}