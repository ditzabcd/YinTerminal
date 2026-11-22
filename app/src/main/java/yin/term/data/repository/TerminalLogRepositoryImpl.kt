package com.yin.term.data.repository

import com.yin.term.data.local.YinLocalStorage
import com.yin.term.data.mapper.toDomain
import com.yin.term.data.mapper.toEntity
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TerminalLogRepositoryImpl(
    private val storage: YinLocalStorage
) : TerminalLogRepository {

    override fun getLogs(): Flow<List<TerminalLog>> =
        storage.getLogs().map { list -> list.map { it.toDomain() } }

    override suspend fun addLog(log: TerminalLog) {
        storage.insertLog(log.toEntity())
    }

    override suspend fun clearLogs() {
        storage.deleteLogs()
    }
}