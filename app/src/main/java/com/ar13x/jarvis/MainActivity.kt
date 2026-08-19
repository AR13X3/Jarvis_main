package com.ar13x.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.navigation.JarvisApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            JarvisTheme(darkTheme = dark) {
                val colors = JarvisTheme.colors
                // The gradient runs under the status bar, so the bars stay
                // transparent and their icons are chosen against the *wash*,
                // not against the ground — light icons on the crimson in both
                // themes.
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (dark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                colors.surface.toArgb(),
                            )
                        },
                    )
                }
                JarvisApp()
            }
        }
    }
}
