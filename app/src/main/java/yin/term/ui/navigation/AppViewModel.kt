package com.yin.term.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val _terminalCommandChannel = Channel<String>(Channel.BUFFERED)
    val terminalCommandChannel = _terminalCommandChannel.receiveAsFlow()

    fun onItemExecuteClicked(command: String) {
        viewModelScope.launch {
            _terminalCommandChannel.send(command)
        }
    }
}