package com.yin.term.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.yin.term.core.YinIcons

enum class AppDestinations(
    val label: String,
    val icon: ImageVector
) {
    Scripts("Scripts", YinIcons.EditNote),
    TERMINAL("Terminal", YinIcons.Code),
    HISTORY("History", YinIcons.History)
}