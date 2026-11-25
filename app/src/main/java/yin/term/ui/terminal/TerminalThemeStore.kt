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
    val presetName: String = "Black AMOLED Red Dragon",
    val backgroundHex: String = "#000000",
    val textHex: String = "#F4F4F6",
    val accentHex: String = "#EF233C",
    val fontSizeSp: Int = 13,
    val showQuickKeys: Boolean = true
) {
    val backgroundColor: Color
        get() = parseHexColor(backgroundHex, Color(0xFF000000))

    val textColor: Color
        get() = parseHexColor(textHex, Color(0xFFF4F4F6))

    val accentColor: Color
        get() = parseHexColor(accentHex, Color(0xFFEF233C))

    val secondaryAccentColor: Color
        get() = lerp(accentColor, Color.White, 0.22f)

    val surfaceColor: Color
        get() {
            val bg = backgroundColor
            return if (bg.luminance() < 0.05f) {
                Color(0xFF09090B)
            } else {
                lerp(bg, Color.White, 0.055f)
            }
        }

    val elevatedSurfaceColor: Color
        get() {
            val bg = backgroundColor
            return if (bg.luminance() < 0.05f) {
                Color(0xFF111115)
            } else {
                lerp(bg, Color.White, 0.095f)
            }
        }

    val borderColor: Color
        get() = lerp(surfaceColor, accentColor, 0.24f)

    val mutedTextColor: Color
        get() = lerp(backgroundColor, textColor, 0.56f)

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
                presetName = "Black AMOLED Red Dragon",
                backgroundHex = "#000000",
                textHex = "#F4F4F6",
                accentHex = "#EF233C"
            ),
            TerminalThemeConfig(
                presetName = "Crimson Ember AMOLED",
                backgroundHex = "#000000",
                textHex = "#FFE4E6",
                accentHex = "#FF2A55"
            ),
            TerminalThemeConfig(
                presetName = "Ghostty Obsidian",
                backgroundHex = "#090A0F",
                textHex = "#E6E8F0",
                accentHex = "#38BDF8"
            ),
            TerminalThemeConfig(
                presetName = "Catppuccin Mocha",
                backgroundHex = "#11111B",
                textHex = "#CDD6F4",
                accentHex = "#CBA6F7"
            ),
            TerminalThemeConfig(
                presetName = "Emerald Matrix AMOLED",
                backgroundHex = "#000000",
                textHex = "#DCFCE7",
                accentHex = "#22C55E"
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
        val savedPreset = prefs.getString("preset_name", "Black AMOLED Red Dragon")
            ?: "Black AMOLED Red Dragon"

        // Migrate old default Ghostty theme to Black AMOLED Red Dragon automatically
        if (savedPreset == "Ghostty Obsidian" && !prefs.getBoolean("user_selected_theme", false)) {
            return TerminalThemeConfig()
        }

        return TerminalThemeConfig(
            presetName = savedPreset,
            backgroundHex = prefs.getString("bg_hex", "#000000") ?: "#000000",
            textHex = prefs.getString("text_hex", "#F4F4F6") ?: "#F4F4F6",
            accentHex = prefs.getString("accent_hex", "#EF233C") ?: "#EF233C",
            fontSizeSp = prefs.getInt("font_size", 13).coerceIn(9, 26),
            showQuickKeys = prefs.getBoolean("show_quick_keys", true)
        )
    }

    fun updateTheme(newConfig: TerminalThemeConfig) {
        val clamped = newConfig.copy(fontSizeSp = newConfig.fontSizeSp.coerceIn(9, 26))
        _theme.value = clamped
        prefs.edit()
            .putBoolean("user_selected_theme", true)
            .putString("preset_name", clamped.presetName)
            .putString("bg_hex", clamped.backgroundHex)
            .putString("text_hex", clamped.textHex)
            .putString("accent_hex", clamped.accentHex)
            .putInt("font_size", clamped.fontSizeSp)
            .putBoolean("show_quick_keys", clamped.showQuickKeys)
            .apply()
    }
}