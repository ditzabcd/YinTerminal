package com.yin.term.data.datasource

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PythonService : Service() {

    companion object {
        const val MSG_HANDSHAKE = 1
        const val MSG_RUN_BLOCK = 2
        const val MSG_OUTPUT_RESULT = 3
        const val MSG_RUN_COMPLETED = 103

        const val KEY_CODE_BLOCK = "key_code_block"
        const val KEY_RESULT_TEXT = "key_result_text"
        const val KEY_SERVICE_PID = "key_service_pid"

        private val initLock = Any()
    }

    interface PythonStreamListener {
        fun onStdout(text: String)
    }

    private val mMessenger = Messenger(IncomingHandler())

    override fun onBind(intent: Intent?): IBinder = mMessenger.binder

    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            synchronized(initLock) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(applicationContext))
                }
            }
        }
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_HANDSHAKE -> {
                    val replyTo = msg.replyTo ?: return
                    val response = Message.obtain(null, MSG_HANDSHAKE).apply {
                        data = Bundle().apply {
                            putInt(KEY_SERVICE_PID, Process.myPid())
                        }
                    }
                    runCatching { replyTo.send(response) }
                }

                MSG_RUN_BLOCK -> {
                    val replyTo = msg.replyTo ?: return
                    val codeBlock = msg.data.getString(KEY_CODE_BLOCK).orEmpty()
                    val appFilesDir = filesDir.absolutePath

                    Thread({
                        executePythonBlockStreaming(codeBlock, replyTo, appFilesDir)
                    }, "YinTerm-PyWorker").start()
                }

                else -> super.handleMessage(msg)
            }
        }
    }

    private fun executePythonBlockStreaming(
        codeBlock: String,
        replyTo: Messenger,
        appFilesDir: String
    ) {
        try {
            ensurePythonStarted()
            val py = Python.getInstance()
            val bridge = py.getModule("py_bridge")

            val listener = object : PythonStreamListener {
                override fun onStdout(text: String) {
                    val response = Message.obtain(null, MSG_OUTPUT_RESULT).apply {
                        data = Bundle().apply {
                            putString(KEY_RESULT_TEXT, text)
                        }
                    }
                    try {
                        replyTo.send(response)
                    } catch (_: RemoteException) {
                    }
                }
            }

            bridge.callAttr("execute_smart_block", codeBlock, listener, appFilesDir)
        } catch (e: Exception) {
            val errorResponse = Message.obtain(null, MSG_OUTPUT_RESULT).apply {
                data = Bundle().apply {
                    putString(KEY_RESULT_TEXT, "Python Runtime Error: ${e.localizedMessage}\n")
                }
            }
            runCatching { replyTo.send(errorResponse) }
        } finally {
            val completedMsg = Message.obtain(null, MSG_RUN_COMPLETED)
            runCatching { replyTo.send(completedMsg) }
        }
    }
}