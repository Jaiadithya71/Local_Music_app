package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.contentagent.vibestream.ui.theme.TextSecondary

@Composable
fun YouTubeAccountMenu(onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.AccountCircle, "YouTube Music account", tint = TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Sign out of YouTube Music") }, onClick = { expanded = false; onSignOut() })
        }
    }
}
