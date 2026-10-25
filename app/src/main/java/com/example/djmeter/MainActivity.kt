package com.example.djmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.djmeter.ui.screens.SonometerScreen
import com.example.djmeter.ui.screens.SplashScreen
import com.example.djmeter.ui.theme.DjMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge with transparent system bars; bars stay visible.
        enableEdgeToEdge()
        setContent {
            DjMeterTheme {
                var showSplash by remember { mutableStateOf(true) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (showSplash) {
                        SplashScreen(onSplashComplete = { showSplash = false })
                    } else {
                        SonometerScreen()
                    }
                }
            }
        }
    }
}
