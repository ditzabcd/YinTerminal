package com.yin.term.ui.terminal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.yin.term.domain.model.Settings
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState

@Immutable
data class TerminalUiState(
    val settings: Settings = Settings(),
    val logs: List<TerminalLog> = emptyList(),
    val executionState: TerminalState = TerminalState.Idle,
    val isToolsMenuOpen: Boolean = false,
    val isRoot: Boolean = false,
    val command: String = "",
    val terminalLogText: AnnotatedString = AnnotatedString("")
)