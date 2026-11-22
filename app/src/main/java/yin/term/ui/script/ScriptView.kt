package com.yin.term.ui.script

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.yin.term.domain.model.Script
import com.yin.term.ui.theme.CrimsonRed
import com.yin.term.ui.theme.ObsidianBlack
import com.yin.term.ui.theme.ObsidianBorder
import com.yin.term.ui.theme.ObsidianElevated
import com.yin.term.ui.theme.ObsidianSurface
import com.yin.term.ui.theme.TerminalTextMuted
import com.yin.term.ui.theme.TerminalTextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Script(
    viewModel: ScriptViewModel = viewModel(factory = ScriptViewModel.Factory),
    onScriptItemExecuteClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ScriptUiEffect.ShowSnackbar -> {
                    snackbarJob?.cancel()
                    snackbarJob = snackbarScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                            duration = SnackbarDuration.Short,
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onEvent(ScriptUiEvent.UndoDeleteScript)
                        }
                    }
                }
            }
        }
    }

    val scrollState = rememberLazyListState()
    var previousIndex by remember { mutableIntStateOf(0) }
    val isScrollingUp by remember {
        derivedStateOf {
            val currentIndex = scrollState.firstVisibleItemIndex
            val scrollingUp = currentIndex == 0 || currentIndex < previousIndex
            previousIndex = currentIndex
            scrollingUp
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = { ScriptTopBar() },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isScrollingUp,
                enter = slideInVertically(initialOffsetY = { it + 50 }),
                exit = slideOutVertically(targetOffsetY = { it + 50 })
            ) {
                FloatingActionButton(
                    onClick = { viewModel.onEvent(ScriptUiEvent.AddNewScript) },
                    containerColor = CrimsonRed,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = YinIcons.Add,
                        contentDescription = "New Script"
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
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
                        imageVector = YinIcons.EditNote,
                        contentDescription = "No Script",
                        tint = TerminalTextMuted,
                        modifier = Modifier.size(76.dp)
                    )
                    Text(
                        text = "Tap + to add a script",
                        fontFamily = FontFamily.Monospace,
                        color = TerminalTextMuted,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = uiState.scripts, key = { it.id }) { item ->
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
                        ScriptItem(
                            script = item,
                            onExecuteClick = { onScriptItemExecuteClick(item.command) },
                            onEditClick = { viewModel.onEvent(ScriptUiEvent.StartEdit(item)) },
                            onDeleteSwipe = { viewModel.onEvent(ScriptUiEvent.DeleteScript(item)) }
                        )
                    }
                }
            }
        }

        if (uiState.isEditing) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.onEvent(ScriptUiEvent.CancelEdit) },
                containerColor = ObsidianSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                EditSheetContent(viewModel, uiState)
            }
        }
    }
}

@Composable
private fun ScriptItem(
    script: Script,
    onExecuteClick: () -> Unit,
    onEditClick: () -> Unit,
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

    LaunchedEffect(script.id) {
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
                    text = "SCRIPT",
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
                            text = script.name,
                            textAlign = TextAlign.Left,
                            fontFamily = FontFamily.Monospace,
                            color = TerminalTextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianElevated)
                            .clickable(onClick = onEditClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = YinIcons.Edit,
                            contentDescription = "Edit",
                            tint = TerminalTextPrimary
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
private fun ScriptTopBar() {
    BaseTopBar(title = "Scripts", animateOnLaunch = false)
}

@Composable
private fun EditSheetContent(viewModel: ScriptViewModel, uiState: ScriptUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Script Options",
                fontFamily = FontFamily.Monospace,
                color = TerminalTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = uiState.editingScriptName,
                onValueChange = { viewModel.onEvent(ScriptUiEvent.OnEditingScriptNameChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                label = { Text("Name", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                isError = uiState.hasEditingScriptNameError,
                supportingText = { Text(text = uiState.editingScriptNameError) }
            )

            OutlinedTextField(
                value = uiState.editingScriptCommand,
                onValueChange = { viewModel.onEvent(ScriptUiEvent.OnEditingScriptCommandChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                label = { Text("Command", fontFamily = FontFamily.Monospace) },
                isError = uiState.hasEditingScriptCommandError,
                supportingText = { Text(text = uiState.editingScriptCommandError) }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.onEvent(ScriptUiEvent.CancelEdit) }) {
                Text("Cancel", color = TerminalTextMuted, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.onEvent(ScriptUiEvent.SaveEdit) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonRed,
                    contentColor = Color.White
                )
            ) {
                Text("Save", fontFamily = FontFamily.Monospace)
            }
        }
    }
}