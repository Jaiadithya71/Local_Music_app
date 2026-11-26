package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentagent.vibestream.data.local.ListeningSync
import com.contentagent.vibestream.data.local.MoodRuntime
import com.contentagent.vibestream.ui.theme.*

@Composable
fun ListeningSyncDialog(onDismiss: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    val on = remember(tick) { ListeningSync.connected() }
    val status = remember(tick) { ListeningSync.status() }
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
                Text("Sends your play log (song, artist, seconds listened, skipped or finished, time of day) to your private store so Instinct can see why picks miss. No login. Pause any time.", color = TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { ListeningSync.setPaused(on); tick++ }) { Text(if (on) "Pause sync" else "Resume sync") }
        }
    )
}
