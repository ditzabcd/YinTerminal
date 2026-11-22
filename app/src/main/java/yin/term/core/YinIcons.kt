package com.yin.term.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object YinIcons {

    val Add: ImageVector by lazy {
        materialIcon("Add") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.0f, 13.0f)
                horizontalLineTo(13.0f)
                verticalLineTo(19.0f)
                horizontalLineTo(11.0f)
                verticalLineTo(13.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(11.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(13.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(13.0f)
                close()
            }
        }
    }

    val Clear: ImageVector by lazy {
        materialIcon("Clear") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }
    }

    val ClearAll: ImageVector by lazy {
        materialIcon("ClearAll") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.0f, 13.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(13.0f)
                close()
                moveTo(3.0f, 17.0f)
                horizontalLineTo(17.0f)
                verticalLineTo(15.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(7.0f, 7.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(21.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(7.0f)
                close()
            }
        }
    }

    val Code: ImageVector by lazy {
        materialIcon("Code") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.4f, 16.6f)
                lineTo(4.8f, 12.0f)
                lineTo(9.4f, 7.4f)
                lineTo(8.0f, 6.0f)
                lineTo(2.0f, 12.0f)
                lineTo(8.0f, 18.0f)
                lineTo(9.4f, 16.6f)
                close()
                moveTo(14.6f, 16.6f)
                lineTo(19.2f, 12.0f)
                lineTo(14.6f, 7.4f)
                lineTo(16.0f, 6.0f)
                lineTo(22.0f, 12.0f)
                lineTo(16.0f, 18.0f)
                lineTo(14.6f, 16.6f)
                close()
            }
        }
    }

    val Delete: ImageVector by lazy {
        materialIcon("Delete") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.0f, 19.0f)
                curveTo(6.0f, 20.1f, 6.9f, 21.0f, 8.0f, 21.0f)
                horizontalLineTo(16.0f)
                curveTo(17.1f, 21.0f, 18.0f, 20.1f, 18.0f, 19.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(19.0f, 4.0f)
                horizontalLineTo(15.5f)
                lineTo(14.5f, 3.0f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(4.0f)
                close()
            }
        }
    }

    val Edit: ImageVector by lazy {
        materialIcon("Edit") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.0f, 17.25f)
                verticalLineTo(21.0f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3.0f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }
    }

    val EditNote: ImageVector by lazy {
        materialIcon("EditNote") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.0f, 10.0f)
                horizontalLineTo(14.0f)
                verticalLineTo(12.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(3.0f, 6.0f)
                horizontalLineTo(14.0f)
                verticalLineTo(8.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(6.0f)
                close()
                moveTo(3.0f, 14.0f)
                horizontalLineTo(10.0f)
                verticalLineTo(16.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(18.01f, 12.87f)
                lineTo(18.72f, 12.16f)
                curveTo(19.11f, 11.77f, 19.74f, 11.77f, 20.13f, 12.16f)
                lineTo(20.84f, 12.87f)
                curveTo(21.23f, 13.26f, 21.23f, 13.89f, 20.84f, 14.28f)
                lineTo(20.13f, 14.99f)
                lineTo(18.01f, 12.87f)
                close()
                moveTo(17.3f, 13.58f)
                lineTo(12.0f, 18.88f)
                verticalLineTo(21.0f)
                horizontalLineTo(14.12f)
                lineTo(19.42f, 15.7f)
                lineTo(17.3f, 13.58f)
                close()
            }
        }
    }

    val Grass: ImageVector by lazy {
        materialIcon("Grass") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.0f, 20.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(18.0f)
                horizontalLineTo(7.75f)
                curveTo(7.11f, 15.1f, 4.98f, 12.73f, 2.0f, 12.08f)
                curveTo(5.63f, 11.35f, 8.63f, 13.55f, 9.76f, 17.0f)
                curveTo(9.49f, 13.0f, 7.62f, 9.39f, 4.35f, 7.04f)
                curveTo(8.56f, 8.15f, 11.58f, 11.67f, 12.0f, 16.0f)
                curveTo(12.42f, 11.67f, 15.44f, 8.15f, 19.65f, 7.04f)
                curveTo(16.38f, 9.39f, 14.51f, 13.0f, 14.24f, 17.0f)
                curveTo(15.37f, 13.55f, 18.37f, 11.35f, 22.0f, 12.08f)
                curveTo(19.02f, 12.73f, 16.89f, 15.1f, 16.25f, 18.0f)
                horizontalLineTo(22.0f)
                verticalLineTo(20.0f)
                horizontalLineTo(12.0f)
                close()
            }
        }
    }

    val History: ImageVector by lazy {
        materialIcon("History") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.0f, 3.0f)
                curveTo(8.03f, 3.0f, 4.0f, 7.03f, 4.0f, 12.0f)
                horizontalLineTo(1.0f)
                lineTo(4.89f, 15.89f)
                lineTo(4.96f, 16.03f)
                lineTo(9.0f, 12.0f)
                horizontalLineTo(6.0f)
                curveTo(6.0f, 8.13f, 9.13f, 5.0f, 13.0f, 5.0f)
                reflectiveCurveTo(20.0f, 8.13f, 20.0f, 12.0f)
                reflectiveCurveTo(16.87f, 19.0f, 13.0f, 19.0f)
                curveTo(11.07f, 19.0f, 9.32f, 18.21f, 8.06f, 16.94f)
                lineTo(6.64f, 18.36f)
                curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                curveTo(17.97f, 21.0f, 22.0f, 16.97f, 22.0f, 12.0f)
                reflectiveCurveTo(17.97f, 3.0f, 13.0f, 3.0f)
                close()
                moveTo(12.0f, 8.0f)
                verticalLineTo(13.0f)
                lineTo(16.28f, 15.54f)
                lineTo(17.0f, 14.33f)
                lineTo(13.5f, 12.25f)
                verticalLineTo(8.0f)
                horizontalLineTo(12.0f)
                close()
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        materialIcon("MoreVert") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.0f, 8.0f)
                curveTo(13.1f, 8.0f, 14.0f, 7.1f, 14.0f, 6.0f)
                reflectiveCurveTo(13.1f, 4.0f, 12.0f, 4.0f)
                reflectiveCurveTo(10.0f, 4.9f, 10.0f, 6.0f)
                reflectiveCurveTo(10.9f, 8.0f, 12.0f, 8.0f)
                close()
                moveTo(12.0f, 10.0f)
                curveTo(10.9f, 10.0f, 10.0f, 10.9f, 10.0f, 12.0f)
                reflectiveCurveTo(10.9f, 14.0f, 12.0f, 14.0f)
                reflectiveCurveTo(14.0f, 13.1f, 14.0f, 12.0f)
                reflectiveCurveTo(13.1f, 10.0f, 12.0f, 10.0f)
                close()
                moveTo(12.0f, 16.0f)
                curveTo(10.9f, 16.0f, 10.0f, 16.9f, 10.0f, 18.0f)
                reflectiveCurveTo(10.9f, 20.0f, 12.0f, 20.0f)
                reflectiveCurveTo(14.0f, 19.1f, 14.0f, 18.0f)
                reflectiveCurveTo(13.1f, 16.0f, 12.0f, 16.0f)
                close()
            }
        }
    }

    val PlayArrow: ImageVector by lazy {
        materialIcon("PlayArrow") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.0f, 5.0f)
                verticalLineTo(19.0f)
                lineTo(19.0f, 12.0f)
                close()
            }
        }
    }

    val Storage: ImageVector by lazy {
        materialIcon("Storage") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.0f, 20.0f)
                horizontalLineTo(22.0f)
                verticalLineTo(16.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(4.0f, 17.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(19.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(2.0f, 4.0f)
                verticalLineTo(8.0f)
                horizontalLineTo(22.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(2.0f)
                close()
                moveTo(6.0f, 7.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(7.0f)
                close()
                moveTo(2.0f, 14.0f)
                horizontalLineTo(22.0f)
                verticalLineTo(10.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(4.0f, 11.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(13.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(11.0f)
                close()
            }
        }
    }

    inline fun materialIcon(
        name: String,
        block: ImageVector.Builder.() -> ImageVector.Builder
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).block().build()
}