package com.minova.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minova.cinema.presentation.CinemaViewModel
import com.minova.cinema.ui.MinovaCinemaApp
import com.minova.cinema.ui.theme.MinovaCinemaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CinemaViewModel by viewModels { CinemaViewModel.Factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MinovaCinemaTheme {
                MinovaCinemaApp(viewModel)
            }
        }
    }
}
