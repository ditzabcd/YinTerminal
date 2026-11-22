package com.yin.term.ui.terminal

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yin.term.core.AnsiParser
import com.yin.term.core.BaseTopBar
import com.yin.term.core.YinIcons
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.ui.theme.CrimsonDim
import com.yin.term.ui.theme.CrimsonRed
import com.yin.term.ui.theme.CrimsonSubtle
import com.yin.term.ui.theme.ObsidianBlack
import com.yin.term.ui.theme.ObsidianBorder
import com.yin.term.ui.theme.ObsidianElevated
import com.yin.term.ui.theme.ObsidianSurface
import com.yin.term.ui.theme.TerminalTextMuted
import com.yin.term.ui.theme.TerminalTextPrimary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import yin.term.R

@Composable
fun Terminal(
    viewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory),
    terminalCommandChannel: Flow<String>
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val isRootActive by viewModel.isRootActive.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        terminalCommandChannel.collect { command ->
            viewModel.onEvent(TerminalUiEvent.OnCommandChange(command))
        }
    }

    val orderedLogs = remember(uiState.logs) { uiState.logs.asReversed() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(orderedLogs.size) {
        if (orderedLogs.isNotEmpty() && isAtBottom) {
            listState.scrollToItem(orderedLogs.lastIndex)
        }
    }

    val fontSizeSp = uiState.settings.logFontSize.coerceIn(8, 24)
    val customSuccessColor = if (uiState.settings.logSuccessFontColor == -1) {
        TerminalTextPrimary
    } else {
        Color(uiState.settings.logSuccessFontColor)
    }
    val customErrorColor = if (uiState.settings.logErrorFontColor == -1) {
        CrimsonRed
    } else {
        Color(uiState.settings.logErrorFontColor)
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = { TerminalTopBar(viewModel, uiState, isRootActive) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(paddingValues)
                .imePadding()
        ) {
            // Active Session & Working Directory Bar (`cd` synced)
            SessionStatusStrip(
                currentDirectory = currentDirectory,
                isRootActive = isRootActive,
                lineCount = orderedLogs.size,
                onClearLogs = { viewModel.onEvent(TerminalUiEvent.ClearOutput) }
            )

            HorizontalDivider(thickness = 1.dp, color = ObsidianBorder)

            // High-FPS Scrollback (ANSI Colors + Emoji Support)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ObsidianBlack)
            ) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(
                            items = orderedLogs,
                            key = { index, _ -> index }
                        ) { _, log ->
                            LogLineItem(
                                log = log,
                                fontSizeSp = fontSizeSp,
                                defaultSuccessColor = customSuccessColor,
                                defaultErrorColor = customErrorColor
                            )
                        }
                    }
                }

                if (!isAtBottom && orderedLogs.isNotEmpty()) {
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                listState.scrollToItem(orderedLogs.lastIndex)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(1.dp, CrimsonRed),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Scroll to Bottom",
                                tint = CrimsonRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TAIL",
                                color = CrimsonRed,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = ObsidianBorder)

            // Quick Terminal Keys & Shortcuts
            QuickKeysRow(
                onInterrupt = { viewModel.onEvent(TerminalUiEvent.Terminate) },
                onInsert = { token ->
                    viewModel.onEvent(TerminalUiEvent.OnCommandChange(uiState.command + token))
                },
                onQuickExecute = { cmd ->
                    viewModel.onEvent(TerminalUiEvent.OnCommandChange(cmd))
                    viewModel.onEvent(TerminalUiEvent.Execute)
                }
            )

            HorizontalDivider(thickness = 1.dp, color = ObsidianBorder)

            // Prompt Input Bar
            PromptInputRow(
                command = uiState.command,
                isRootActive = isRootActive,
                isRunning = uiState.executionState == TerminalState.Running,
                fontSizeSp = fontSizeSp,
                onCommandChange = { viewModel.onEvent(TerminalUiEvent.OnCommandChange(it)) },
                onExecute = { viewModel.onEvent(TerminalUiEvent.Execute) }
            )
        }
    }
}

@Composable
private fun SessionStatusStrip(
    currentDirectory: String,
    isRootActive: Boolean,
    lineCount: Int,
    onClearLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (isRootActive) CrimsonSubtle else ObsidianElevated,
                    shape = RoundedCornerShape(3.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isRootActive) CrimsonRed else ObsidianBorder,
                    shape = RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isRootActive) "ROOT #" else "SHELL $",
                color = if (isRootActive) CrimsonRed else TerminalTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = currentDirectory,
            color = TerminalTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${lineCount}L",
            color = TerminalTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clickable(onClick = onClearLogs)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = "Clear",
                tint = CrimsonRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LogLineItem(
    log: TerminalLog,
    fontSizeSp: Int,
    defaultSuccessColor: Color,
    defaultErrorColor: Color
) {
    val baseColor = when (log.state) {
        TerminalState.Error -> defaultErrorColor
        else -> defaultSuccessColor
    }

    val parsed = remember(log.message, baseColor) {
        AnsiParser.parse(
            rawText = log.message.trimEnd('\n'),
            defaultFg = baseColor,
            defaultBg = ObsidianBlack
        )
    }

    Text(
        text = parsed,
        style = AnsiParser.terminalTextStyle(
            fontSizeSp = fontSizeSp,
            defaultColor = baseColor
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun QuickKeysRow(
    onInterrupt: () -> Unit,
    onInsert: (String) -> Unit,
    onQuickExecute: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyChip(label = "CTRL+C", isAlert = true, onClick = onInterrupt)
        KeyChip(label = "TAB", onClick = { onInsert("\t") })
        KeyChip(label = "ls -la", onClick = { onQuickExecute("ls -la --color=always") })
        KeyChip(label = "pwd", onClick = { onQuickExecute("pwd") })
        KeyChip(label = "cd ..", onClick = { onQuickExecute("cd .. && pwd") })
        KeyChip(label = "whoami", onClick = { onQuickExecute("whoami") })
        KeyChip(label = "clear", onClick = { onQuickExecute("clear") })
    }
}

@Composable
private fun KeyChip(
    label: String,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isAlert) CrimsonSubtle else ObsidianElevated,
                shape = RoundedCornerShape(3.dp)
            )
            .border(
                width = 1.dp,
                color = if (isAlert) CrimsonRed else ObsidianBorder,
                shape = RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (isAlert) CrimsonRed else TerminalTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PromptInputRow(
    command: String,
    isRootActive: Boolean,
    isRunning: Boolean,
    fontSizeSp: Int,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isRootActive) "#" else "$",
            color = CrimsonRed,
            fontFamily = FontFamily.Monospace,
            fontSize = (fontSizeSp + 2).sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (command.isEmpty()) {
                Text(
                    text = "enter command...",
                    color = TerminalTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp
                )
            }
            BasicTextField(
                value = command,
                onValueChange = onCommandChange,
                textStyle = AnsiParser.terminalTextStyle(
                    fontSizeSp = fontSizeSp,
                    defaultColor = TerminalTextPrimary
                ),
                cursorBrush = SolidColor(CrimsonRed),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onExecute() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (command.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clickable { onCommandChange("") }
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear Input",
                    tint = TerminalTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .background(CrimsonDim, RoundedCornerShape(4.dp))
                .border(1.dp, CrimsonRed, RoundedCornerShape(4.dp))
                .clickable(enabled = !isRunning, onClick = onExecute)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = YinIcons.PlayArrow,
                    contentDescription = "Run",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TerminalTopBar(
    viewModel: TerminalViewModel,
    uiState: TerminalUiState,
    isRootActive: Boolean
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    BaseTopBar(actions = {
        IconButton(onClick = { viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(true)) }) {
            Icon(
                imageVector = YinIcons.MoreVert,
                contentDescription = "Tools",
                tint = TerminalTextPrimary
            )
        }
        DropdownMenu(
            expanded = uiState.isToolsMenuOpen,
            onDismissRequest = { viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false)) },
            containerColor = ObsidianElevated,
            offset = DpOffset(0.dp, 8.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isRootActive) "Switch to Shell ($)" else "Switch to Root (#)",
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = {
                    viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                    viewModel.onEvent(TerminalUiEvent.ToggleRoot)
                },
                leadingIcon = {
                    Icon(
                        imageVector = YinIcons.Grass,
                        contentDescription = "Root",
                        tint = CrimsonRed
                    )
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Restart session",
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = {
                    viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                    viewModel.restartSession()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Restart",
                        tint = CrimsonRed
                    )
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Terminate process",
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = {
                    viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                    viewModel.onEvent(TerminalUiEvent.Terminate)
                },
                leadingIcon = {
                    Icon(
                        imageVector = YinIcons.Clear,
                        contentDescription = "Terminate",
                        tint = CrimsonRed
                    )
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Clear output",
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = {
                    viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                    viewModel.onEvent(TerminalUiEvent.ClearOutput)
                },
                leadingIcon = {
                    Icon(
                        imageVector = YinIcons.ClearAll,
                        contentDescription = "Clear",
                        tint = TerminalTextMuted
                    )
                }
            )
            if (!Environment.isExternalStorageManager()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Storage Permission",
                            color = TerminalTextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    onClick = {
                        viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        ).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(intent)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = YinIcons.Storage,
                            contentDescription = "Storage Permission",
                            tint = CrimsonRed
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = "GitHub (Upstream)",
                        color = TerminalTextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = {
                    viewModel.onEvent(TerminalUiEvent.ToggleToolsMenu(false))
                    uriHandler.openUri("https://github.com/dedeadend/DTerminal")
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = TerminalTextMuted
                    )
                }
            )
        }
    })
}