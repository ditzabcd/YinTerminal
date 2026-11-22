package com.yin.term.ui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yin.term.YinTermApp
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.Script
import com.yin.term.domain.repository.ScriptRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScriptViewModel(
    private val scriptRepository: ScriptRepository,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScriptUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEffect = Channel<ScriptUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        viewModelScope.launch {
            scriptRepository.getScripts()
                .flowOn(dispatchers.io)
                .collect { scripts ->
                    _uiState.update { it.copy(scripts = scripts, isLoading = false) }
                }
        }
    }

    fun onEvent(event: ScriptUiEvent) {
        when (event) {
            is ScriptUiEvent.DeleteScript -> deleteScript(event.scriptCommand)
            is ScriptUiEvent.AddNewScript -> addNewScript()
            is ScriptUiEvent.UndoDeleteScript -> undoDeleteScript()
            is ScriptUiEvent.StartEdit -> startEdit(event.script)
            is ScriptUiEvent.CancelEdit -> cancelEdit()
            is ScriptUiEvent.OnEditingScriptCommandChange -> onEditingScriptCommandChange(event.newCommand)
            is ScriptUiEvent.OnEditingScriptNameChange -> onEditingScriptNameChange(event.newName)
            is ScriptUiEvent.SaveEdit -> saveEdit()
        }
    }

    private fun deleteScript(scriptCommand: Script) {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(scriptsBackup = listOf(scriptCommand)) }
            scriptRepository.deleteScriptWithId(scriptCommand.id)
            _uiEffect.send(ScriptUiEffect.ShowSnackbar("Script Deleted", "Undo"))
        }
    }

    private fun undoDeleteScript() {
        viewModelScope.launch(dispatchers.io) {
            val backup = _uiState.value.scriptsBackup
            if (backup.isNotEmpty()) {
                scriptRepository.addScript(backup.last())
                _uiState.update { it.copy(scriptsBackup = emptyList()) }
            }
        }
    }

    private fun addNewScript() {
        startEdit(Script("", ""))
    }

    private fun startEdit(script: Script) {
        _uiState.update {
            it.copy(
                editingScriptName = script.name,
                editingScriptCommand = script.command,
                editingScriptId = script.id,
                editingScriptNameError = "",
                editingScriptCommandError = "",
                isEditing = true
            )
        }
    }

    private fun saveEdit() {
        val current = _uiState.value
        var isValid = true
        if (current.editingScriptName.isBlank()) {
            _uiState.update { it.copy(editingScriptNameError = "Name cannot be empty") }
            isValid = false
        }
        if (current.editingScriptCommand.isBlank()) {
            _uiState.update { it.copy(editingScriptCommandError = "Command cannot be empty") }
            isValid = false
        }
        if (!isValid) return

        viewModelScope.launch(dispatchers.io) {
            scriptRepository.addScript(
                Script(
                    name = current.editingScriptName.trim(),
                    command = current.editingScriptCommand,
                    id = current.editingScriptId
                )
            )
            _uiState.update {
                it.copy(
                    editingScriptName = "",
                    editingScriptCommand = "",
                    editingScriptNameError = "",
                    editingScriptCommandError = "",
                    editingScriptId = 0,
                    isEditing = false
                )
            }
            _uiEffect.send(ScriptUiEffect.ShowSnackbar("Script Saved Successfully"))
        }
    }

    private fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingScriptNameError = "",
                editingScriptCommandError = "",
                editingScriptName = "",
                editingScriptCommand = "",
                editingScriptId = 0,
                isEditing = false
            )
        }
    }

    private fun onEditingScriptNameChange(newName: String) {
        _uiState.update {
            it.copy(editingScriptName = newName, editingScriptNameError = "")
        }
    }

    private fun onEditingScriptCommandChange(newCommand: String) {
        _uiState.update {
            it.copy(editingScriptCommand = newCommand, editingScriptCommandError = "")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = YinTermApp.instance
                return ScriptViewModel(
                    scriptRepository = app.container.scriptRepository,
                    dispatchers = app.dispatchers
                ) as T
            }
        }
    }
}