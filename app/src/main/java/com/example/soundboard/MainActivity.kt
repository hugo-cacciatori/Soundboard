package com.example.soundboard

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.soundboard.ui.navigation.SoundboardNavHost
import com.example.soundboard.ui.theme.SoundboardTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as SoundboardApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only, so always use light (white) system bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SoundboardTheme {
                SoundboardNavHost(container)
            }
        }
    }

    // The mixer thread only runs while the app is visible.
    override fun onStart() {
        super.onStart()
        container.mixerEngine.start()
    }

    override fun onStop() {
        container.mixerEngine.release()
        super.onStop()
    }
}
