package com.yin.term.data.repository

import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.ShellManager
import com.yin.term.domain.repository.CommandExecutor
import kotlinx.coroutines.withContext

class CommandExecutorImpl(
    private val shellManager: ShellManager,
    private val dispatchers: AppDispatchers
) : CommandExecutor {

    override suspend fun execute(command: String, isRoot: Boolean) {
        withContext(dispatchers.io) {
            shellManager.execute(command, isRoot)
        }
    }

    override suspend fun cancel() {
        withContext(dispatchers.io) {
            shellManager.cancel()
        }
    }
}