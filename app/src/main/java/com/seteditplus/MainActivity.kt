package com.seteditplus

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.seteditplus.data.ThemeMode
import com.seteditplus.ui.screens.MainScreen
import com.seteditplus.ui.screens.PermissionScreen
import com.seteditplus.ui.screens.SettingsDialog
import com.seteditplus.ui.theme.SetEditPlusTheme
import com.seteditplus.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val themeMode by viewModel.themeMode
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.OLED -> true
                ThemeMode.SYSTEM -> systemDark
            }
            
            val isOled = themeMode == ThemeMode.OLED

            SetEditPlusTheme(darkTheme = isDark, oledTheme = isOled) {
                val isPermissionGranted by viewModel.isPermissionGranted
                var showSettings by remember { mutableStateOf(false) }

                AnimatedContent(
                    targetState = isPermissionGranted,
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(400)
                        )).togetherWith(
                            fadeOut(tween(300))
                        )
                    },
                    label = "screenTransition"
                ) { granted ->
                    if (granted) {
                        MainScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { showSettings = true }
                        )
                    } else {
                        PermissionScreen(
                            viewModel = viewModel,
                            onPermissionGranted = { /* AnimatedContent handles transition */ }
                        )
                    }
                }
                
                if (showSettings) {
                    SettingsDialog(
                        viewModel = viewModel,
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }
}
