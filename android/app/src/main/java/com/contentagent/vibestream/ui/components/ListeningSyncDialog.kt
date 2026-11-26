package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.contentagent.vibestream.data.local.ListeningSync
import com.contentagent.vibestream.data.local.MoodRuntime
import com.contentagent.vibestream.ui.theme.*

@Composable
fun ListeningSyncDialog(onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val connected = remember(tick) { ListeningSync.connected() }
    val status = remember(tick) { ListeningSync.status() }
    val link = remember(tick) { ListeningSync.gistUrl() }
    val plays = remember(tick) { MoodRuntime.store()?.all()?.size ?: 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = { Text("Listening sync", color = TextPrimary) },
        text = {
            Column {
                Text("Plays logged on this phone: $plays", color = TextSecondary)
                Text(status, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (!connected) {
                    Text("Paste a GitHub token that only has the Gist permission. The play log goes to a secret gist in your own GitHub. Nothing is sent without it.", color = TextSecondary)
                    OutlinedTextField(
                        value = token, onValueChange = { token = it }, singleLine = true,
                        label = { Text("GitHub token") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (link != null) {
                    Text("Log link (send this to Instinct once):", color = TextSecondary)
                    SelectionContainer { Text(link, color = TextPrimary) }
                }
            }
        },
        confirmButton = {
            if (!connected) {
                TextButton(enabled = token.isNotBlank(), onClick = { ListeningSync.connect(token); token = ""; tick++ }) { Text("Connect") }
            } else {
                TextButton(onClick = { tick++; onDismiss() }) { Text("Done") }
            }
        },
        dismissButton = {
            if (connected) TextButton(onClick = { ListeningSync.disconnect(); tick++ }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
