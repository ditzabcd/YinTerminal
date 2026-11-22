package com.yin.term.ui.script

import com.yin.term.domain.model.Script

sealed interface ScriptUiEvent {
    data class DeleteScript(val scriptCommand: Script) : ScriptUiEvent
    data class StartEdit(val script: Script) : ScriptUiEvent
    data class OnEditingScriptNameChange(val newName: String) : ScriptUiEvent
    data class OnEditingScriptCommandChange(val newCommand: String) : ScriptUiEvent
    data object UndoDeleteScript : ScriptUiEvent
    data object AddNewScript : ScriptUiEvent
    data object SaveEdit : ScriptUiEvent
    data object CancelEdit : ScriptUiEvent
}