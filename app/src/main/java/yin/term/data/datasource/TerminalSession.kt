package com.yin.term.data.datasource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

class TerminalSession(
    private val terminalLogRepository: TerminalLogRepository,
    private val dispatchers: AppDispatchers
) {
    companion object {
        private const val PWD_MARKER = "__YINTERM_PWD__:"
        private const val STREAM_BUFFER_SIZE = 4096

        @Volatile
        private var crashHandlerInstalled = false

        private fun installCrashLogger() {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    val trace = "Thread: ${thread.name}\n$sw"
                    val app = YinTermApp.instance
                    app.getExternalFilesDir(null)?.let { dir ->
                        File(dir, "yinterm_crash.txt").writeText(trace)
                    }
                    val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("YinTerm Crash", trace))
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    init {
        installCrashLogger()
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val sessionMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var shellProcess: Process? = null

    @Volatile
    private var stdinWriter: BufferedWriter? = null

    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null

    private val _currentDirectory = MutableStateFlow("/")
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    private val _isRootActive = MutableStateFlow(false)
    val isRootActive: StateFlow<Boolean> = _isRootActive.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    suspend fun ensureSessionStarted(preferRoot: Boolean = true) {
        sessionMutex.withLock {
            val existing = shellProcess
            if (existing != null && existing.isAlive) return

            withContext(dispatchers.io) {
                destroyInternal()

                var spawnedProcess: Process? = null
                var rootGranted = false

                if (preferRoot) {
                    val suAttempt = runCatching {
                        ProcessBuilder("su")
                            .redirectErrorStream(false)
                            .start()
                    }.getOrNull()

                    if (suAttempt != null) {
                        delay(120L)
                        if (suAttempt.isAlive) {
                            spawnedProcess = suAttempt
                            rootGranted = true
                        } else {
                            runCatching { suAttempt.destroyForcibly() }
                        }
                    }
                }

                if (spawnedProcess == null) {
                    spawnedProcess = runCatching {
                        ProcessBuilder("/system/bin/sh")
                            .redirectErrorStream(false)
                            .start()
                    }.getOrElse { err ->
                        terminalLogRepository.addLog(
                            TerminalLog(
                                TerminalState.Error,
                                "Failed to initialize shell: ${err.message ?: "Unknown Error"}\n"
                            )
                        )
                        return@withContext
                    }
                    rootGranted = false
                }

                shellProcess = spawnedProcess
                _isRootActive.value = rootGranted
                _isRunning.value = true

                stdinWriter = BufferedWriter(
                    OutputStreamWriter(spawnedProcess.outputStream, StandardCharsets.UTF_8)
                )

                stdoutJob = sessionScope.launch {
                    streamOutput(spawnedProcess.inputStream, isErrorStream = false)
                }
                stderrJob = sessionScope.launch {
                    streamOutput(spawnedProcess.errorStream, isErrorStream = true)
                }

                writeRawLocked(
                    """
                    export TERM=xterm-256color CLICOLOR=1 CLICOLOR_FORCE=1 FORCE_COLOR=1 LANG=en_US.UTF-8
                    cd /sdcard 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || cd /
                    printf '%s%s\n' '$PWD_MARKER' "${'$'}PWD"
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun executeCommand(command: String, preferRoot: Boolean = true) {
        if (preferRoot != _isRootActive.value && shellProcess?.isAlive == true) {
            restartSession(preferRoot)
        } else {
            ensureSessionStarted(preferRoot)
        }

        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (trimmed == "su" && !_isRootActive.value) {
            restartSession(preferRoot = true)
            if (_isRootActive.value) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Switched to ROOT shell (#)\n")
                )
            } else {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "su: Permission denied (device not rooted)\n")
                )
            }
            return
        }

        withContext(dispatchers.io) {
            writeMutex.withLock {
                val proc = shellProcess
                if (proc == null || !proc.isAlive) {
                    ensureSessionStarted(preferRoot)
                }
                val payload = buildString {
                    append(command)
                    append('\n')
                    append("printf '%s%s\\n' '")
                    append(PWD_MARKER)
                    append("' \"\$PWD\"")
                }
                writeRawLocked(payload)
            }
        }
    }

    suspend fun sendInterrupt() {
        withContext(dispatchers.io) {
            val proc = shellProcess
            if (proc == null || !proc.isAlive) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "No active shell session.\n")
                )
                return@withContext
            }

            val pid = getPid(proc)
            if (pid != -1) {
                runCatching {
                    Runtime.getRuntime()
                        .exec(arrayOf("pkill", "-2", "-P", pid.toString()))
                        .waitFor(300, TimeUnit.MILLISECONDS)
                }
            }
            terminalLogRepository.addLog(
                TerminalLog(TerminalState.Error, "^C\n")
            )
        }
    }

    suspend fun restartSession(preferRoot: Boolean = true) {
        sessionMutex.withLock {
            withContext(dispatchers.io) {
                destroyInternal()
            }
        }
        ensureSessionStarted(preferRoot)
    }

    private suspend fun writeRawLocked(script: String) {
        try {
            val writer = stdinWriter ?: return
            writer.write(script)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            terminalLogRepository.addLog(
                TerminalLog(
                    TerminalState.Error,
                    "Shell write error: ${e.message ?: "Broken pipe"}\n"
                )
            )
            _isRunning.value = false
        }
    }

    private suspend fun streamOutput(stream: InputStream, isErrorStream: Boolean) {
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        val pendingLine = StringBuilder()

        try {
            while (sessionScope.isActive) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) break
                if (bytesRead == 0) continue

                val chunk = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)

                if (isErrorStream) {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Error, chunk)
                    )
                } else {
                    pendingLine.append(chunk)
                    processStdoutBuffer(pendingLine)
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!isErrorStream && pendingLine.isNotEmpty()) {
                val remaining = pendingLine.toString()
                if (!remaining.startsWith(PWD_MARKER)) {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, remaining))
                }
            }
        }
    }

    private suspend fun processStdoutBuffer(buffer: StringBuilder) {
        while (true) {
            val newlineIdx = buffer.indexOf("\n")
            if (newlineIdx == -1) {
                if (buffer.isNotEmpty() && !PWD_MARKER.startsWith(buffer.toString()) && !buffer.startsWith(PWD_MARKER)) {
                    val partial = buffer.toString()
                    buffer.clear()
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, partial))
                }
                break
            }

            val lineWithNewline = buffer.substring(0, newlineIdx + 1)
            buffer.delete(0, newlineIdx + 1)

            val trimmedLine = lineWithNewline.trimEnd('\r', '\n')
            if (trimmedLine.startsWith(PWD_MARKER)) {
                val newPwd = trimmedLine.removePrefix(PWD_MARKER).trim()
                if (newPwd.isNotEmpty()) {
                    _currentDirectory.value = newPwd
                }
            } else {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, lineWithNewline)
                )
            }
        }
    }

    private fun destroyInternal() {
        stdoutJob?.cancel()
        stderrJob?.cancel()
        stdoutJob = null
        stderrJob = null

        runCatching { stdinWriter?.close() }
        stdinWriter = null

        shellProcess?.let { proc ->
            val pid = getPid(proc)
            if (pid != -1) {
                runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-P", pid.toString())) }
            }
            runCatching { proc.inputStream.close() }
            runCatching { proc.errorStream.close() }
            runCatching { proc.outputStream.close() }
            runCatching { proc.destroyForcibly() }
        }
        shellProcess = null
        _isRunning.value = false
    }

    private fun getPid(proc: Process): Int {
        return try {
            val field = proc.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(proc)
        } catch (_: Exception) {
            -1
        }
    }
}