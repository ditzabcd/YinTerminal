package com.yin.term

import android.app.Application
import com.yin.term.core.AppDispatchers
import com.yin.term.di.AppContainer

class YinTermApp : Application() {

    lateinit var dispatchers: AppDispatchers
        private set

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        dispatchers = AppDispatchers()
        container = AppContainer(this, dispatchers)
    }

    companion object {
        lateinit var instance: YinTermApp
            private set
    }
}