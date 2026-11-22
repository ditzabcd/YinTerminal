package com.yin.term.ui.terminal

sealed interface TerminalUiEvent {
    data class ToggleToolsMenu(val show: Boolean) : TerminalUiEvent
    data class OnCommandChange(val newCommand: String) : TerminalUiEvent
    data object ToggleRoot : TerminalUiEvent
    data object ClearOutput : TerminalUiEvent
    data object Execute : TerminalUiEvent
    data object Terminate : TerminalUiEvent
}