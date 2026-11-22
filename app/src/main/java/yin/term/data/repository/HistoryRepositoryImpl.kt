package com.yin.term.data.repository

import com.yin.term.core.AppDispatchers
import com.yin.term.data.local.YinLocalStorage
import com.yin.term.data.mapper.toDomain
import com.yin.term.data.mapper.toEntity
import com.yin.term.domain.model.History
import com.yin.term.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HistoryRepositoryImpl(
    private val storage: YinLocalStorage,
    private val dispatchers: AppDispatchers
) : HistoryRepository {

    override fun getHistory(): Flow<List<History>> =
        storage.getAllHistory().map { list -> list.map { it.toDomain() } }

    override suspend fun addHistory(command: History) = withContext(dispatchers.io) {
        storage.insertHistory(command.toEntity())
    }

    override suspend fun restoreHistory(commands: List<History>) = withContext(dispatchers.io) {
        storage.insertHistory(commands.map { it.toEntity() })
    }

    override suspend fun deleteHistoryWithId(id: Int) = withContext(dispatchers.io) {
        storage.deleteHistoryById(id)
    }

    override suspend fun clearHistory() = withContext(dispatchers.io) {
        storage.deleteAllHistory()
    }
}