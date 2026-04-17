package org.pihole.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.pihole.android.navigation.AppNavHost
import org.pihole.android.ui.theme.PiholeTheme

/**
 * Debug / instrumented-test host: same UI as [MainActivity] without launcher intent
 * noise (see connected tests on some OEM ROMs).
 */
class ComposeHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PiholeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
