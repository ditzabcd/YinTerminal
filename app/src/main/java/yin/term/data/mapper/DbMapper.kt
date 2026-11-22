package com.yin.term.data.mapper

import com.yin.term.data.local.HistoryEntity
import com.yin.term.data.local.ScriptEntity
import com.yin.term.data.local.SettingsEntity
import com.yin.term.data.local.TerminalLogEntity
import com.yin.term.domain.model.History
import com.yin.term.domain.model.Script
import com.yin.term.domain.model.Settings
import com.yin.term.domain.model.TerminalLog
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOG_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

fun SettingsEntity.toDomain(): Settings = Settings(
    isFirstBoot = isFirstBoot,
    logSuccessFontColor = logSuccessFontColor,
    logErrorFontColor = logErrorFontColor,
    logInfoFontColor = logInfoFontColor,
    logFontSize = logFontSize,
    id = id
)

fun Settings.toEntity(): SettingsEntity = SettingsEntity(
    isFirstBoot = isFirstBoot,
    logSuccessFontColor = logSuccessFontColor,
    logErrorFontColor = logErrorFontColor,
    logInfoFontColor = logInfoFontColor,
    logFontSize = logFontSize,
    id = id
)

fun TerminalLogEntity.toDomain(): TerminalLog {
    val formattedDate = runCatching {
        LOG_DATE_FORMATTER.format(
            Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
        )
    }.getOrDefault("")

    return TerminalLog(
        state = state,
        message = message,
        date = formattedDate,
        id = id
    )
}

fun TerminalLog.toEntity(): TerminalLogEntity {
    val timeMillis = if (date.isBlank()) {
        System.currentTimeMillis()
    } else {
        runCatching {
            LocalDateTime.parse(date, LOG_DATE_FORMATTER)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    return TerminalLogEntity(
        state = state,
        message = message,
        date = timeMillis,
        id = id
    )
}

fun HistoryEntity.toDomain(): History = History(
    command = command,
    id = id
)

fun History.toEntity(): HistoryEntity = HistoryEntity(
    command = command,
    id = id
)

fun ScriptEntity.toDomain(): Script = Script(
    name = name,
    command = command,
    id = id
)

fun Script.toEntity(): ScriptEntity = ScriptEntity(
    name = name,
    command = command,
    id = id
)