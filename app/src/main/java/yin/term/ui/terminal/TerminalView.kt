package com.yin.term.ui.terminal

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.yin.term.data.datasource.NativePty
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

private fun Modifier.pinchToZoom(
    currentFontSize: Int,
    onFontSizeChange: (Int) -> Unit
): Modifier = pointerInput(currentFontSize) {
    awaitEachGesture {
        var zoomAccum = 1f
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f) {
                    zoomAccum *= zoomChange
                    if (zoomAccum >= 1.12f) {
                        onFontSizeChange((currentFontSize + 1).coerceIn(9, 26))
                        zoomAccum = 1f
                    } else if (zoomAccum <= 0.88f) {
                        onFontSizeChange((currentFontSize - 1).coerceIn(9, 26))
                        zoomAccum = 1f
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Terminal(
    viewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory),
    terminalCommandChannel: Flow<String>
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val isRootActive by viewModel.isRootActive.collectAsStateWithLifecycle()
    val activeShellName by viewModel.activeShellName.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeStore.theme.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val inputFocusRequester = remember { FocusRequester() }

    var showContextDialog by remember { mutableStateOf(false) }
    var selectedLineText by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var lastSubmitTimestamp by remember { mutableLongStateOf(0L) }
    var gridCols by remember { mutableIntStateOf(80) }
    var gridRows by remember { mutableIntStateOf(32) }

    val submitCommandSafe: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastSubmitTimestamp > 140L) {
            lastSubmitTimestamp = now
            ctrlArmed = false
            viewModel.executeWithContext(context)
        }
    }

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
    val elevatedSurfaceColor = themeConfig.elevatedSurfaceColor
    val borderColor = themeConfig.borderColor
    val textColor = themeConfig.textColor
    val mutedColor = themeConfig.mutedTextColor
    val accentColor = themeConfig.accentColor
    val fontSizeSp = themeConfig.fontSizeSp
    val isCommandRunning = uiState.executionState == TerminalState.Running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .pinchToZoom(
                currentFontSize = fontSizeSp,
                onFontSizeChange = { updatedSize ->
                    viewModel.themeStore.updateTheme(
                        themeConfig.copy(fontSizeSp = updatedSize)
                    )
                }
            )
    ) {
        // Slim Status Strip (~4% vertical footprint)
        SlimTerminalStatusBar(
            currentDirectory = currentDirectory,
            isRootActive = isRootActive,
            shellName = activeShellName,
            gridLabel = "${gridCols}x${gridRows}",
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            onToggleRoot = { viewModel.onEvent(TerminalUiEvent.ToggleRoot) },
            onClear = { viewModel.onEvent(TerminalUiEvent.ClearOutput) },
            onOpenMenu = {
                selectedLineText = null
                showContextDialog = true
            }
        )

        // 90%+ Pure Terminal Viewport
        BoxWithConstraints(
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
            val charWidthDp = fontSizeSp * 0.6f
            val lineHeightDp = (fontSizeSp + 5).toFloat()
            val cols = ((maxWidth.value - 24f) / charWidthDp).toInt().coerceIn(24, 240)
            val rows = ((maxHeight.value - 12f) / lineHeightDp).toInt().coerceIn(8, 120)

            LaunchedEffect(rows, cols) {
                gridCols = cols
                gridRows = rows
                viewModel.updateTerminalSize(rows, cols)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(
                    items = orderedLogs,
                    key = { index, log -> "${index}_${log.state.name}_${log.message.length}" }
                ) { _, log ->
                    TerminalOutputLine(
                        log = log,
                        fontSizeSp = fontSizeSp,
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

                item(key = "active_inline_prompt") {
                    InlineTerminalPrompt(
                        command = uiState.command,
                        currentDirectory = currentDirectory,
                        isRootActive = isRootActive,
                        isCommandRunning = isCommandRunning,
                        ctrlArmed = ctrlArmed,
                        fontSizeSp = fontSizeSp,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        focusRequester = inputFocusRequester,
                        onCommandChange = { newText ->
                            if (ctrlArmed && newText.length > uiState.command.length) {
                                val addedChar = newText.last().lowercaseChar()
                                ctrlArmed = false
                                if (addedChar == 'c') {
                                    viewModel.onEvent(TerminalUiEvent.Terminate)
                                } else if (addedChar == 'l') {
                                    viewModel.onEvent(TerminalUiEvent.ClearOutput)
                                }
                            } else {
                                viewModel.onEvent(TerminalUiEvent.OnCommandChange(newText))
                            }
                        },
                        onInterrupt = { viewModel.onEvent(TerminalUiEvent.Terminate) },
                        onSubmit = submitCommandSafe
                    )
                }
            }

            if (!isAtBottom && orderedLogs.isNotEmpty()) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            listState.scrollToItem(orderedLogs.lastIndex)
                        }
                    },
                    color = elevatedSurfaceColor,
                    border = BorderStroke(1.dp, accentColor),
                    shape = RoundedCornerShape(6.dp),
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

        // Compact 1-Row Quick Keys Bar (~5% vertical footprint)
        AnimatedVisibility(
            visible = themeConfig.showQuickKeys,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TerminalQuickKeysBar(
                ctrlArmed = ctrlArmed,
                surfaceColor = surfaceColor,
                elevatedColor = elevatedSurfaceColor,
                borderColor = borderColor,
                textColor = textColor,
                accentColor = accentColor,
                onToggleCtrl = { ctrlArmed = !ctrlArmed },
                onInterrupt = {
                    ctrlArmed = false
                    viewModel.onEvent(TerminalUiEvent.Terminate)
                },
                onInsertToken = { token ->
                    ctrlArmed = false
                    val updated = if (uiState.command.isEmpty() || uiState.command.endsWith(" ")) {
                        uiState.command + token
                    } else {
                        uiState.command + token
                    }
                    viewModel.onEvent(TerminalUiEvent.OnCommandChange(updated))
                    runCatching { inputFocusRequester.requestFocus() }
                },
                onClearLine = {
                    ctrlArmed = false
                    viewModel.onEvent(TerminalUiEvent.OnCommandChange(""))
                }
            )
        }
    }

    if (showContextDialog) {
        TerminalContextMenuDialog(
            selectedLineText = selectedLineText,
            isRootActive = isRootActive,
            currentFontSize = fontSizeSp,
            showQuickKeys = themeConfig.showQuickKeys,
            surfaceColor = elevatedSurfaceColor,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            onDismiss = { showContextDialog = false },
            onInterrupt = {
                viewModel.onEvent(TerminalUiEvent.Terminate)
                showContextDialog = false
            },
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
            onToggleQuickKeys = {
                viewModel.themeStore.updateTheme(
                    themeConfig.copy(showQuickKeys = !themeConfig.showQuickKeys)
                )
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
private fun SlimTerminalStatusBar(
    currentDirectory: String,
    isRootActive: Boolean,
    shellName: String,
    gridLabel: String,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onToggleRoot: () -> Unit,
    onClear: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onToggleRoot)
                    .semantics { contentDescription = "Toggle root or user shell" }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (NativePty.isAvailable) accentColor else mutedColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRootActive) "$shellName #" else "$shellName $",
                        color = if (isRootActive) accentColor else textColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = currentDirectory,
                color = mutedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = gridLabel,
                color = mutedColor.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onClear)
                    .semantics { contentDescription = "Clear terminal" }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CLR",
                    color = mutedColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onOpenMenu)
                    .semantics { contentDescription = "Terminal options" }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OPT",
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = borderColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalOutputLine(
    log: TerminalLog,
    fontSizeSp: Int,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onTapLine: () -> Unit,
    onLongPressLine: (String) -> Unit
) {
    val isCommandEcho = log.state == TerminalState.Info
    val isError = log.state == TerminalState.Error

    val baseColor = when {
        isError -> accentColor
        isCommandEcho -> accentColor
        else -> textColor
    }

    val cleanMessage = log.message.trimEnd('\n')
    val displayText = remember(cleanMessage, baseColor, isCommandEcho, textColor) {
        if (isCommandEcho) {
            AnnotatedString(cleanMessage)
        } else {
            AnsiParser.parse(
                rawText = cleanMessage,
                defaultFg = if (isError) accentColor else textColor,
                defaultBg = Color.Unspecified
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isCommandEcho) 4.dp else 0.dp)
            .combinedClickable(
                onClick = onTapLine,
                onLongClick = { onLongPressLine(displayText.text) }
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (isCommandEcho) {
            Text(
                text = "❯ ",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (isError) {
            Text(
                text = "! ",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = displayText,
            style = AnsiParser.terminalTextStyle(
                fontSizeSp = fontSizeSp,
                defaultColor = if (isCommandEcho) mutedColor else baseColor
            ).copy(
                fontWeight = if (isCommandEcho) FontWeight.SemiBold else FontWeight.Normal
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlineTerminalPrompt(
    command: String,
    currentDirectory: String,
    isRootActive: Boolean,
    isCommandRunning: Boolean,
    ctrlArmed: Boolean,
    fontSizeSp: Int,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    focusRequester: FocusRequester,
    onCommandChange: (String) -> Unit,
    onInterrupt: () -> Unit,
    onSubmit: () -> Unit
) {
    val dirLabel = remember(currentDirectory) {
        if (currentDirectory == "/") "/" else currentDirectory.substringAfterLast('/').ifEmpty { "/" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$dirLabel ${if (isRootActive) "#" else "❯"}",
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold
        )

        if (ctrlArmed) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "^CTRL",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, accentColor, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (command.isEmpty()) {
                Text(
                    text = if (isCommandRunning) "running..." else "type command...",
                    color = mutedColor.copy(alpha = 0.45f),
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
                    autoCorrectEnabled = false,
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

        if (isCommandRunning) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, accentColor, RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onInterrupt)
                    .semantics { contentDescription = "Interrupt process" }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "^C",
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (command.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
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

@Composable
private fun TerminalQuickKeysBar(
    ctrlArmed: Boolean,
    surfaceColor: Color,
    elevatedColor: Color,
    borderColor: Color,
    textColor: Color,
    accentColor: Color,
    onToggleCtrl: () -> Unit,
    onInterrupt: () -> Unit,
    onInsertToken: (String) -> Unit,
    onClearLine: () -> Unit
) {
    val quickTokens = listOf(
        "/" to "/",
        "-" to "-",
        "|" to "|",
        "~" to "~",
        "&&" to " && ",
        "ls -la" to "ls -la",
        "pkg " to "pkg "
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = borderColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickKeyChip(
                label = "CTRL",
                highlighted = ctrlArmed,
                elevatedColor = elevatedColor,
                borderColor = borderColor,
                textColor = textColor,
                accentColor = accentColor,
                onClick = onToggleCtrl
            )
            QuickKeyChip(
                label = "^C",
                highlighted = false,
                elevatedColor = elevatedColor,
                borderColor = borderColor,
                textColor = accentColor,
                accentColor = accentColor,
                onClick = onInterrupt
            )
            quickTokens.forEach { (label, token) ->
                QuickKeyChip(
                    label = label,
                    highlighted = false,
                    elevatedColor = elevatedColor,
                    borderColor = borderColor,
                    textColor = textColor,
                    accentColor = accentColor,
                    onClick = { onInsertToken(token) }
                )
            }
            QuickKeyChip(
                label = "DEL",
                highlighted = false,
                elevatedColor = elevatedColor,
                borderColor = borderColor,
                textColor = textColor,
                accentColor = accentColor,
                onClick = onClearLine
            )
        }
    }
}

@Composable
private fun QuickKeyChip(
    label: String,
    highlighted: Boolean,
    elevatedColor: Color,
    borderColor: Color,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .defaultMinSize(minWidth = 42.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (highlighted) accentColor.copy(alpha = 0.18f) else elevatedColor)
            .border(
                width = 1.dp,
                color = if (highlighted) accentColor else borderColor,
                shape = RoundedCornerShape(5.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) accentColor else textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TerminalContextMenuDialog(
    selectedLineText: String?,
    isRootActive: Boolean,
    currentFontSize: Int,
    showQuickKeys: Boolean,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onDismiss: () -> Unit,
    onInterrupt: () -> Unit,
    onCopyLine: (String) -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
    onToggleRoot: () -> Unit,
    onToggleQuickKeys: () -> Unit,
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
                ContextMenuActionRow(
                    tag = "[INT]",
                    label = "Send interrupt (Ctrl+C)",
                    textColor = accentColor,
                    tint = accentColor,
                    onClick = onInterrupt
                )
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
                    tag = "[KEY]",
                    label = if (showQuickKeys) "Hide quick keys bar" else "Show quick keys bar",
                    textColor = textColor,
                    tint = mutedColor,
                    onClick = onToggleQuickKeys
                )
                ContextMenuActionRow(
                    tag = "[RGB]",
                    label = "Themes and color customizer",
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
                        text = "Font Size (${currentFontSize}sp)",
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
                                .semantics { contentDescription = "Zoom out text" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A-", color = textColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button) { onChangeFontSize(1) }
                                .semantics { contentDescription = "Zoom in text" },
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
        fontSizeSp = currentTheme.fontSizeSp,
        showQuickKeys = currentTheme.showQuickKeys
    )
    val ratio = previewConfig.textContrastRatio
    val ratioFormatted = String.format(Locale.US, "%.2f:1", ratio)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141418),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "Terminal Themes",
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
                            text = "sdcard ❯ pkg update && ls -la",
                            color = previewConfig.accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Contrast: $ratioFormatted (${if (ratio >= 4.5f) "WCAG AA Pass" else "Low Contrast"})",
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
                                .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(preset.backgroundColor)
                                .border(1.dp, preset.accentColor, RoundedCornerShape(5.dp))
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
                    label = { Text("Background Hex (#090A0F)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = textHex,
                    onValueChange = {
                        presetName = "Custom"
                        textHex = it
                    },
                    label = { Text("Text Hex (#E6E8F0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accentHex,
                    onValueChange = {
                        presetName = "Custom"
                        accentHex = it
                    },
                    label = { Text("Accent Hex (#38BDF8)") },
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