package com.yin.term.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

object AnsiParser {

    private val Standard16Palette: Array<Color> = arrayOf(
        Color(0xFF1E1E24), // 0: Black
        Color(0xFFF87171), // 1: Red
        Color(0xFF4ADE80), // 2: Green
        Color(0xFFFACC15), // 3: Yellow
        Color(0xFF60A5FA), // 4: Blue
        Color(0xFFC084FC), // 5: Magenta
        Color(0xFF22D3EE), // 6: Cyan
        Color(0xFFE4E4E7), // 7: White
        Color(0xFF71717A), // 8: Bright Black
        Color(0xFFFCA5A5), // 9: Bright Red
        Color(0xFF86EFAC), // 10: Bright Green
        Color(0xFFFEF08A), // 11: Bright Yellow
        Color(0xFF93C5FD), // 12: Bright Blue
        Color(0xFFD8B4FE), // 13: Bright Magenta
        Color(0xFF67E8F9), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    private val CsiSequenceRegex = Regex("\u001B\\[([0-9:;?]*)([ -/]*[@-~])") private val OscSequenceRegex = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
    // Strips non-CSI VT100 sequences such as ESC(B (charset reset) emitted by tput/ls
    private val Vt100SingleEscRegex = Regex("\u001B(?:\\([0-9A-Za-z]\vert{}\\)[0-9A-Za-z]|[=>78DMEHc])")

    private data class AnsiStyleState(
        var fg: Color,
        var bg: Color,
        var bold: Boolean = false,
        var dim: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strikethrough: Boolean = false,
        var inverse: Boolean = false
    ) {
        fun reset(defaultFg: Color) {
            fg = defaultFg
            bg = Color.Unspecified
            bold = false
            dim = false
            italic = false
            underline = false
            strikethrough = false
            inverse = false
        }

        fun toSpanStyle(defaultBg: Color): SpanStyle {
            val baseFg = if (dim && fg != Color.Unspecified) fg.copy(alpha = fg.alpha * 0.65f) else fg
            val resolvedBg = if (bg != Color.Unspecified) bg else defaultBg

            val effectiveFg = if (inverse) {
                if (resolvedBg != Color.Unspecified) resolvedBg else Color(0xFF0A0A0C)
            } else {
                baseFg
            }

            val effectiveBg = if (inverse) {
                if (baseFg != Color.Unspecified) baseFg else Color(0xFFF2F2F5)
            } else {
                bg
            }

            val decoration = when {
                underline && strikethrough -> TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
                underline -> TextDecoration.Underline
                strikethrough -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }

            return SpanStyle(
                color = effectiveFg,
                background = effectiveBg,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = decoration
            )
        }
    }

    fun parse(
        rawText: String,
        defaultFg: Color,
        defaultBg: Color = Color.Unspecified
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val normalized = normalizeCarriageReturns(rawText)
            .replace(OscSequenceRegex, "")
            .replace(Vt100SingleEscRegex, "")

        if (!normalized.contains('\u001B')) {
            return AnnotatedString(normalized)
        }

        val state = AnsiStyleState(fg = defaultFg, bg = Color.Unspecified)

        return buildAnnotatedString {
            var cursor = 0
            for (match in CsiSequenceRegex.findAll(normalized)) {
                val start = match.range.first
                if (start > cursor) {
                    val segment = normalized.substring(cursor, start)
                    pushStyle(state.toSpanStyle(defaultBg))
                    append(segment)
                    pop()
                }

                val paramsRaw = match.groupValues[1]
                val finalByte = match.groupValues[2]

                if (finalByte == "m" && !paramsRaw.startsWith("?")) {
                    applySgrCodes(paramsRaw, state, defaultFg)
                }

                cursor = match.range.last + 1
            }

            if (cursor < normalized.length) {
                val tail = normalized.substring(cursor).replace("\u001B", "")
                if (tail.isNotEmpty()) {
                    pushStyle(state.toSpanStyle(defaultBg))
                    append(tail)
                    pop()
                }
            }
        }
    }

    fun terminalTextStyle(
        fontSizeSp: Int,
        defaultColor: Color
    ): TextStyle {
        return TextStyle(
            color = defaultColor,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 4).sp,
            fontFeatureSettings = "tnum"
        )
    }

    private fun normalizeCarriageReturns(text: String): String {
        if (!text.contains('\r')) return text
        val lines = text.split('\n')
        return lines.joinToString("\n") { line ->
            val lastCr = line.lastIndexOf('\r')
            if (lastCr == -1) {
                line
            } else if (lastCr == line.lastIndex) {
                line.substring(0, lastCr)
            } else {
                line.substring(lastCr + 1)
            }
        }
    }

    private fun applySgrCodes(paramsRaw: String, state: AnsiStyleState, defaultFg: Color) {
        if (paramsRaw.isEmpty()) {
            state.reset(defaultFg)
            return
        }

        val tokens = paramsRaw.replace(':', ';').split(';')
        val codes = IntArray(tokens.size) { idx -> tokens[idx].toIntOrNull() ?: 0 }

        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> state.reset(defaultFg)
                1 -> state.bold = true
                2 -> state.dim = true
                3 -> state.italic = true
                4 -> state.underline = true
                7 -> state.inverse = true
                9 -> state.strikethrough = true
                22 -> {
                    state.bold = false
                    state.dim = false
                }
                23 -> state.italic = false
                24 -> state.underline = false
                27 -> state.inverse = false
                29 -> state.strikethrough = false
                in 30..37 -> state.fg = Standard16Palette[code - 30]
                38 -> i += parseExtendedColor(codes, i + 1) { color -> state.fg = color }
                39 -> state.fg = defaultFg
                in 40..47 -> state.bg = Standard16Palette[code - 40]
                48 -> i += parseExtendedColor(codes, i + 1) { color -> state.bg = color }
                49 -> state.bg = Color.Unspecified
                in 90..97 -> state.fg = Standard16Palette[(code - 90) + 8]
                in 100..107 -> state.bg = Standard16Palette[(code - 100) + 8]
            }
            i++
        }
    }

    private fun parseExtendedColor(
        codes: IntArray,
        startIndex: Int,
        onColorParsed: (Color) -> Unit
    ): Int {
        if (startIndex >= codes.size) return 0
        return when (codes[startIndex]) {
            5 -> {
                if (startIndex + 1 < codes.size) {
                    onColorParsed(xterm256ToColor(codes[startIndex + 1].coerceIn(0, 255)))
                    2
                } else {
                    1
                }
            }
            2 -> {
                val hasColorSpace = startIndex + 4 < codes.size && codes.size - startIndex >= 5
                val rIdx = if (hasColorSpace) startIndex + 2 else startIndex + 1
                if (rIdx + 2 < codes.size) {
                    val r = codes[rIdx].coerceIn(0, 255)
                    val g = codes[rIdx + 1].coerceIn(0, 255)
                    val b = codes[rIdx + 2].coerceIn(0, 255)
                    onColorParsed(Color(r, g, b))
                    if (hasColorSpace) 4 else 3
                } else {
                    1
                }
            }
            else -> 1
        }
    }

    private fun xterm256ToColor(index: Int): Color {
        if (index < 16) return Standard16Palette[index]
        if (index in 16..231) {
            val cubeIndex = index - 16
            val rLevel = cubeIndex / 36
            val gLevel = (cubeIndex % 36) / 6
            val bLevel = cubeIndex % 6
            val r = if (rLevel == 0) 0 else 55 + rLevel * 40
            val g = if (gLevel == 0) 0 else 55 + gLevel * 40
            val b = if (bLevel == 0) 0 else 55 + bLevel * 40
            return Color(r, g, b)
        }
        val gray = (8 + (index - 232) * 10).coerceIn(0, 255)
        return Color(gray, gray, gray)
    }
}