package com.yin.term.ui.history

import com.yin.term.domain.model.History

sealed interface HistoryUiEvent {
    data class DeleteHistoryItem(val history: History) : HistoryUiEvent
    data object UndoDeleteHistoryItems : HistoryUiEvent
    data object ClearHistory : HistoryUiEvent
}