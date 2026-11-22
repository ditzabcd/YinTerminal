package com.yin.term.data.repository

import com.yin.term.core.AppDispatchers
import com.yin.term.data.local.YinLocalStorage
import com.yin.term.data.mapper.toDomain
import com.yin.term.data.mapper.toEntity
import com.yin.term.domain.model.Script
import com.yin.term.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ScriptRepositoryImpl(
    private val storage: YinLocalStorage,
    private val dispatchers: AppDispatchers
) : ScriptRepository {

    override fun getScripts(): Flow<List<Script>> =
        storage.getAllScripts().map { list -> list.map { it.toDomain() } }

    override suspend fun addScript(command: Script) = withContext(dispatchers.io) {
        storage.insertScript(command.toEntity())
    }

    override suspend fun deleteScriptWithId(id: Int) = withContext(dispatchers.io) {
        storage.deleteScriptById(id)
    }
}