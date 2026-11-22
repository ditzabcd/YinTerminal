package com.yin.term.domain.repository

import com.yin.term.domain.model.History
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {

    fun getHistory(): Flow<List<History>>

    suspend fun addHistory(command: History)

    suspend fun restoreHistory(commands: List<History>)

    suspend fun deleteHistoryWithId(id: Int)

    suspend fun clearHistory()
}