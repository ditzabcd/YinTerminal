package com.yin.term.ui.terminal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
        get() {
            val bg = backgroundColor
            val target = if (bg.luminance() < 0.5f) Color.White else Color.Black
            return lerp(bg, target, 0.06f)
        }

    val borderColor: Color
        get() = lerp(backgroundColor, textColor, 0.16f)

    val mutedTextColor: Color
        get() = lerp(backgroundColor, textColor, 0.62f)

    val textContrastRatio: Float
        get() {
            val l1 = backgroundColor.luminance()
            val l2 = textColor.luminance()
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05f) / (darker + 0.05f)
        }

    companion object {
        val Presets: List<TerminalThemeConfig> = listOf(
            TerminalThemeConfig(
                presetName = "Obsidian Crimson",
                backgroundHex = "#0A0A0C",
                textHex = "#F2F2F5",
                accentHex = "#F43F5E"
            ),
            TerminalThemeConfig(
                presetName = "Emerald Matrix",
                backgroundHex = "#070D0A",
                textHex = "#E2F8EC",
                accentHex = "#22C55E"
            ),
            TerminalThemeConfig(
                presetName = "Tokyo Night",
                backgroundHex = "#1A1B26",
                textHex = "#C0CAF5",
                accentHex = "#7AA2F7"
            ),
            TerminalThemeConfig(
                presetName = "Nord Frost",
                backgroundHex = "#2E3440",
                textHex = "#ECEFF4",
                accentHex = "#88C0D0"
            ),
            TerminalThemeConfig(
                presetName = "Amber Phosphor",
                backgroundHex = "#0D0A06",
                textHex = "#FDE68A",
                accentHex = "#F59E0B"
            )
        )

        private fun parseHexColor(rawHex: String, fallback: Color): Color {
            val cleaned = rawHex.trim()
            if (cleaned.isEmpty()) return fallback
            val withHash = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
            if (withHash.length != 7 && withHash.length != 9) return fallback
            return runCatching {
                Color(android.graphics.Color.parseColor(withHash))
            }.getOrDefault(fallback)
        }
    }
}

class TerminalThemeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yinterm_theme_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadSavedTheme())
    val theme: StateFlow<TerminalThemeConfig> = _theme.asStateFlow()

    private fun loadSavedTheme(): TerminalThemeConfig {
        return TerminalThemeConfig(
            presetName = prefs.getString("preset_name", "Obsidian Crimson") ?: "Obsidian Crimson",
            backgroundHex = prefs.getString("bg_hex", "#0A0A0C") ?: "#0A0A0C",
            textHex = prefs.getString("text_hex", "#F2F2F5") ?: "#F2F2F5",
            accentHex = prefs.getString("accent_hex", "#F43F5E") ?: "#F43F5E",
            fontSizeSp = prefs.getInt("font_size", 13).coerceIn(9, 26)
        )
    }

    fun updateTheme(newConfig: TerminalThemeConfig) {
        val clamped = newConfig.copy(fontSizeSp = newConfig.fontSizeSp.coerceIn(9, 26))
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