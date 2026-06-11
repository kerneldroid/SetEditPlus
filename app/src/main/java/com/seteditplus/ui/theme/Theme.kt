package com.seteditplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetEditPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    oledTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalColorScheme = if (oledTheme && darkTheme) {
        // Apply pure black background for OLED mode — override ALL surface tokens
        colorScheme.copy(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black,
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF101010),
            surfaceDim = androidx.compose.ui.graphics.Color.Black,
            surfaceBright = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            surfaceContainer = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
            surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF050505),
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF141414),
            surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF1C1C1C)
        )
    } else {
        colorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = finalColorScheme,
        content = content
    )
}
