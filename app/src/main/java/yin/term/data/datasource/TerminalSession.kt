package com.yin.term.data.datasource

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import com.yin.term.YinTermApp
import com.yin.term.core.AnsiParser
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.system.exitProcess

class TerminalSession(
    private val terminalLogRepository: TerminalLogRepository,
    private val dispatchers: AppDispatchers
) {
    companion object {
        private const val STREAM_BUFFER_SIZE = 4096
        private const val ROOT_HANDSHAKE_TIMEOUT_MS = 12_000L
        private const val USER_HANDSHAKE_TIMEOUT_MS = 3_000L
        private const val SIGINT_NUM = 2
        private const val SIGKILL_NUM = 9
        const val CRASH_FILE_NAME = "yinterm_crash.txt"

        private const val FULL_PATH =
            "/debug_ramdisk:/sbin:/system/bin:/system/xbin:/vendor/bin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk"

        private val CandidateSuPaths = listOf(
            "/system/bin/su",
            "/debug_ramdisk/su",
            "/sbin/su",
            "/system/xbin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su",
            "su"
        )

        private val CandidateShells = listOf(
            "/system/bin/bash",
            "/system/xbin/bash",
            "/sbin/bash",
            "/data/adb/modules/bash/system/bin/bash",
            "/system/bin/sh"
        )

        // Matches ONLY actual executed handshake output (numeric UID), NEVER the echoed $(id -u)
        private val HandshakeRegex = Regex("""__YIN_HS__(\d+)__(/[^\r\n]*)__""")
        private val AnsiStripRegex = Regex("""\u001B\[[0-9:;?<=>]*[ -/]*[@-~]""")
        private val ShellPromptNoiseRegex = Regex("""^(?:[a-zA-Z0-9_.-]+:)?/[^\s]*\s*[#$]\s*$""")

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

    @Volatile
    private var previousDirectory = "/sdcard"

    @Volatile
    private var handshakeCompleted = false

    @Volatile
    private var handshakeSignal: CompletableDeferred<Boolean>? = null

    private val echoSuppressQueue = ConcurrentLinkedQueue<String>()

    private val _currentDirectory = MutableStateFlow("/sdcard")
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

    private fun resolveSuBinary(): String {
        return CandidateSuPaths.firstOrNull { path ->
            if (path == "su") true else File(path).exists()
        } ?: "su"
    }

    private fun resolveBestShellBinary(): String {
        return CandidateShells.firstOrNull { path ->
            val file = File(path)
            file.exists() && file.canExecute()
        } ?: "/system/bin/sh"
    }

    private fun buildBaseEnvArray(): Array<String> {
        val existingPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val homeDir = if (File("/sdcard").canWrite()) "/sdcard" else YinTermApp.instance.filesDir.absolutePath

        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "CLICOLOR=1",
            "FORCE_COLOR=1",
            "LANG=en_US.UTF-8",
            "PS1=",
            "PS2=",
            "PROMPT_COMMAND=",
            "ENV=",
            "HOME=$homeDir",
            "PATH=$FULL_PATH:$existingPath"
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
            if (isAlive() && handshakeCompleted) return

            withContext(dispatchers.io) {
                if (!sessionScope.isActive) {
                    sessionScope = CoroutineScope(SupervisorJob() + dispatchers.io)
                }

                if (preferRoot) {
                    if (startShellAttempt(asRoot = true)) {
                        return@withContext
                    }
                    destroyInternal()
                }

                startShellAttempt(asRoot = false)
            }
        }
    }

    private suspend fun startShellAttempt(asRoot: Boolean): Boolean {
        destroyInternal()
        handshakeCompleted = false

        val bestShell = resolveBestShellBinary()
        val shellBaseName = File(bestShell).name
        val suBin = resolveSuBinary()
        val nonInteractiveShell = if (shellBaseName == "bash") {
            "$bestShell --norc --noprofile -s"
        } else {
            "/system/bin/sh -s"
        }

        val startCwd = if (File("/sdcard").canRead()) "/sdcard" else "/"
        val envVars = buildBaseEnvArray()

        val deferred = CompletableDeferred<Boolean>()
        handshakeSignal = deferred

        val initCommand =
            "stty -echo -echoe -echok -echonl 2>/dev/null; " +
                "export PS1='' PS2='' PROMPT_COMMAND='' TERM=xterm-256color COLORTERM=truecolor CLICOLOR=1 FORCE_COLOR=1 PATH='$FULL_PATH:'\"\$PATH\"; " +
                "alias ls='ls --color=auto' 2>/dev/null; alias ll='ls -la --color=auto' 2>/dev/null; alias grep='grep --color=auto' 2>/dev/null; " +
                "cd /sdcard 2>/dev/null || cd \"\$HOME\" 2>/dev/null || cd /; " +
                "echo \"__YIN_HS__\$(id -u)__\$PWD__\""

        if (NativePty.isAvailable) {
            val cmd = if (asRoot) suBin else bestShell
            val args = if (asRoot) {
                arrayOf("su")
            } else if (shellBaseName == "bash") {
                arrayOf(bestShell, "--norc", "--noprofile", "-s")
            } else {
                arrayOf("/system/bin/sh", "-s")
            }

            val spawnedPty = NativePty.spawn(
                cmd = cmd,
                cwd = startCwd,
                args = args,
                envVars = envVars,
                rows = terminalRows,
                cols = terminalCols
            )

            if (spawnedPty != null) {
                ptyProcess = spawnedPty
                _isRunning.value = true
                _activeShellName.value = shellBaseName

                stdoutJob = sessionScope.launch {
                    streamPtyOutput(spawnedPty.masterFd)
                }
                reaperJob = sessionScope.launch {
                    NativePty.waitForProcess(spawnedPty.pid)
                    if (ptyProcess?.pid == spawnedPty.pid) {
                        _isRunning.value = false
                    }
                    deferred.complete(false)
                }

                writeMutex.withLock {
                    if (asRoot) {
                        writeRawLocked("stty -echo 2>/dev/null; exec $nonInteractiveShell")
                    }
                    writeRawLocked(initCommand)
                }

                val timeout = if (asRoot) ROOT_HANDSHAKE_TIMEOUT_MS else USER_HANDSHAKE_TIMEOUT_MS
                val verified = withTimeoutOrNull(timeout) { deferred.await() } ?: false
                if (verified && isAlive() && (!asRoot || _isRootActive.value)) {
                    handshakeCompleted = true
                    return true
                }
                return false
            }
        }

        // Fallback ProcessBuilder jika PTY diblokir SELinux
        val fallbackCmd = if (asRoot) {
            arrayOf(suBin)
        } else if (shellBaseName == "bash") {
            arrayOf(bestShell, "--norc", "--noprofile", "-s")
        } else {
            arrayOf("/system/bin/sh", "-s")
        }

        val proc = runCatching {
            ProcessBuilder(*fallbackCmd).redirectErrorStream(false).start()
        }.getOrNull() ?: return false

        fallbackProcess = proc
        _isRunning.value = true
        _activeShellName.value = shellBaseName

        fallbackStdinWriter = BufferedWriter(
            OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8)
        )

        stdoutJob = sessionScope.launch {
            streamPipeOutput(proc.inputStream, isErrorStream = false)
        }
        stderrJob = sessionScope.launch {
            streamPipeOutput(proc.errorStream, isErrorStream = true)
        }
        reaperJob = sessionScope.launch {
            runCatching { proc.waitFor() }
            if (fallbackProcess == proc) {
                _isRunning.value = false
            }
            deferred.complete(false)
        }

        writeMutex.withLock {
            if (asRoot) {
                writeRawLocked("stty -echo 2>/dev/null; exec $nonInteractiveShell")
            }
            writeRawLocked(initCommand)
        }

        val timeout = if (asRoot) ROOT_HANDSHAKE_TIMEOUT_MS else USER_HANDSHAKE_TIMEOUT_MS
        val verified = withTimeoutOrNull(timeout) { deferred.await() } ?: false
        if (verified && isAlive() && (!asRoot || _isRootActive.value)) {
            handshakeCompleted = true
            return true
        }
        return false
    }

    suspend fun executeCommand(command: String, preferRoot: Boolean = true, context: Context? = null) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (isExitCommand(trimmed)) {
            shutdownAndKillApp(context)
            return
        }

        if (trimmed == "su" || trimmed == "su -" || trimmed == "su root") {
            if (_isRootActive.value && isAlive()) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Already in root shell (#)\n")
                )
            } else {
                restartSession(preferRoot = true)
                if (_isRootActive.value) {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Success, "Root shell granted (#)\n")
                    )
                } else {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Error, "su: permission denied (grant root in Magisk/KernelSU)\n")
                    )
                }
            }
            return
        }

        if (preferRoot != _isRootActive.value && isAlive()) {
            restartSession(preferRoot)
        } else if (!isAlive() || !handshakeCompleted) {
            ensureSessionStarted(preferRoot)
        }

        // Enqueue exact command so any raw PTY stdin echo is suppressed cleanly
        enqueueEchoSuppression(trimmed)

        withContext(dispatchers.io) {
            writeMutex.withLock {
                // Pure user command only — zero injected printf or control characters
                writeRawLocked(trimmed)
            }
            delay(35L)
            refreshWorkingDirectory(trimmed)
        }
    }

    private fun refreshWorkingDirectory(executedCommand: String) {
        // 1. Try out-of-band kernel /proc/<pid>/cwd lookup first (zero PTY traffic)
        val pid = ptyProcess?.pid ?: -1
        if (pid > 0) {
            val procCwd = runCatching { Os.readlink("/proc/$pid/cwd") }.getOrNull()
            if (!procCwd.isNullOrBlank() && procCwd.startsWith("/")) {
                if (procCwd != _currentDirectory.value) {
                    previousDirectory = _currentDirectory.value
                    _currentDirectory.value = procCwd
                }
                return
            }
        }

        // 2. Fallback path resolver for cd commands when /proc/<root_pid>/cwd is uid=0
        val segments = executedCommand.split("&&", ";")
        for (seg in segments) {
            val part = seg.trim()
            if (part == "cd" || part.startsWith("cd ")) {
                val targetRaw = part.removePrefix("cd").trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                val current = _currentDirectory.value
                val nextDir = when {
                    targetRaw.isEmpty() || targetRaw == "~" -> "/sdcard"
                    targetRaw == "-" -> previousDirectory
                    targetRaw.startsWith("~/") -> "/sdcard/" + targetRaw.removePrefix("~/")
                    targetRaw.startsWith("/") -> File(targetRaw).canonicalPath
                    else -> File(current, targetRaw).canonicalPath
                }
                val dirFile = File(nextDir)
                if (!dirFile.exists() || dirFile.isDirectory) {
                    previousDirectory = current
                    _currentDirectory.value = nextDir
                }
            }
        }
    }

    private fun enqueueEchoSuppression(cmd: String) {
        while (echoSuppressQueue.size >= 8) {
            echoSuppressQueue.poll()
        }
        echoSuppressQueue.offer(cmd)
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
        var incompleteEscape = ""

        try {
            while (sessionScope.isActive) {
                val bytesRead = NativePty.readFromPty(masterFd, byteBuf)
                if (bytesRead <= 0) break

                charBuf.clear()
                decoder.decode(ByteBuffer.wrap(byteBuf, 0, bytesRead), charBuf, false)
                charBuf.flip()
                if (charBuf.remaining() == 0) continue

                val combined = incompleteEscape + charBuf.toString()
                val (readyText, leftoverEscape) = AnsiParser.extractCompleteChunk(combined)
                incompleteEscape = leftoverEscape

                if (readyText.isNotEmpty()) {
                    pendingLine.append(readyText)
                }

                processStdoutBuffer(pendingLine, forceFlushPartial = false)
            }
        } catch (_: Exception) {
        } finally {
            if (pendingLine.isNotEmpty()) {
                processStdoutBuffer(pendingLine, forceFlushPartial = true)
            }
            if (!isAlive()) {
                _isRunning.value = false
            }
        }
    }

    private suspend fun streamPipeOutput(stream: InputStream, isErrorStream: Boolean) {
        val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
        val buffer = CharArray(STREAM_BUFFER_SIZE)
        val pendingLine = StringBuilder()
        var incompleteEscape = ""

        try {
            while (sessionScope.isActive) {
                val charsRead = reader.read(buffer)
                if (charsRead == -1) break
                if (charsRead == 0) continue

                val combined = incompleteEscape + String(buffer, 0, charsRead)
                val (readyText, leftoverEscape) = AnsiParser.extractCompleteChunk(combined)
                incompleteEscape = leftoverEscape

                if (readyText.isEmpty()) continue

                if (isErrorStream) {
                    if (handshakeCompleted) {
                        val cleanErr = sanitizeOutputLine(readyText)
                        if (cleanErr.isNotEmpty()) {
                            terminalLogRepository.addLog(
                                TerminalLog(TerminalState.Error, cleanErr)
                            )
                        }
                    }
                } else {
                    pendingLine.append(readyText)
                    processStdoutBuffer(pendingLine, forceFlushPartial = false)
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!isErrorStream && pendingLine.isNotEmpty()) {
                processStdoutBuffer(pendingLine, forceFlushPartial = true)
            }
            if (!isAlive()) {
                _isRunning.value = false
            }
        }
    }

    private fun sanitizeOutputLine(rawLine: String): String {
        if (rawLine.contains("__YIN_HS__") ||
            rawLine.contains("stty -echo") ||
            rawLine.contains("PROMPT_COMMAND=")
        ) {
            return ""
        }

        val plainTrimmed = rawLine
            .replace(AnsiStripRegex, "")
            .trimEnd('\r', '\n')
            .trim()

        if (plainTrimmed.isEmpty()) return rawLine

        // Suppress echoed command if PTY/subshell echoed stdin
        val expectedEcho = echoSuppressQueue.peek()
        if (expectedEcho != null && plainTrimmed == expectedEcho) {
            echoSuppressQueue.poll()
            return ""
        }

        // Suppress stray mksh prompts (e.g. ":/storage/emulated/0 $ " or ":/ # ")
        if (ShellPromptNoiseRegex.matches(plainTrimmed)) {
            return ""
        }

        return rawLine
    }

    private suspend fun processStdoutBuffer(buffer: StringBuilder, forceFlushPartial: Boolean) {
        val batchLines = StringBuilder()

        while (true) {
            val newlineIdx = buffer.indexOf("\n")
            if (newlineIdx == -1) {
                if (forceFlushPartial && buffer.isNotEmpty() && handshakeCompleted) {
                    val tail = buffer.toString()
                    buffer.clear()
                    val cleaned = sanitizeOutputLine(tail)
                    if (cleaned.isNotEmpty()) {
                        batchLines.append(cleaned)
                    }
                }
                break
            }

            val lineWithNewline = buffer.substring(0, newlineIdx + 1)
            buffer.delete(0, newlineIdx + 1)

            val trimmedLine = lineWithNewline.trimEnd('\r', '\n')
            val hsMatch = HandshakeRegex.find(trimmedLine)

            if (hsMatch != null) {
                val uidStr = hsMatch.groupValues[1]
                val pwdStr = hsMatch.groupValues[2].trim()
                _isRootActive.value = (uidStr == "0")
                if (pwdStr.startsWith("/")) {
                    _currentDirectory.value = pwdStr
                    previousDirectory = pwdStr
                }
                handshakeSignal?.complete(true)
                continue
            }

            // Swallow all init/su noise until handshake completes
            if (!handshakeCompleted) {
                continue
            }

            val cleaned = sanitizeOutputLine(lineWithNewline)
            if (cleaned.isNotEmpty()) {
                batchLines.append(cleaned)
            }
        }

        if (batchLines.isNotEmpty()) {
            terminalLogRepository.addLog(
                TerminalLog(TerminalState.Success, batchLines.toString())
            )
        }
    }

    private fun destroyInternal() {
        handshakeCompleted = false
        handshakeSignal?.cancel()
        handshakeSignal = null
        echoSuppressQueue.clear()

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
