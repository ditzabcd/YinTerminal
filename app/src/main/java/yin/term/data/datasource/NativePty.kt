package com.yin.term.data.datasource

import java.nio.charset.StandardCharsets

object NativePty {

    val isAvailable: Boolean = runCatching {
        System.loadLibrary("yinterm_pty")
        true
    }.getOrDefault(false)

    data class PtyProcess(
        val masterFd: Int,
        val pid: Int
    )

    fun spawn(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        rows: Int,
        cols: Int
    ): PtyProcess? {
        if (!isAvailable) return null
        val pair = runCatching {
            createSubprocess(cmd, cwd, args, envVars, rows, cols)
        }.getOrNull() ?: return null

        if (pair.size < 2 || pair[0] < 0 || pair[1] <= 0) return null
        return PtyProcess(masterFd = pair[0], pid = pair[1])
    }

    fun writeString(masterFd: Int, text: String): Boolean {
        if (!isAvailable || masterFd < 0) return false
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return writeToPty(masterFd, bytes, bytes.size) == bytes.size
    }

    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        rows: Int,
        cols: Int
    ): IntArray?

    external fun setWindowSize(masterFd: Int, rows: Int, cols: Int)

    external fun readFromPty(masterFd: Int, buffer: ByteArray): Int

    external fun writeToPty(masterFd: Int, data: ByteArray, length: Int): Int

    external fun sendSignal(pid: Int, signalNumber: Int)

    external fun waitForProcess(pid: Int): Int

    external fun closePty(masterFd: Int)
}