package com.hermes.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(log: String, onClear: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onClear) { Text("Clear log") }
            Button(onClick = onShare) { Text("Share log") }
        }
        Text("Hermes debug log (tag 'Hermes' in logcat). 'Share log' bundles debug + crash + logcat into one file you can send without a PC.", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(log, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}
