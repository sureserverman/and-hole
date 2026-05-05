package org.pihole.android.ui.theme

import androidx.compose.runtime.Composable
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * Compatibility wrapper retained for tests and any other callers; delegates to [AhTheme].
 */
@Composable
fun PiholeTheme(content: @Composable () -> Unit) {
    AhTheme(content = content)
}
