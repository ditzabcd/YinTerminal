package com.yin.term.core

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yin.term.ui.theme.CrimsonRed
import com.yin.term.ui.theme.CrimsonSubtle
import com.yin.term.ui.theme.ObsidianBorder
import com.yin.term.ui.theme.ObsidianSurface
import com.yin.term.ui.theme.TerminalTextPrimary

@Composable
fun BaseTopBar(
    title: String = "YinTerm",
    animateOnLaunch: Boolean = true,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    var rotationAngle by remember { mutableFloatStateOf(if (animateOnLaunch) 360f else 0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 700),
        label = "BadgeRotation"
    )

    LaunchedEffect(animateOnLaunch) {
        if (animateOnLaunch) {
            rotationAngle = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer { rotationZ = animatedRotation }
                        .background(CrimsonSubtle, RoundedCornerShape(6.dp))
                        .border(1.dp, CrimsonRed, RoundedCornerShape(6.dp))
                        .clickable { rotationAngle += 360f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ">_",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = TerminalTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(3.dp))

                Text(
                    text = "_",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = CrimsonRed,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        HorizontalDivider(thickness = 1.dp, color = ObsidianBorder)
    }
}