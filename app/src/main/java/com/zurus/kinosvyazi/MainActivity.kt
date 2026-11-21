package com.zurus.kinosvyazi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zurus.kinosvyazi.ui.navigation.AppNavHost
import com.zurus.kinosvyazi.ui.theme.KinoSvyaziTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KinoSvyaziTheme {
                AppNavHost()
            }
        }
    }
}
