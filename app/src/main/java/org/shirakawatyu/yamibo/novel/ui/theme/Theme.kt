package org.shirakawatyu.yamibo.novel.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = RedLight,
    secondary = YellowLightDark,
    tertiary = YellowLightLight,
    background = YellowLightDark,
    surface = YellowLightDark

    /* Other default colors to override
background = Color(0xFFFFFBFE),
surface = Color(0xFFFFFBFE),
onPrimary = Color.White,
onSecondary = Color.White,
onTertiary = Color.White,
onBackground = Color(0xFF1C1B1F),
onSurface = Color(0xFF1C1B1F),
*/
)

@Composable
fun _300文学Theme(
//        darkTheme: Boolean = isSystemInDarkTheme(),
    darkTheme: Boolean = false,    // Not support currently
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
//            window.statusBarColor = YamiboColors.onSurface.toArgb()
//            window.statusBarColor = Color.Transparent.toArgb()
//            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )

}

/**
 * 阅读器夜间模式
 */
private val ReaderNightColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),      // 强调色 (来自默认的 Purple80)
    background = Color(0xFF212121),    // 页面背景 (来自 ContentViewer)
    onBackground = Color(0xFFC8C8C8),  // 正文文字
    surface = Color(0xFF333333),      // 设置栏、对话框、抽屉背景
    onSurface = Color(0xFFE0E0E0),      // 设置栏、对话框、抽屉上的文字
    surfaceVariant = Color(0xFF333333),  // 设置栏 Surface 背景
    onSurfaceVariant = Color(0xFF9E9E9E), // 页脚文字 (来自 ContentViewer 的 757575，稍亮)
    outline = Color(0xFF757575)       // 分割线
)

/**
 * 阅读器浅色模式
 */
private val ReaderLightColorScheme = lightColorScheme(
    primary = RedLight,                // (来自 app 主题)
    secondary = YellowLightDark,       // (来自 app 主题)
    tertiary = YellowLightLight,       // (来自 app 主题)
    background = YellowLightDark,      // 页面背景 (来自 app 主题)
    onBackground = Color.Black,        // 正文文字 (来自 ContentViewer)
    surface = YellowLightDark,         // 设置栏、对话框、抽屉背景 (来自 app 主题)
    onSurface = Color.Black,           // 设置栏、对话框、抽屉上的文字
    surfaceVariant = Color(0xFFEEEEEE),  // 设置栏 Surface 背景
    onSurfaceVariant = Color.DarkGray, // 页脚文字 (来自 ContentViewer)
    outline = Color.Gray                 // 分割线
)

/**
 * 应用于阅读器页面的专用主题，
 * 可根据uiState.nightMode在ReaderLightColorScheme和ReaderNightColorScheme之间切换。
 */
@Composable
fun ReaderTheme(
    nightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (nightMode) {
        ReaderNightColorScheme
    } else {
        ReaderLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}