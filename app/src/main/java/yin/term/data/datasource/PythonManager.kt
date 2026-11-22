package com.yin.term.data.datasource

import android.content.Context
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.TerminalLogRepository

class PythonManager(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val terminalLogRepository: TerminalLogRepository,
    @Suppress("UNUSED_PARAMETER") dispatchers: AppDispatchers
) {
    suspend fun execute(@Suppress("UNUSED_PARAMETER") command: String) {
        terminalLogRepository.addLog(
            TerminalLog(
                TerminalState.Error,
                "Python runtime has been removed from this build.\n"
            )
        )
    }

    fun cancel() {
        // No-op: Python runtime removed
    }
}