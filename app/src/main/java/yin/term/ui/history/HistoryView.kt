package com.yin.term.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yin.term.core.BaseTopBar
import com.yin.term.core.YinIcons
import com.yin.term.domain.model.History
import com.yin.term.ui.theme.CrimsonRed
import com.yin.term.ui.theme.ObsidianBlack
import com.yin.term.ui.theme.ObsidianBorder
import com.yin.term.ui.theme.ObsidianSurface
import com.yin.term.ui.theme.TerminalTextMuted
import com.yin.term.ui.theme.TerminalTextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun History(
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
    onHistoryItemExecuteClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HistoryUiEffect.ShowSnackbar -> {
                    snackbarJob?.cancel()
                    snackbarJob = snackbarScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                            duration = SnackbarDuration.Short,
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onEvent(HistoryUiEvent.UndoDeleteHistoryItems)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            HistoryTopBar(
                onClearHistoryClick = { viewModel.onEvent(HistoryUiEvent.ClearHistory) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
        ) {
            AnimatedVisibility(
                visible = uiState.showEmptyState,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 400, delayMillis = 150),
                    initialAlpha = 0.0f
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 400, delayMillis = 150),
                    initialScale = 0.85f
                ),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = YinIcons.History,
                        contentDescription = "No History",
                        tint = TerminalTextMuted,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "No History Yet",
                        fontFamily = FontFamily.Monospace,
                        color = TerminalTextMuted,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true
            ) {
                items(items = uiState.history, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 200),
                            fadeOutSpec = tween(durationMillis = 180),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        HistoryItem(
                            history = item,
                            onExecuteClick = { onHistoryItemExecuteClick(item.command) },
                            onDeleteSwipe = { viewModel.onEvent(HistoryUiEvent.DeleteHistoryItem(item)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: History,
    onExecuteClick: () -> Unit,
    onDeleteSwipe: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            ) {
                onDeleteSwipe()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(history.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CrimsonRed),
                horizontalArrangement = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = YinIcons.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "COMMAND",
                    textAlign = TextAlign.Left,
                    fontFamily = FontFamily.Monospace,
                    color = CrimsonRed,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.height(intrinsicSize = IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        thickness = 2.dp,
                        color = CrimsonRed
                    )
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = history.command,
                            textAlign = TextAlign.Left,
                            fontFamily = FontFamily.Monospace,
                            color = TerminalTextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CrimsonRed)
                            .clickable(onClick = onExecuteClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = YinIcons.PlayArrow,
                            contentDescription = "Run",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(onClearHistoryClick: () -> Unit) {
    BaseTopBar(
        title = "History",
        animateOnLaunch = false,
        actions = {
            IconButton(onClick = onClearHistoryClick) {
                Icon(
                    imageVector = YinIcons.Delete,
                    contentDescription = "Clear History",
                    tint = CrimsonRed
                )
            }
        }
    )
}