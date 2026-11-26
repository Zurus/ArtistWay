package com.zurus.cinethread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zurus.cinethread.ui.navigation.AppNavHost
import com.zurus.cinethread.ui.theme.CineThreadTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CineThreadTheme {
                AppNavHost()
            }
        }
    }
}
