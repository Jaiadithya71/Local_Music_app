package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.player.EqualizerManager
import com.contentagent.vibestream.ui.theme.*

/** System equalizer: enable switch, preset chips, per-band sliders. */
@Composable
fun EqualizerDialog(eq: EqualizerManager, onDismiss: () -> Unit) {
    val enabled by eq.enabled.collectAsState()
    val levels by eq.bandLevels.collectAsState()
    val available by eq.available.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Equalizer", color = TextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { eq.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AmberOrange)
                )
            }
        },
        text = {
            Column {
                when {
                    !available -> Text(
                        "The equalizer is not available on this device.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    levels.isEmpty() -> Text(
                        "Start playing a song, then reopen this dialog.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    else -> {
                        val presets = eq.presets()
                        if (presets.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEachIndexed { i, name ->
                                    FilterChip(
                                        selected = eq.currentPresetIndex() == i,
                                        onClick = { eq.usePreset(i) },
                                        label = { Text(name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = DarkSurfaceElevated,
                                            labelColor = TextSecondary,
                                            selectedContainerColor = AmberOrange,
                                            selectedLabelColor = DarkBackground
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        val range = eq.bandLevelRange
                        if (range != null) {
                            levels.forEachIndexed { band, lvl ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        eq.bandLabel(band), color = TextSecondary,
                                        fontSize = 11.sp, modifier = Modifier.width(52.dp)
                                    )
                                    Slider(
                                        value = lvl.toFloat(),
                                        onValueChange = { eq.setBandLevel(band, it.toInt().toShort()) },
                                        valueRange = range.first.toFloat()..range.second.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AmberOrange,
                                            activeTrackColor = AmberOrange,
                                            inactiveTrackColor = DarkSurfaceBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${lvl / 100}dB", color = TextTertiary, fontSize = 11.sp,
                                        textAlign = TextAlign.End, modifier = Modifier.width(36.dp)
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bass", color = TextTertiary, fontSize = 11.sp)
                                Text("Treble", color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = AmberOrange) }
        }
    )
}
