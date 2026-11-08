package fr.ecole2recte.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun averageDigits(): Int {
    val digits by rememberAppContainer().settings.averageDecimals.collectAsStateWithLifecycle()
    return digits
}
