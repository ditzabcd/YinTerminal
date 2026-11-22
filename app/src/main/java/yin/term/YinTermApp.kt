package com.yin.term

import android.app.Application
import com.yin.term.core.AppDispatchers
import com.yin.term.data.datasource.TerminalSession
import com.yin.term.di.AppContainer

class YinTermApp : Application() {

    val dispatchers: AppDispatchers = AppDispatchers()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        instance = this
        TerminalSession.installCrashLogger()
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: YinTermApp
            private set
    }
}