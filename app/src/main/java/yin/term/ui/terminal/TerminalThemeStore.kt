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
    val presetName: String = "Ghostty Obsidian",
    val backgroundHex: String = "#090A0F",
    val textHex: String = "#E6E8F0",
    val accentHex: String = "#38BDF8",
    val fontSizeSp: Int = 13,
    val showQuickKeys: Boolean = true
) {
    val backgroundColor: Color
        get() = parseHexColor(backgroundHex, Color(0xFF090A0F))

    val textColor: Color
        get() = parseHexColor(textHex, Color(0xFFE6E8F0))

    val accentColor: Color
        get() = parseHexColor(accentHex, Color(0xFF38BDF8))

    val surfaceColor: Color
        get() {
            val bg = backgroundColor
            val target = if (bg.luminance() < 0.5f) Color.White else Color.Black
            return lerp(bg, target, 0.055f)
        }

    val elevatedSurfaceColor: Color
        get() {
            val bg = backgroundColor
            val target = if (bg.luminance() < 0.5f) Color.White else Color.Black
            return lerp(bg, target, 0.095f)
        }

    val borderColor: Color
        get() = lerp(backgroundColor, textColor, 0.14f)

    val mutedTextColor: Color
        get() = lerp(backgroundColor, textColor, 0.58f)

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
                presetName = "Ghostty Obsidian",
                backgroundHex = "#090A0F",
                textHex = "#E6E8F0",
                accentHex = "#38BDF8"
            ),
            TerminalThemeConfig(
                presetName = "Obsidian Crimson",
                backgroundHex = "#0A0A0C",
                textHex = "#F2F2F5",
                accentHex = "#F43F5E"
            ),
            TerminalThemeConfig(
                presetName = "Catppuccin Mocha",
                backgroundHex = "#11111B",
                textHex = "#CDD6F4",
                accentHex = "#CBA6F7"
            ),
            TerminalThemeConfig(
                presetName = "Tokyo Night",
                backgroundHex = "#16161E",
                textHex = "#C0CAF5",
                accentHex = "#7AA2F7"
            ),
            TerminalThemeConfig(
                presetName = "Emerald Matrix",
                backgroundHex = "#060C09",
                textHex = "#DCFCE7",
                accentHex = "#22C55E"
            ),
            TerminalThemeConfig(
                presetName = "Amber Phosphor",
                backgroundHex = "#0C0906",
                textHex = "#FEF3C7",
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
            presetName = prefs.getString("preset_name", "Ghostty Obsidian") ?: "Ghostty Obsidian",
            backgroundHex = prefs.getString("bg_hex", "#090A0F") ?: "#090A0F",
            textHex = prefs.getString("text_hex", "#E6E8F0") ?: "#E6E8F0",
            accentHex = prefs.getString("accent_hex", "#38BDF8") ?: "#38BDF8",
            fontSizeSp = prefs.getInt("font_size", 13).coerceIn(9, 26),
            showQuickKeys = prefs.getBoolean("show_quick_keys", true)
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
            .putBoolean("show_quick_keys", clamped.showQuickKeys)
            .apply()
    }
}
