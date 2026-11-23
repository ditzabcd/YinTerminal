package com.yin.term.ui.terminal

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yin.term.core.AnsiParser
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Terminal(
    viewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory),
    terminalCommandChannel: Flow<String>
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val isRootActive by viewModel.isRootActive.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeStore.theme.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val inputFocusRequester = remember { FocusRequester() }

    var showContextDialog by remember { mutableStateOf(false) }
    var selectedLineText by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        terminalCommandChannel.collect { command ->
            viewModel.onEvent(TerminalUiEvent.OnCommandChange(command))
        }
    }

    LaunchedEffect(Unit) {
        runCatching { inputFocusRequester.requestFocus() }
    }

    val orderedLogs = remember(uiState.logs) { uiState.logs.toList().asReversed() }
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

    val bgColor = themeConfig.backgroundColor
    val surfaceColor = themeConfig.surfaceColor
    val borderColor = themeConfig.borderColor
    val textColor = themeConfig.textColor
    val mutedColor = themeConfig.mutedTextColor
    val accentColor = themeConfig.accentColor
    val fontSizeSp = themeConfig.fontSizeSp

    Scaffold(
        containerColor = bgColor,
        topBar = {
            MinimalTerminalHeader(
                currentDirectory = currentDirectory,
                isRootActive = isRootActive,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onToggleRoot = { viewModel.onEvent(TerminalUiEvent.ToggleRoot) },
                onOpenMenu = {
                    selectedLineText = null
                    showContextDialog = true
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { runCatching { inputFocusRequester.requestFocus() } },
                        onLongClick = {
                            selectedLineText = null
                            showContextDialog = true
                        }
                    )
            ) {
                if (orderedLogs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = if (isRootActive) "root@android:$currentDirectory #" else "shell@android:$currentDirectory $",
                            color = mutedColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = orderedLogs,
                            key = { index, _ -> index }
                        ) { _, log ->
                            TerminalOutputLine(
                                log = log,
                                fontSizeSp = fontSizeSp,
                                bgColor = bgColor,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                onTapLine = {
                                    runCatching { inputFocusRequester.requestFocus() }
                                },
                                onLongPressLine = { lineText ->
                                    selectedLineText = lineText
                                    showContextDialog = true
                                }
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
                        color = surfaceColor,
                        border = BorderStroke(1.dp, accentColor),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Scroll to bottom",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            InlinePromptBar(
                command = uiState.command,
                isRootActive = isRootActive,
                fontSizeSp = fontSizeSp,
                bgColor = bgColor,
                borderColor = borderColor,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                focusRequester = inputFocusRequester,
                onCommandChange = { viewModel.onEvent(TerminalUiEvent.OnCommandChange(it)) },
                onSubmit = { viewModel.executeWithContext(context) }
            )

            TerminalShortcutStrip(
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textColor = textColor,
                accentColor = accentColor,
                onInterrupt = { viewModel.onEvent(TerminalUiEvent.Terminate) },
                onInsertToken = { token ->
                    viewModel.onEvent(TerminalUiEvent.OnCommandChange(uiState.command + token))
                    runCatching { inputFocusRequester.requestFocus() }
                },
                onClear = { viewModel.onEvent(TerminalUiEvent.ClearOutput) }
            )
        }
    }

    if (showContextDialog) {
        TerminalContextMenuDialog(
            selectedLineText = selectedLineText,
            isRootActive = isRootActive,
            currentFontSize = fontSizeSp,
            surfaceColor = surfaceColor,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            onDismiss = { showContextDialog = false },
            onCopyLine = { line ->
                clipboardManager.setText(AnnotatedString(line))
                Toast.makeText(context, "Line copied", Toast.LENGTH_SHORT).show()
                showContextDialog = false
            },
            onCopyAll = {
                val plainAll = orderedLogs.joinToString("") { it.message }
                clipboardManager.setText(AnnotatedString(plainAll))
                Toast.makeText(context, "All output copied", Toast.LENGTH_SHORT).show()
                showContextDialog = false
            },
            onClear = {
                viewModel.onEvent(TerminalUiEvent.ClearOutput)
                showContextDialog = false
            },
            onToggleRoot = {
                viewModel.onEvent(TerminalUiEvent.ToggleRoot)
                showContextDialog = false
            },
            onRestart = {
                viewModel.restartSession()
                showContextDialog = false
            },
            onChangeFontSize = { delta ->
                viewModel.themeStore.updateTheme(
                    themeConfig.copy(fontSizeSp = themeConfig.fontSizeSp + delta)
                )
            },
            onOpenThemeCustomizer = {
                showContextDialog = false
                showThemeDialog = true
            },
            onStoragePermission = {
                showContextDialog = false
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                ).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
            },
            onExitApp = {
                showContextDialog = false
                viewModel.exitAndKillApp(context)
            }
        )
    }

    if (showThemeDialog) {
        ThemeCustomizerDialog(
            currentTheme = themeConfig,
            onDismiss = { showThemeDialog = false },
            onApplyTheme = { updated ->
                viewModel.themeStore.updateTheme(updated)
                showThemeDialog = false
            }
        )
    }
}

@Composable
private fun MinimalTerminalHeader(
    currentDirectory: String,
    isRootActive: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onToggleRoot: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, if (isRootActive) accentColor else borderColor, RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onToggleRoot)
                    .semantics { contentDescription = "Toggle root or user shell" }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRootActive) "ROOT #" else "USER $",
                    color = if (isRootActive) accentColor else textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = currentDirectory,
                color = mutedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onOpenMenu)
                    .semantics { contentDescription = "Terminal options" }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OPT",
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = borderColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalOutputLine(
    log: TerminalLog,
    fontSizeSp: Int,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onTapLine: () -> Unit,
    onLongPressLine: (String) -> Unit
) {
    val lineColor = when (log.state) {
        TerminalState.Error -> accentColor
        TerminalState.Info -> mutedColor
        else -> textColor
    }

    val cleanMessage = log.message.trimEnd('\n')
    val parsed = remember(cleanMessage, lineColor, bgColor) {
        AnsiParser.parse(
            rawText = cleanMessage,
            defaultFg = lineColor,
            defaultBg = bgColor
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTapLine,
                onLongClick = { onLongPressLine(parsed.text) }
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (log.state == TerminalState.Error) {
            Text(
                text = "[ERR] ",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = (fontSizeSp - 1).sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = parsed,
            style = AnsiParser.terminalTextStyle(
                fontSizeSp = fontSizeSp,
                defaultColor = lineColor
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlinePromptBar(
    command: String,
    isRootActive: Boolean,
    fontSizeSp: Int,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    focusRequester: FocusRequester,
    onCommandChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
    ) {
        HorizontalDivider(thickness = 1.dp, color = borderColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRootActive) "#" else "$",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = (fontSizeSp + 1).sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (command.isEmpty()) {
                    Text(
                        text = "enter command (type exit to close)",
                        color = mutedColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp
                    )
                }
                BasicTextField(
                    value = command,
                    onValueChange = onCommandChange,
                    textStyle = AnsiParser.terminalTextStyle(
                        fontSizeSp = fontSizeSp,
                        defaultColor = textColor
                    ),
                    cursorBrush = SolidColor(accentColor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { onSubmit() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                onSubmit()
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            if (command.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button) { onCommandChange("") }
                        .semantics { contentDescription = "Clear command line" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = mutedColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalShortcutStrip(
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    accentColor: Color,
    onInterrupt: () -> Unit,
    onInsertToken: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
    ) {
        HorizontalDivider(thickness = 1.dp, color = borderColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShortcutKey(label = "CTRL+C", isAccent = true, borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = onInterrupt)
            ShortcutKey(label = "TAB", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("\t") })
            ShortcutKey(label = "ESC", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("\u001B") })
            ShortcutKey(label = "|", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("|") })
            ShortcutKey(label = "/", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("/") })
            ShortcutKey(label = "-", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("-") })
            ShortcutKey(label = "~", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = { onInsertToken("~") })
            ShortcutKey(label = "CLEAR", borderColor = borderColor, textColor = textColor, accentColor = accentColor, onClick = onClear)
        }
    }
}

@Composable
private fun ShortcutKey(
    label: String,
    isAccent: Boolean = false,
    borderColor: Color,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (isAccent) accentColor else borderColor, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isAccent) accentColor else textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TerminalContextMenuDialog(
    selectedLineText: String?,
    isRootActive: Boolean,
    currentFontSize: Int,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onDismiss: () -> Unit,
    onCopyLine: (String) -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
    onToggleRoot: () -> Unit,
    onRestart: () -> Unit,
    onChangeFontSize: (Int) -> Unit,
    onOpenThemeCustomizer: () -> Unit,
    onStoragePermission: () -> Unit,
    onExitApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "Terminal Options",
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!selectedLineText.isNullOrBlank()) {
                    ContextMenuActionRow(
                        tag = "[CP]",
                        label = "Copy selected line",
                        textColor = textColor,
                        tint = accentColor,
                        onClick = { onCopyLine(selectedLineText) }
                    )
                }
                ContextMenuActionRow(
                    tag = "[ALL]",
                    label = "Copy all output",
                    textColor = textColor,
                    tint = mutedColor,
                    onClick = onCopyAll
                )
                ContextMenuActionRow(
                    tag = "[CLR]",
                    label = "Clear terminal buffer",
                    textColor = textColor,
                    tint = mutedColor,
                    onClick = onClear
                )
                ContextMenuActionRow(
                    tag = "[RGB]",
                    label = "Customize theme and colors",
                    textColor = textColor,
                    tint = accentColor,
                    onClick = onOpenThemeCustomizer
                )
                ContextMenuActionRow(
                    tag = "[SH]",
                    label = if (isRootActive) "Switch to user shell ($)" else "Switch to root shell (#)",
                    textColor = textColor,
                    tint = mutedColor,
                    onClick = onToggleRoot
                )
                ContextMenuActionRow(
                    tag = "[RST]",
                    label = "Restart shell session",
                    textColor = textColor,
                    tint = mutedColor,
                    onClick = onRestart
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Font size (${currentFontSize}sp)",
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button) { onChangeFontSize(-1) }
                                .semantics { contentDescription = "Decrease font size" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A-", color = textColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button) { onChangeFontSize(1) }
                                .semantics { contentDescription = "Increase font size" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A+", color = textColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!Environment.isExternalStorageManager()) {
                    ContextMenuActionRow(
                        tag = "[DIR]",
                        label = "Grant all-files storage access",
                        textColor = textColor,
                        tint = mutedColor,
                        onClick = onStoragePermission
                    )
                }

                ContextMenuActionRow(
                    tag = "[OFF]",
                    label = "Exit and kill app process",
                    textColor = accentColor,
                    tint = accentColor,
                    onClick = onExitApp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Close", color = textColor, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun ContextMenuActionRow(
    tag: String,
    label: String,
    textColor: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            color = tint,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ThemeCustomizerDialog(
    currentTheme: TerminalThemeConfig,
    onDismiss: () -> Unit,
    onApplyTheme: (TerminalThemeConfig) -> Unit
) {
    var presetName by remember { mutableStateOf(currentTheme.presetName) }
    var bgHex by remember { mutableStateOf(currentTheme.backgroundHex) }
    var textHex by remember { mutableStateOf(currentTheme.textHex) }
    var accentHex by remember { mutableStateOf(currentTheme.accentHex) }

    val previewConfig = TerminalThemeConfig(
        presetName = presetName,
        backgroundHex = bgHex,
        textHex = textHex,
        accentHex = accentHex,
        fontSizeSp = currentTheme.fontSizeSp
    )
    val ratio = previewConfig.textContrastRatio
    val ratioFormatted = String.format(Locale.US, "%.2f:1", ratio)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141418),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "Customize Terminal Colors",
                color = Color(0xFFF2F2F5),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(previewConfig.backgroundColor)
                        .border(1.dp, previewConfig.accentColor, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "# ls -la /sdcard",
                            color = previewConfig.accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Contrast ratio: $ratioFormatted (${if (ratio >= 4.5f) "WCAG AA Pass" else "Low Contrast"})",
                            color = previewConfig.textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalThemeConfig.Presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(preset.backgroundColor)
                                .border(1.dp, preset.accentColor, RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button) {
                                    presetName = preset.presetName
                                    bgHex = preset.backgroundHex
                                    textHex = preset.textHex
                                    accentHex = preset.accentHex
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.presetName,
                                color = preset.textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = bgHex,
                    onValueChange = {
                        presetName = "Custom"
                        bgHex = it
                    },
                    label = { Text("Background Hex (e.g. #0A0A0C)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = textHex,
                    onValueChange = {
                        presetName = "Custom"
                        textHex = it
                    },
                    label = { Text("Text Hex (e.g. #F2F2F5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accentHex,
                    onValueChange = {
                        presetName = "Custom"
                        accentHex = it
                    },
                    label = { Text("Accent Hex (e.g. #F43F5E)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApplyTheme(previewConfig) },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Apply", color = previewConfig.accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Cancel", color = Color(0xFFA1A1AA), fontFamily = FontFamily.Monospace)
            }
        }
    )
}