package com.yin.term.domain.repository

import com.yin.term.domain.model.TerminalLog
import kotlinx.coroutines.flow.Flow

interface TerminalLogRepository {

    fun getLogs(): Flow<List<TerminalLog>>

    suspend fun addLog(log: TerminalLog)

    suspend fun clearLogs()
}