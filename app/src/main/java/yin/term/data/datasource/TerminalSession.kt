package com.yin.term.data.datasource

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.system.exitProcess

class TerminalSession(
    private val terminalLogRepository: TerminalLogRepository,
    private val dispatchers: AppDispatchers
) {
    companion object {
        private const val PWD_MARKER = "__YINTERM_PWD__:"
        private const val STREAM_BUFFER_SIZE = 8192
        private const val FLUSH_INTERVAL_MS = 16L
        const val CRASH_FILE_NAME = "yinterm_crash.txt"

        @Volatile
        private var crashHandlerInstalled = false

        fun installCrashLogger() {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    val trace = "Thread: ${thread.name}\n$sw"
                    val app = YinTermApp.instance
                    File(app.filesDir, CRASH_FILE_NAME).writeText(trace)
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        fun terminateApplicationPermanently(context: Context? = null) {
            runCatching {
                context?.findActivity()?.finishAndRemoveTask()
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }

        private tailrec fun Context.findActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }

    init {
        installCrashLogger()
    }

    private var sessionScope = CoroutineScope(SupervisorJob() + dispatchers.io)
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
                if (!sessionScope.isActive) {
                    sessionScope = CoroutineScope(SupervisorJob() + dispatchers.io)
                }

                var spawnedProcess: Process? = null
                var rootGranted = false

                if (preferRoot) {
                    val suAttempt = runCatching {
                        ProcessBuilder("su")
                            .redirectErrorStream(false)
                            .start()
                    }.getOrNull()

                    if (suAttempt != null) {
                        delay(100L)
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
                                "Shell init failed: ${err.message ?: "Unknown error"}\n"
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

                // Configure clean environment and sync initial working directory
                writeRawLocked(
                    """
                    export TERM=xterm-256color CLICOLOR=1 FORCE_COLOR=1 LANG=en_US.UTF-8
                    export PATH="/sbin:/system/bin:/system/xbin:/vendor/bin:${'$'}PATH"
                    cd /sdcard 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || cd /
                    printf '%s%s\n' '$PWD_MARKER' "${'$'}PWD"
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun executeCommand(command: String, preferRoot: Boolean = true, context: Context? = null) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (isExitCommand(trimmed)) {
            shutdownAndKillApp(context)
            return
        }

        if (preferRoot != _isRootActive.value && shellProcess?.isAlive == true) {
            restartSession(preferRoot)
        } else {
            ensureSessionStarted(preferRoot)
        }

        if (trimmed == "su" && !_isRootActive.value) {
            restartSession(preferRoot = true)
            if (_isRootActive.value) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Switched to root shell (#)\n")
                )
            } else {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "su: permission denied\n")
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

    fun isExitCommand(rawCommand: String): Boolean {
        val token = rawCommand.trim().substringBefore(' ')
        return token == "exit" || token == "logout" || token == "quit"
    }

    suspend fun shutdownAndKillApp(context: Context? = null) {
        withContext(dispatchers.io) {
            destroyInternal()
            sessionScope.cancel()
        }
        withContext(dispatchers.main) {
            terminateApplicationPermanently(context)
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

            // Send SIGINT to child processes only so the parent interactive shell survives
            val pid = getPid(proc)
            if (pid != -1) {
                runCatching {
                    Runtime.getRuntime()
                        .exec(arrayOf("pkill", "-2", "-P", pid.toString()))
                        .waitFor(250, TimeUnit.MILLISECONDS)
                }
            }
            runCatching {
                stdinWriter?.write("\u0003")
                stdinWriter?.flush()
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

    private fun writeRawLocked(script: String) {
        try {
            val writer = stdinWriter ?: return
            writer.write(script)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            _isRunning.value = false
        }
    }

    private suspend fun streamOutput(stream: InputStream, isErrorStream: Boolean) {
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        val pendingLine = StringBuilder()
        var lastFlushTime = System.currentTimeMillis()

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
                    val now = System.currentTimeMillis()
                    if (now - lastFlushTime >= FLUSH_INTERVAL_MS || pendingLine.contains('\n')) {
                        processStdoutBuffer(pendingLine)
                        lastFlushTime = now
                    }
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
        val batchLines = StringBuilder()

        while (true) {
            val newlineIdx = buffer.indexOf("\n")
            if (newlineIdx == -1) {
                if (buffer.isNotEmpty() && !PWD_MARKER.startsWith(buffer.toString()) && !buffer.startsWith(PWD_MARKER)) {
                    batchLines.append(buffer)
                    buffer.clear()
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
                batchLines.append(lineWithNewline)
            }
        }

        if (batchLines.isNotEmpty()) {
            terminalLogRepository.addLog(
                TerminalLog(TerminalState.Success, batchLines.toString())
            )
        }
    }

    private fun destroyInternal() {
        stdoutJob?.cancel()
        stderrJob?.cancel()
        stdoutJob = null
        stderrJob = null

        runCatching {
            stdinWriter?.write("exit\n")
            stdinWriter?.flush()
            stdinWriter?.close()
        }
        stdinWriter = null

        shellProcess?.let { proc ->
            val pid = getPid(proc)
            if (pid != -1) {
                runCatching {
                    Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-P", pid.toString()))
                }
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