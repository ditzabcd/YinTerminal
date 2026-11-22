package com.yin.term.data.datasource

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

class PythonManager(
    private val context: Context,
    private val terminalLogRepository: TerminalLogRepository,
    private val dispatchers: AppDispatchers
) {
    private val systemErrors = listOf(
        "Python Runtime Error", "Failed to talk", "Traceback (most recent call last):"
    )
    private val pythonErrors = listOf(
        "SyntaxError:", "IndentationError:", "NameError:", "TypeError:",
        "ValueError:", "IndexError:", "KeyError:", "AttributeError:",
        "ZeroDivisionError:", "ModuleNotFoundError:", "ImportError:",
        "RuntimeError:", "FileNotFoundError:", "PermissionError:",
        "OSError:", "OverflowError:", "UnboundLocalError:", "AssertionError:"
    )

    @Volatile
    private var mService: Messenger? = null

    @Volatile
    private var isBound = false

    @Volatile
    private var workerPid: Int = -1

    @Volatile
    private var serviceReadyDeferred: CompletableDeferred<Messenger>? = null

    @Volatile
    private var streamChannel: Channel<StreamEvent>? = null

    private sealed interface StreamEvent {
        data class Chunk(val text: String, val state: TerminalState) : StreamEvent
        data object Finished : StreamEvent
    }

    suspend fun execute(codeBlock: String) {
        withContext(dispatchers.default) {
            val messenger = getOrBindMessenger()
            if (messenger == null) {
                terminalLogRepository.addLog(
                    TerminalLog(
                        TerminalState.Error,
                        "Failed to start Python Engine. Please try again.\n"
                    )
                )
                return@withContext
            }

            val channel = Channel<StreamEvent>(Channel.UNLIMITED)
            streamChannel = channel

            try {
                val msg = Message.obtain(null, PythonService.MSG_RUN_BLOCK).apply {
                    replyTo = clientMessenger
                    data = Bundle().apply {
                        putString(PythonService.KEY_CODE_BLOCK, codeBlock)
                    }
                }
                messenger.send(msg)
            } catch (e: RemoteException) {
                terminalLogRepository.addLog(
                    TerminalLog(
                        TerminalState.Error,
                        "Failed to talk with Python Worker: ${e.localizedMessage}\n"
                    )
                )
                streamChannel = null
                return@withContext
            }

            for (event in channel) {
                when (event) {
                    is StreamEvent.Chunk -> {
                        terminalLogRepository.addLog(TerminalLog(event.state, event.text))
                    }
                    is StreamEvent.Finished -> break
                }
            }
            streamChannel = null
        }
    }

    private suspend fun getOrBindMessenger(): Messenger? {
        mService?.let { if (isBound) return it }

        val existing = serviceReadyDeferred
        if (existing != null && !existing.isCompleted) {
            return withTimeoutOrNull(10_000L) { existing.await() }
        }

        val deferred = CompletableDeferred<Messenger>()
        serviceReadyDeferred = deferred

        terminalLogRepository.addLog(
            TerminalLog(
                TerminalState.Info,
                "\n[Starting Python 3.13 Engine...]\n"
            )
        )

        bindService()
        return withTimeoutOrNull(10_000L) { deferred.await() }
    }

    suspend fun cancel() {
        withContext(dispatchers.default) {
            if (workerPid != -1) {
                Process.killProcess(workerPid)
                streamChannel?.trySend(
                    StreamEvent.Chunk(
                        "Process terminated by user.\n",
                        TerminalState.Error
                    )
                )
                streamChannel?.trySend(StreamEvent.Finished)
                unbindService()
            }
        }
    }

    private val clientMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            PythonService.MSG_HANDSHAKE -> {
                workerPid = msg.data.getInt(PythonService.KEY_SERVICE_PID, -1)
                true
            }

            PythonService.MSG_OUTPUT_RESULT -> {
                val result = msg.data.getString(PythonService.KEY_RESULT_TEXT).orEmpty()
                val trimmedResult = result.trim()
                val state = if (
                    systemErrors.any { trimmedResult.contains(it) } ||
                    pythonErrors.any { trimmedResult.startsWith(it) }
                ) {
                    TerminalState.Error
                } else {
                    TerminalState.Success
                }
                streamChannel?.trySend(StreamEvent.Chunk(result, state))
                true
            }

            PythonService.MSG_RUN_COMPLETED -> {
                streamChannel?.trySend(StreamEvent.Finished)
                true
            }

            else -> false
        }
    })

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val messenger = Messenger(service)
            mService = messenger
            isBound = true
            serviceReadyDeferred?.complete(messenger)
            try {
                val msg = Message.obtain(null, PythonService.MSG_HANDSHAKE).apply {
                    replyTo = clientMessenger
                }
                messenger.send(msg)
            } catch (_: RemoteException) {
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
            isBound = false
            workerPid = -1
            serviceReadyDeferred?.cancel()
            serviceReadyDeferred = null
            streamChannel?.trySend(StreamEvent.Finished)
        }
    }

    private fun bindService() {
        if (!isBound) {
            val intent = Intent(context, PythonService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindService() {
        if (isBound) {
            runCatching { context.unbindService(serviceConnection) }
            isBound = false
            mService = null
            workerPid = -1
            serviceReadyDeferred?.cancel()
            serviceReadyDeferred = null
        }
    }
}