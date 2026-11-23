package com.yin.term.ui.terminal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

data class TerminalThemeConfig(
    val presetName: String = "Obsidian Crimson",
    val backgroundHex: String = "#0A0A0C",
    val textHex: String = "#F2F2F5",
    val accentHex: String = "#F43F5E",
    val fontSizeSp: Int = 13
) {
    val backgroundColor: Color
        get() = parseHexColor(backgroundHex, Color(0xFF0A0A0C))

    val textColor: Color
        get() = parseHexColor(textHex, Color(0xFFF2F2F5))

    val accentColor: Color
        get() = parseHexColor(accentHex, Color(0xFFF43F5E))

    val surfaceColor: Color
        get() = if (backgroundColor.luminance() > 0.5f) {
            Color(0xFFE2E8F0)
        } else {
            Color(0xFF141418)
        }

    val borderColor: Color
        get() = if (backgroundColor.luminance() > 0.5f) {
            Color(0xFFCBD5E1)
        } else {
            Color(0xFF27272F)
        }

    val mutedTextColor: Color
        get() = if (backgroundColor.luminance() > 0.5f) {
            Color(0xFF475569)
        } else {
            Color(0xFFA1A1AA)
        }

    val textContrastRatio: Float
        get() = computeContrastRatio(backgroundColor, textColor)

    companion object {
        val Presets: List<TerminalThemeConfig> = listOf(
            TerminalThemeConfig("Obsidian Crimson", "#0A0A0C", "#F2F2F5", "#F43F5E", 13),
            TerminalThemeConfig("Phosphor Green", "#080C0A", "#E4F2EA", "#22C55E", 13),
            TerminalThemeConfig("Amber CRT", "#0C0A07", "#F5EFE6", "#F59E0B", 13),
            TerminalThemeConfig("Slate Cyan", "#090D12", "#EDF2F7", "#38BDF8", 13),
            TerminalThemeConfig("Paper Light", "#F8FAFC", "#0F172A", "#B91C1C", 13)
        )

        fun parseHexColor(hex: String, fallback: Color): Color {
            val clean = hex.trim().removePrefix("#")
            return runCatching {
                when (clean.length) {
                    6 -> Color(("FF$clean").toLong(16))
                    8 -> Color(clean.toLong(16))
                    else -> fallback
                }
            }.getOrDefault(fallback)
        }

        fun computeContrastRatio(bg: Color, fg: Color): Float {
            val l1 = bg.luminance()
            val l2 = fg.luminance()
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05f) / (darker + 0.05f)
        }
    }
}

class TerminalThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("yinterm_theme_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadSavedTheme())
    val theme: StateFlow<TerminalThemeConfig> = _theme.asStateFlow()

    private fun loadSavedTheme(): TerminalThemeConfig {
        return TerminalThemeConfig(
            presetName = prefs.getString("preset_name", "Obsidian Crimson") ?: "Obsidian Crimson",
            backgroundHex = prefs.getString("bg_hex", "#0A0A0C") ?: "#0A0A0C",
            textHex = prefs.getString("text_hex", "#F2F2F5") ?: "#F2F2F5",
            accentHex = prefs.getString("accent_hex", "#F43F5E") ?: "#F43F5E",
            fontSizeSp = prefs.getInt("font_size", 13).coerceIn(10, 22)
        )
    }

    fun updateTheme(newConfig: TerminalThemeConfig) {
        val clamped = newConfig.copy(fontSizeSp = newConfig.fontSizeSp.coerceIn(10, 22))
        _theme.value = clamped
        prefs.edit()
            .putString("preset_name", clamped.presetName)
            .putString("bg_hex", clamped.backgroundHex)
            .putString("text_hex", clamped.textHex)
            .putString("accent_hex", clamped.accentHex)
            .putInt("font_size", clamped.fontSizeSp)
            .apply()
    }
}