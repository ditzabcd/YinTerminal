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
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
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
        private const val SHELL_MARKER = "__YINTERM_SH__:"
        private const val STREAM_BUFFER_SIZE = 4096
        private const val FLUSH_INTERVAL_MS = 16L
        private const val SIGINT_NUM = 2
        private const val SIGKILL_NUM = 9
        const val CRASH_FILE_NAME = "yinterm_crash.txt"

        private val CandidateShells = listOf(
            "/system/bin/zsh",
            "/system/xbin/zsh",
            "/data/adb/modules/zsh/system/bin/zsh",
            "/system/bin/bash",
            "/system/xbin/bash",
            "/sbin/bash",
            "/data/adb/modules/bash/system/bin/bash",
            "/system/bin/sh"
        )

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
                    File(YinTermApp.instance.filesDir, CRASH_FILE_NAME).writeText(trace)
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
    private var ptyProcess: NativePty.PtyProcess? = null

    @Volatile
    private var fallbackProcess: Process? = null

    @Volatile
    private var fallbackStdinWriter: BufferedWriter? = null

    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var reaperJob: Job? = null

    @Volatile
    private var terminalRows = 32

    @Volatile
    private var terminalCols = 80

    private val _currentDirectory = MutableStateFlow("/")
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    private val _isRootActive = MutableStateFlow(false)
    val isRootActive: StateFlow<Boolean> = _isRootActive.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeShellName = MutableStateFlow("sh")
    val activeShellName: StateFlow<String> = _activeShellName.asStateFlow()

    private fun isAlive(): Boolean {
        return ptyProcess != null || (fallbackProcess?.isAlive == true)
    }

    private fun resolveBestShellBinary(): String {
        return CandidateShells.firstOrNull { path ->
            val file = File(path)
            file.exists() && file.canExecute()
        } ?: "/system/bin/sh"
    }

    private fun buildBaseEnvArray(): Array<String> {
        val existingPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val mergedPath = "/sbin:/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:$existingPath"
        val homeDir = if (File("/sdcard").canWrite()) "/sdcard" else YinTermApp.instance.filesDir.absolutePath
        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "CLICOLOR=1",
            "FORCE_COLOR=1",
            "LANG=en_US.UTF-8",
            "HOME=$homeDir",
            "PATH=$mergedPath"
        )
    }

    fun resizeTerminal(rows: Int, cols: Int) {
        val safeRows = rows.coerceIn(8, 200)
        val safeCols = cols.coerceIn(20, 300)
        if (safeRows == terminalRows && safeCols == terminalCols) return
        terminalRows = safeRows
        terminalCols = safeCols
        ptyProcess?.let { pty ->
            NativePty.setWindowSize(pty.masterFd, safeRows, safeCols)
        }
    }

    suspend fun ensureSessionStarted(preferRoot: Boolean = true) {
        sessionMutex.withLock {
            if (isAlive()) return

            withContext(dispatchers.io) {
                destroyInternal()
                if (!sessionScope.isActive) {
                    sessionScope = CoroutineScope(SupervisorJob() + dispatchers.io)
                }

                val bestShell = resolveBestShellBinary()
                val shellBaseName = File(bestShell).name
                val startCwd = if (File("/sdcard").canRead()) "/sdcard" else "/"
                val envVars = buildBaseEnvArray()

                var spawnedPty: NativePty.PtyProcess? = null
                var rootGranted = false

                // 1. Primary path: Real Linux C++ PTY (enables native colors, Ctrl+C, and TIOCSWINSZ)
                if (NativePty.isAvailable) {
                    if (preferRoot) {
                        val suArgs = when (shellBaseName) {
                            "zsh" -> arrayOf("su", "-c", "$bestShell -f")
                            "bash" -> arrayOf("su", "-c", "$bestShell --norc --noprofile")
                            else -> arrayOf("su")
                        }
                        val rootPty = NativePty.spawn(
                            cmd = "su",
                            cwd = startCwd,
                            args = suArgs,
                            envVars = envVars,
                            rows = terminalRows,
                            cols = terminalCols
                        )
                        if (rootPty != null) {
                            delay(110L)
                            // Verify su did not immediately exit with permission denied
                            val checkStatus = runCatching {
                                Runtime.getRuntime().exec(arrayOf("kill", "-0", rootPty.pid.toString())).waitFor()
                            }.getOrDefault(-1)
                            if (checkStatus == 0) {
                                spawnedPty = rootPty
                                rootGranted = true
                            } else {
                                NativePty.closePty(rootPty.masterFd)
                                NativePty.waitForProcess(rootPty.pid)
                            }
                        }
                    }

                    if (spawnedPty == null) {
                        val userArgs = when (shellBaseName) {
                            "zsh" -> arrayOf(bestShell, "-f")
                            "bash" -> arrayOf(bestShell, "--norc", "--noprofile")
                            else -> arrayOf("/system/bin/sh")
                        }
                        spawnedPty = NativePty.spawn(
                            cmd = userArgs[0],
                            cwd = startCwd,
                            args = userArgs,
                            envVars = envVars,
                            rows = terminalRows,
                            cols = terminalCols
                        )
                        rootGranted = false
                    }
                }

                if (spawnedPty != null) {
                    ptyProcess = spawnedPty
                    _isRootActive.value = rootGranted
                    _isRunning.value = true
                    _activeShellName.value = shellBaseName

                    writeMutex.withLock {
                        writeRawLocked(buildBootstrapScript(shellBaseName))
                    }

                    stdoutJob = sessionScope.launch {
                        streamPtyOutput(spawnedPty.masterFd)
                    }
                    reaperJob = sessionScope.launch {
                        NativePty.waitForProcess(spawnedPty.pid)
                        if (ptyProcess?.pid == spawnedPty.pid) {
                            _isRunning.value = false
                        }
                    }
                    return@withContext
                }

                // 2. Fallback path: Standard ProcessBuilder pipes if PTY creation is restricted
                var spawnedFallback: Process? = null
                if (preferRoot) {
                    val suCmd = when (shellBaseName) {
                        "zsh" -> arrayOf("su", "-c", "$bestShell -f")
                        "bash" -> arrayOf("su", "-c", "$bestShell --norc --noprofile")
                        else -> arrayOf("su")
                    }
                    val suAttempt = runCatching {
                        ProcessBuilder(*suCmd).redirectErrorStream(false).start()
                    }.getOrNull()

                    if (suAttempt != null) {
                        delay(100L)
                        if (suAttempt.isAlive) {
                            spawnedFallback = suAttempt
                            rootGranted = true
                        } else {
                            runCatching { suAttempt.destroyForcibly() }
                        }
                    }
                }

                if (spawnedFallback == null) {
                    val userCmd = when (shellBaseName) {
                        "zsh" -> arrayOf(bestShell, "-f")
                        "bash" -> arrayOf(bestShell, "--norc", "--noprofile")
                        else -> arrayOf("/system/bin/sh")
                    }
                    spawnedFallback = runCatching {
                        ProcessBuilder(*userCmd).redirectErrorStream(false).start()
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

                fallbackProcess = spawnedFallback
                _isRootActive.value = rootGranted
                _isRunning.value = true
                _activeShellName.value = shellBaseName

                writeMutex.withLock {
                    fallbackStdinWriter = BufferedWriter(
                        OutputStreamWriter(spawnedFallback.outputStream, StandardCharsets.UTF_8)
                    )
                    writeRawLocked(buildBootstrapScript(shellBaseName))
                }

                stdoutJob = sessionScope.launch {
                    streamPipeOutput(spawnedFallback.inputStream, isErrorStream = false)
                }
                stderrJob = sessionScope.launch {
                    streamPipeOutput(spawnedFallback.errorStream, isErrorStream = true)
                }
            }
        }
    }

    private fun buildBootstrapScript(shellBaseName: String): String {
        return """
            export TERM=xterm-256color COLORTERM=truecolor CLICOLOR=1 FORCE_COLOR=1 LANG=en_US.UTF-8
            export PATH="/sbin:/system/bin:/system/xbin:/vendor/bin:${'$'}PATH"
            alias ls='ls --color=auto' 2>/dev/null
            alias ll='ls -la --color=auto' 2>/dev/null
            alias grep='grep --color=auto' 2>/dev/null
            if [ -z "${'$'}BASH_VERSION" ] && [ -z "${'$'}ZSH_VERSION" ]; then
              echo() {
                case "${'$'}1" in
                  -e) shift; printf '%b\n' "${'$'}*" ;;
                  -ne|-en) shift; printf '%b' "${'$'}*" ;;
                  -n) shift; printf '%s' "${'$'}*" ;;
                  *) printf '%s\n' "${'$'}*" ;;
                esac
              }
            fi
            cd /sdcard 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || cd /
            printf '%s%s\n%s%s\n' '$PWD_MARKER' "${'$'}PWD" '$SHELL_MARKER' '$shellBaseName'
        """.trimIndent()
    }

    suspend fun executeCommand(command: String, preferRoot: Boolean = true, context: Context? = null) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (isExitCommand(trimmed)) {
            shutdownAndKillApp(context)
            return
        }

        if (preferRoot != _isRootActive.value && isAlive()) {
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

        if (!isAlive()) {
            ensureSessionStarted(preferRoot)
        }

        withContext(dispatchers.io) {
            writeMutex.withLock {
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
        val firstSegment = rawCommand.trim().substringBefore(';').substringBefore('&').trim()
        val token = firstSegment.substringBefore(' ')
        return token == "exit" || token == "logout" || token == "quit"
    }

    suspend fun shutdownAndKillApp(context: Context? = null) {
        sessionMutex.withLock {
            withContext(dispatchers.io) {
                destroyInternal()
                sessionScope.cancel()
            }
        }
        withContext(dispatchers.main) {
            terminateApplicationPermanently(context)
        }
    }

    suspend fun sendInterrupt() {
        withContext(dispatchers.io) {
            val pty = ptyProcess
            if (pty != null) {
                writeMutex.withLock {
                    NativePty.writeString(pty.masterFd, "\u0003")
                }
                NativePty.sendSignal(pty.pid, SIGINT_NUM)
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "^C\n")
                )
                return@withContext
            }

            val proc = fallbackProcess
            if (proc == null || !proc.isAlive) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "No active shell session.\n")
                )
                return@withContext
            }

            val pid = getFallbackPid(proc)
            if (pid != -1) {
                runCatching {
                    Runtime.getRuntime()
                        .exec(arrayOf("pkill", "-2", "-P", pid.toString()))
                        .waitFor(250, TimeUnit.MILLISECONDS)
                }
            }
            writeMutex.withLock {
                runCatching {
                    fallbackStdinWriter?.write("\u0003")
                    fallbackStdinWriter?.flush()
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

    private fun writeRawLocked(script: String) {
        val pty = ptyProcess
        if (pty != null) {
            val ok = NativePty.writeString(pty.masterFd, "$script\n")
            if (!ok) _isRunning.value = false
            return
        }

        try {
            val writer = fallbackStdinWriter ?: return
            writer.write(script)
            writer.newLine()
            writer.flush()
        } catch (_: Exception) {
            _isRunning.value = false
        }
    }

    private suspend fun streamPtyOutput(masterFd: Int) {
        val byteBuf = ByteArray(STREAM_BUFFER_SIZE)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val charBuf = CharBuffer.allocate(STREAM_BUFFER_SIZE)
        val pendingLine = StringBuilder()
        var lastFlushTime = System.currentTimeMillis()

        try {
            while (sessionScope.isActive) {
                val bytesRead = NativePty.readFromPty(masterFd, byteBuf)
                if (bytesRead <= 0) break

                charBuf.clear()
                decoder.decode(ByteBuffer.wrap(byteBuf, 0, bytesRead), charBuf, false)
                charBuf.flip()
                if (charBuf.remaining() == 0) continue

                pendingLine.append(charBuf)
                val now = System.currentTimeMillis()
                if (now - lastFlushTime >= FLUSH_INTERVAL_MS || pendingLine.indexOf("\n") != -1) {
                    processStdoutBuffer(pendingLine)
                    lastFlushTime = now
                }
            }
        } catch (_: Exception) {
        } finally {
            if (pendingLine.isNotEmpty()) {
                processStdoutBuffer(pendingLine)
                val leftover = pendingLine.toString()
                if (leftover.isNotEmpty() && !leftover.startsWith(PWD_MARKER) && !leftover.startsWith(SHELL_MARKER)) {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Success, leftover)
                    )
                }
            }
        }
    }

    private suspend fun streamPipeOutput(stream: InputStream, isErrorStream: Boolean) {
        val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
        val buffer = CharArray(STREAM_BUFFER_SIZE)
        val pendingLine = StringBuilder()
        var lastFlushTime = System.currentTimeMillis()

        try {
            while (sessionScope.isActive) {
                val charsRead = reader.read(buffer)
                if (charsRead == -1) break
                if (charsRead == 0) continue

                val chunk = String(buffer, 0, charsRead)

                if (isErrorStream) {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Error, chunk)
                    )
                } else {
                    pendingLine.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastFlushTime >= FLUSH_INTERVAL_MS || pendingLine.indexOf("\n") != -1) {
                        processStdoutBuffer(pendingLine)
                        lastFlushTime = now
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!isErrorStream) {
                if (pendingLine.isNotEmpty()) {
                    processStdoutBuffer(pendingLine)
                    val leftover = pendingLine.toString()
                    if (leftover.isNotEmpty() && !leftover.startsWith(PWD_MARKER) && !leftover.startsWith(SHELL_MARKER)) {
                        terminalLogRepository.addLog(
                            TerminalLog(TerminalState.Success, leftover)
                        )
                    }
                }
                if (!isAlive()) {
                    _isRunning.value = false
                }
            }
        }
    }

    private suspend fun processStdoutBuffer(buffer: StringBuilder) {
        val batchLines = StringBuilder()

        while (true) {
            val newlineIdx = buffer.indexOf("\n")
            if (newlineIdx == -1) {
                val current = buffer.toString()
                if (current.isNotEmpty() &&
                    !PWD_MARKER.startsWith(current) &&
                    !SHELL_MARKER.startsWith(current) &&
                    !current.contains(PWD_MARKER) &&
                    !current.contains(SHELL_MARKER)
                ) {
                    batchLines.append(current)
                    buffer.clear()
                }
                break
            }

            val lineWithNewline = buffer.substring(0, newlineIdx + 1)
            buffer.delete(0, newlineIdx + 1)

            val trimmedLine = lineWithNewline.trimEnd('\r', '\n')
            val pwdIdx = trimmedLine.indexOf(PWD_MARKER)
            val shellIdx = trimmedLine.indexOf(SHELL_MARKER)

            when {
                pwdIdx != -1 -> {
                    if (pwdIdx > 0) {
                        batchLines.append(trimmedLine.substring(0, pwdIdx)).append('\n')
                    }
                    val newPwd = trimmedLine.substring(pwdIdx + PWD_MARKER.length).trim()
                    if (newPwd.isNotEmpty()) {
                        _currentDirectory.value = newPwd
                    }
                }
                shellIdx != -1 -> {
                    if (shellIdx > 0) {
                        batchLines.append(trimmedLine.substring(0, shellIdx)).append('\n')
                    }
                    val detectedShell = trimmedLine.substring(shellIdx + SHELL_MARKER.length).trim()
                    if (detectedShell.isNotEmpty()) {
                        _activeShellName.value = detectedShell
                    }
                }
                else -> batchLines.append(lineWithNewline)
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
        reaperJob?.cancel()
        stdoutJob = null
        stderrJob = null
        reaperJob = null

        ptyProcess?.let { pty ->
            NativePty.writeString(pty.masterFd, "exit\n")
            NativePty.sendSignal(pty.pid, SIGKILL_NUM)
            NativePty.closePty(pty.masterFd)
            NativePty.waitForProcess(pty.pid)
        }
        ptyProcess = null

        runCatching {
            fallbackStdinWriter?.write("exit\n")
            fallbackStdinWriter?.flush()
            fallbackStdinWriter?.close()
        }
        fallbackStdinWriter = null

        fallbackProcess?.let { proc ->
            val pid = getFallbackPid(proc)
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
        fallbackProcess = null
        _isRunning.value = false
    }

    private fun getFallbackPid(proc: Process): Int {
        return try {
            val field = proc.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(proc)
        } catch (_: Exception) {
            -1
        }
    }
}