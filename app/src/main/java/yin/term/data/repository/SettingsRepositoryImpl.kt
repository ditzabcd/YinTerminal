package com.yin.term.data.repository

import android.graphics.Color
import com.yin.term.core.AppDispatchers
import com.yin.term.data.local.YinLocalStorage
import com.yin.term.data.mapper.toDomain
import com.yin.term.data.mapper.toEntity
import com.yin.term.domain.model.Settings
import com.yin.term.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val storage: YinLocalStorage,
    private val dispatchers: AppDispatchers
) : SettingsRepository {

    override fun getSystemSettings(): Flow<Settings> =
        storage.getSettings().map { it.toDomain() }

    override suspend fun setFirstBootCompleted() = withContext(dispatchers.io) {
        val current = getSystemSettings().first().toEntity()
        storage.updateSettings(current.copy(isFirstBoot = false))
    }

    override suspend fun setLogSuccessFontColor(r: Int, g: Int, b: Int) =
        withContext(dispatchers.io) {
            val current = getSystemSettings().first().toEntity()
            storage.updateSettings(current.copy(logSuccessFontColor = encodeColor(r, g, b)))
        }

    override suspend fun setLogErrorFontColor(r: Int, g: Int, b: Int) =
        withContext(dispatchers.io) {
            val current = getSystemSettings().first().toEntity()
            storage.updateSettings(current.copy(logErrorFontColor = encodeColor(r, g, b)))
        }

    override suspend fun setLogInfoFontColor(r: Int, g: Int, b: Int) =
        withContext(dispatchers.io) {
            val current = getSystemSettings().first().toEntity()
            storage.updateSettings(current.copy(logInfoFontColor = encodeColor(r, g, b)))
        }

    override suspend fun setLogFontSize(size: Int) = withContext(dispatchers.io) {
        val current = getSystemSettings().first().toEntity()
        storage.updateSettings(current.copy(logFontSize = size))
    }

    private fun encodeColor(r: Int, g: Int, b: Int): Int =
        if (r < 0 || g < 0 || b < 0) -1 else Color.rgb(r, g, b)
}