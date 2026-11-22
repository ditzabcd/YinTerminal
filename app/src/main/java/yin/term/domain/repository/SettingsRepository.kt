package com.yin.term.domain.repository

import com.yin.term.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun getSystemSettings(): Flow<Settings>

    suspend fun setFirstBootCompleted()

    suspend fun setLogSuccessFontColor(r: Int, g: Int, b: Int)

    suspend fun setLogErrorFontColor(r: Int, g: Int, b: Int)

    suspend fun setLogInfoFontColor(r: Int, g: Int, b: Int)

    suspend fun setLogFontSize(size: Int)
}