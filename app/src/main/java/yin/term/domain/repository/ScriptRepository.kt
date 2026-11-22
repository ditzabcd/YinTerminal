package com.yin.term.domain.repository

import com.yin.term.domain.model.Script
import kotlinx.coroutines.flow.Flow

interface ScriptRepository {

    fun getScripts(): Flow<List<Script>>

    suspend fun addScript(command: Script)

    suspend fun deleteScriptWithId(id: Int)
}