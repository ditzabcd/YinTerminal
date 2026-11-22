package com.yin.term.ui.script

sealed interface ScriptUiEffect {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : ScriptUiEffect
}