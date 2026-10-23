package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.ui.theme.*
import kotlinx.coroutines.delay

/** Sleep timer picker: fixed durations, end-of-song, or off. */
@Composable
fun SleepTimerDialog(
    endAtMs: Long?,
    endOfTrack: Boolean,
    onPickMinutes: (Int) -> Unit,
    onPickEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAtMs) {
        while (endAtMs != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Sleep timer", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (endAtMs != null) {
                    val remaining = ((endAtMs - now) / 1000).coerceAtLeast(0)
                    Text(
                        "Stopping in ${remaining / 60}m ${remaining % 60}s - the volume fades out at the end",
                        color = AmberOrange, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                } else if (endOfTrack) {
                    Text("Will stop at the end of this song", color = AmberOrange, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                }
                for (m in listOf(5, 10, 15, 20, 30, 45, 60)) {
                    TextButton(onClick = { onPickMinutes(m) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$m minutes", color = TextPrimary, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = onPickEndOfTrack, modifier = Modifier.fillMaxWidth()) {
                    Text("End of current song", color = TextPrimary, modifier = Modifier.fillMaxWidth())
                }
                if (endAtMs != null || endOfTrack) {
                    TextButton(onClick = onCancelTimer, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn off", color = AmberOrange, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        }
    )
}
