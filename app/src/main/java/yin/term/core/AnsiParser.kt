package com.yin.term.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.unit.sp

object AnsiParser {

    // Font Monospace dengan fallback system Emoji (NotoColorEmoji) agar tidak muncul kotak □□□
    val TerminalFontFamily: FontFamily = FontFamily.Monospace

    fun terminalTextStyle(
        fontSizeSp: Int,
        defaultColor: Color
    ): TextStyle = TextStyle(
        fontFamily = TerminalFontFamily,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.38f).sp,
        color = defaultColor,
        platformStyle = PlatformTextStyle(
            emojiSupportMatch = EmojiSupportMatch.Default
        )
    )

    private val SGR_REGEX = Regex("\u001B\\[([0-9;]*)m")
    private val NON_SGR_CONTROL_REGEX = Regex(
        "\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B\\[[0-9;?]*[A-LN-Za-z]"
    )

    private val STANDARD_COLORS = listOf(
        Color(0xFF18181B), // 0: Black
        Color(0xFFD32F2F), // 1: Crimson Red
        Color(0xFF22C55E), // 2: Green
        Color(0xFFEAB308), // 3: Yellow
        Color(0xFF3B82F6), // 4: Blue
        Color(0xFFEC4899), // 5: Magenta
        Color(0xFF06B6D4), // 6: Cyan
        Color(0xFFE4E4E7)  // 7: White
    )

    private val BRIGHT_COLORS = listOf(
        Color(0xFF71717A), // 8: Bright Black (Gray)
        Color(0xFFFF5252), // 9: Bright Red
        Color(0xFF4ADE80), // 10: Bright Green
        Color(0xFFFACC15), // 11: Bright Yellow
        Color(0xFF60A5FA), // 12: Bright Blue
        Color(0xFFF472B6), // 13: Bright Magenta
        Color(0xFF22D3EE), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    private data class AnsiState(
        val fg: Color? = null,
        val bg: Color? = null,
        val bold: Boolean = false,
        val dim: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val inverse: Boolean = false
    ) {
        fun toSpanStyle(defaultFg: Color, defaultBg: Color): SpanStyle {
            val resolvedFg = when {
                inverse -> bg ?: defaultBg
                dim && fg != null -> fg.copy(alpha = 0.65f)
                dim -> defaultFg.copy(alpha = 0.65f)
                else -> fg ?: defaultFg
            }
            val resolvedBg = if (inverse) (fg ?: defaultFg) else (bg ?: Color.Unspecified)

            val decorations = buildList {
                if (underline) add(TextDecoration.Underline)
                if (strikethrough) add(TextDecoration.LineThrough)
            }

            return SpanStyle(
                color = resolvedFg,
                background = resolvedBg,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
            )
        }
    }

    fun parse(
        rawText: String,
        defaultFg: Color = Color(0xFFE4E4E7),
        defaultBg: Color = Color(0xFF0A0A0C)
    ): AnnotatedString {
        val cleaned = NON_SGR_CONTROL_REGEX.replace(rawText, "")
        if (!cleaned.contains('\u001B')) {
            return AnnotatedString(cleaned)
        }

        return buildAnnotatedString {
            var state = AnsiState()
            var lastIndex = 0

            for (match in SGR_REGEX.findAll(cleaned)) {
                val start = match.range.first
                if (start > lastIndex) {
                    val segment = cleaned.substring(lastIndex, start)
                    pushStyle(state.toSpanStyle(defaultFg, defaultBg))
                    append(segment)
                    pop()
                }

                val codeStr = match.groupValues[1]
                val codes = if (codeStr.isEmpty()) {
                    listOf(0)
                } else {
                    codeStr.split(';').mapNotNull { it.toIntOrNull() }
                }
                state = applyCodes(state, codes)
                lastIndex = match.range.last + 1
            }

            if (lastIndex < cleaned.length) {
                pushStyle(state.toSpanStyle(defaultFg, defaultBg))
                append(cleaned.substring(lastIndex))
                pop()
            }
        }
    }

    private fun applyCodes(initial: AnsiState, codes: List<Int>): AnsiState {
        var state = initial
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> state = AnsiState()
                1 -> state = state.copy(bold = true)
                2 -> state = state.copy(dim = true)
                3 -> state = state.copy(italic = true)
                4 -> state = state.copy(underline = true)
                7 -> state = state.copy(inverse = true)
                9 -> state = state.copy(strikethrough = true)
                22 -> state = state.copy(bold = false, dim = false)
                23 -> state = state.copy(italic = false)
                24 -> state = state.copy(underline = false)
                27 -> state = state.copy(inverse = false)
                29 -> state = state.copy(strikethrough = false)

                in 30..37 -> state = state.copy(fg = STANDARD_COLORS[code - 30])
                38 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        state = state.copy(fg = xterm256ToColor(codes[i + 2]))
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        state = state.copy(
                            fg = Color(
                                red = codes[i + 2].coerceIn(0, 255),
                                green = codes[i + 3].coerceIn(0, 255),
                                blue = codes[i + 4].coerceIn(0, 255)
                            )
                        )
                        i += 4
                    }
                }
                39 -> state = state.copy(fg = null)

                in 40..47 -> state = state.copy(bg = STANDARD_COLORS[code - 40])
                48 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        state = state.copy(bg = xterm256ToColor(codes[i + 2]))
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        state = state.copy(
                            bg = Color(
                                red = codes[i + 2].coerceIn(0, 255),
                                green = codes[i + 3].coerceIn(0, 255),
                                blue = codes[i + 4].coerceIn(0, 255)
                            )
                        )
                        i += 4
                    }
                }
                49 -> state = state.copy(bg = null)

                in 90..97 -> state = state.copy(fg = BRIGHT_COLORS[code - 90])
                in 100..107 -> state = state.copy(bg = BRIGHT_COLORS[code - 100])
            }
            i++
        }
        return state
    }

    private fun xterm256ToColor(index: Int): Color {
        val idx = index.coerceIn(0, 255)
        return when {
            idx < 8 -> STANDARD_COLORS[idx]
            idx < 16 -> BRIGHT_COLORS[idx - 8]
            idx < 232 -> {
                val n = idx - 16
                val r = n / 36
                val g = (n % 36) / 6
                val b = n % 6
                val levels = intArrayOf(0, 95, 135, 175, 215, 255)
                Color(levels[r], levels[g], levels[b])
            }
            else -> {
                val gray = 8 + (idx - 232) * 10
                Color(gray, gray, gray)
            }
        }
    }
}