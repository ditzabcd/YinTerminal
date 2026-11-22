package com.yin.term.data.local

import com.yin.term.domain.model.TerminalState

data class SettingsEntity(
    val isFirstBoot: Boolean = true,
    val logSuccessFontColor: Int = -1,
    val logErrorFontColor: Int = -1,
    val logInfoFontColor: Int = -1,
    val logFontSize: Int = 10,
    val id: Int = 1
)

data class TerminalLogEntity(
    val state: TerminalState,
    val message: String,
    val date: Long,
    val id: Int = 0
)

data class HistoryEntity(
    val command: String,
    val id: Int = 0
)

data class ScriptEntity(
    val name: String,
    val command: String,
    val id: Int = 0
)