package com.yin.term.ui.history

import androidx.compose.runtime.Immutable
import com.yin.term.domain.model.History

@Immutable
data class HistoryUiState(
    val history: List<History> = emptyList(),
    val historyBackup: List<History> = emptyList(),
    val isLoading: Boolean = true
) {
    val showEmptyState: Boolean get() = history.isEmpty() && !isLoading
}