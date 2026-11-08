package net.pakeng.koreksiljk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import net.pakeng.koreksiljk.data.Store
import net.pakeng.koreksiljk.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext).apply { load() }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F5C4D),
                    onPrimary = Color.White,
                    secondary = Color(0xFF13806B),
                    background = Color(0xFFF3F5F4),
                    surface = Color.White,
                ),
            ) { App(store) }
        }
    }
}
