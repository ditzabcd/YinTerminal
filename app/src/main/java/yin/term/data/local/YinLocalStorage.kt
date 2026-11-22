package com.yin.term.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.yin.term.core.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class YinLocalStorage(
    context: Context,
    private val dispatchers: AppDispatchers
) {
    companion object {
        private const val PREFS_NAME = "yinterm_settings"
        private const val KEY_FIRST_BOOT = "is_first_boot"
        private const val KEY_COLOR_SUCCESS = "log_success_color"
        private const val KEY_COLOR_ERROR = "log_error_color"
        private const val KEY_COLOR_INFO = "log_info_color"
        private const val KEY_FONT_SIZE = "log_font_size"

        private const val MAX_TERMINAL_LOGS = 1000
        private const val MAX_HISTORY_ITEMS = 250
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, "history.json")
    private val scriptsFile = File(context.filesDir, "scripts.json")

    private val logLock = Any()
    private val historyLock = Any()
    private val scriptLock = Any()

    private val logBuffer = ArrayDeque<TerminalLogEntity>(MAX_TERMINAL_LOGS)
    private val logIdGen = AtomicInteger(1)

    private val _settingsFlow = MutableStateFlow(readSettingsFromPrefs())
    private val _logsFlow = MutableStateFlow<List<TerminalLogEntity>>(emptyList())
    private val _historyFlow = MutableStateFlow<List<HistoryEntity>>(emptyList())
    private val _scriptsFlow = MutableStateFlow<List<ScriptEntity>>(emptyList())

    private val historyIdGen = AtomicInteger(1)
    private val scriptIdGen = AtomicInteger(1)

    init {
        val loadedHistory = loadHistoryFromDisk()
        _historyFlow.value = loadedHistory
        historyIdGen.set((loadedHistory.maxOfOrNull { it.id } ?: 0) + 1)

        val loadedScripts = loadScriptsFromDisk()
        _scriptsFlow.value = loadedScripts
        scriptIdGen.set((loadedScripts.maxOfOrNull { it.id } ?: 0) + 1)
    }

    // --- SETTINGS ---

    fun getSettings(): Flow<SettingsEntity> = _settingsFlow.asStateFlow()

    suspend fun updateSettings(settings: SettingsEntity) {
        withContext(dispatchers.io) {
            prefs.edit {
                putBoolean(KEY_FIRST_BOOT, settings.isFirstBoot)
                putInt(KEY_COLOR_SUCCESS, settings.logSuccessFontColor)
                putInt(KEY_COLOR_ERROR, settings.logErrorFontColor)
                putInt(KEY_COLOR_INFO, settings.logInfoFontColor)
                putInt(KEY_FONT_SIZE, settings.logFontSize)
            }
            _settingsFlow.value = settings
        }
    }

    private fun readSettingsFromPrefs(): SettingsEntity {
        return SettingsEntity(
            isFirstBoot = prefs.getBoolean(KEY_FIRST_BOOT, true),
            logSuccessFontColor = prefs.getInt(KEY_COLOR_SUCCESS, -1),
            logErrorFontColor = prefs.getInt(KEY_COLOR_ERROR, -1),
            logInfoFontColor = prefs.getInt(KEY_COLOR_INFO, -1),
            logFontSize = prefs.getInt(KEY_FONT_SIZE, 10)
        )
    }

    // --- TERMINAL LOGS (IN-MEMORY CIRCULAR BUFFER) ---

    fun getLogs(): Flow<List<TerminalLogEntity>> = _logsFlow.asStateFlow()

    fun insertLog(log: TerminalLogEntity) {
        synchronized(logLock) {
            if (logBuffer.size >= MAX_TERMINAL_LOGS) {
                logBuffer.removeLast()
            }
            val entry = if (log.id == 0) {
                log.copy(id = logIdGen.getAndIncrement())
            } else {
                log
            }
            // Urutan terbaru di depan (sama dengan ORDER BY id DESC)
            logBuffer.addFirst(entry)
            _logsFlow.value = logBuffer.toList()
        }
    }

    fun deleteLogs() {
        synchronized(logLock) {
            logBuffer.clear()
            _logsFlow.value = emptyList()
        }
    }

    // --- HISTORY ---

    fun getAllHistory(): Flow<List<HistoryEntity>> = _historyFlow.asStateFlow()

    suspend fun insertHistory(command: HistoryEntity) {
        insertHistory(listOf(command))
    }

    suspend fun insertHistory(commands: List<HistoryEntity>) {
        if (commands.isEmpty()) return
        withContext(dispatchers.io) {
            synchronized(historyLock) {
                val current = _historyFlow.value.toMutableList()
                for (item in commands) {
                    val assigned = if (item.id > 0) {
                        current.removeAll { it.id == item.id }
                        item
                    } else {
                        item.copy(id = historyIdGen.getAndIncrement())
                    }
                    current.add(0, assigned)
                }
                val trimmed = if (current.size > MAX_HISTORY_ITEMS) {
                    current.take(MAX_HISTORY_ITEMS)
                } else {
                    current
                }
                _historyFlow.value = trimmed
                saveHistoryToDisk(trimmed)
            }
        }
    }

    suspend fun deleteAllHistory() {
        withContext(dispatchers.io) {
            synchronized(historyLock) {
                _historyFlow.value = emptyList()
                saveHistoryToDisk(emptyList())
            }
        }
    }

    suspend fun deleteHistoryById(id: Int) {
        withContext(dispatchers.io) {
            synchronized(historyLock) {
                val updated = _historyFlow.value.filterNot { it.id == id }
                _historyFlow.value = updated
                saveHistoryToDisk(updated)
            }
        }
    }

    private fun loadHistoryFromDisk(): List<HistoryEntity> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(historyFile.readText())
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        HistoryEntity(
                            id = obj.optInt("id", i + 1),
                            command = obj.optString("command", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistoryToDisk(items: List<HistoryEntity>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("command", item.command)
                }
            )
        }
        atomicWrite(historyFile, arr.toString())
    }

    // --- SCRIPTS ---

    fun getAllScripts(): Flow<List<ScriptEntity>> = _scriptsFlow.asStateFlow()

    suspend fun insertScript(script: ScriptEntity) {
        insertScript(listOf(script))
    }

    suspend fun insertScript(scripts: List<ScriptEntity>) {
        if (scripts.isEmpty()) return
        withContext(dispatchers.io) {
            synchronized(scriptLock) {
                val current = _scriptsFlow.value.toMutableList()
                for (item in scripts) {
                    if (item.id > 0) {
                        val idx = current.indexOfFirst { it.id == item.id }
                        if (idx >= 0) {
                            current[idx] = item
                        } else {
                            current.add(item)
                        }
                    } else {
                        current.add(item.copy(id = scriptIdGen.getAndIncrement()))
                    }
                }
                _scriptsFlow.value = current
                saveScriptsToDisk(current)
            }
        }
    }

    suspend fun deleteAllScripts() {
        withContext(dispatchers.io) {
            synchronized(scriptLock) {
                _scriptsFlow.value = emptyList()
                saveScriptsToDisk(emptyList())
            }
        }
    }

    suspend fun deleteScriptById(id: Int) {
        withContext(dispatchers.io) {
            synchronized(scriptLock) {
                val updated = _scriptsFlow.value.filterNot { it.id == id }
                _scriptsFlow.value = updated
                saveScriptsToDisk(updated)
            }
        }
    }

    private fun loadScriptsFromDisk(): List<ScriptEntity> {
        if (!scriptsFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(scriptsFile.readText())
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ScriptEntity(
                            id = obj.optInt("id", i + 1),
                            name = obj.optString("name", ""),
                            command = obj.optString("command", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveScriptsToDisk(items: List<ScriptEntity>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("command", item.command)
                }
            )
        }
        atomicWrite(scriptsFile, arr.toString())
    }

    private fun atomicWrite(target: File, content: String) {
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        }
    }
}