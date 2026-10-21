package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.data.local.TasteProfile
import com.contentagent.vibestream.data.local.TasteStore
import com.contentagent.vibestream.ui.theme.*

private val languageOptions = listOf("Tamil", "Hindi", "Telugu", "Malayalam", "Kannada", "English")
private val moodOptions = listOf("Chill", "Workout", "Romance", "Devotional", "Party", "Philosophical", "Retro", "Lo-Fi")

/** First-open taste questions. Skippable; answers seed Your mood, Top picks and the Home shelves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(store: TasteStore, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val langs = remember { mutableStateListOf<String>() }
    val moods = remember { mutableStateListOf<String>() }
    var artists by remember { mutableStateOf("") }
    var musicOnly by remember { mutableStateOf(store.getMusicOnly()) }

    LaunchedEffect(Unit) {
        val p = store.load()
        langs.addAll(p.languages)
        moods.addAll(p.moods)
        artists = p.artists.joinToString(", ")
    }

    fun finish() {
        store.setMusicOnly(musicOnly)
        store.save(
            TasteProfile(
                languages = langs.toList(),
                moods = moods.toList(),
                artists = artists.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            )
        )
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Step ${step + 1} of 4", color = TextTertiary, fontSize = 13.sp)
            TextButton(onClick = { store.save(TasteProfile()); onDone() }) {
                Text("Skip", color = TextSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text("Which languages do you listen to?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in languageOptions) {
                        FilterChip(
                            selected = l in langs,
                            onClick = { if (l in langs) langs.remove(l) else langs.add(l) },
                            label = { Text(l) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary,
                                selectedContainerColor = AmberOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
            }
            1 -> {
                Text("Pick your moods", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in moodOptions) {
                        FilterChip(
                            selected = m in moods,
                            onClick = { if (m in moods) moods.remove(m) else moods.add(m) },
                            label = { Text(m) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary,
                                selectedContainerColor = AmberOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
            }
            2 -> {
                Text("What do you want to hear?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                listOf(
                    true to ("Music only" to "Songs. No podcasts, compilations or long videos."),
                    false to ("Music, podcasts & more" to "Everything on YouTube, including long-form.")
                ).forEach { (value, labelDesc) ->
                    val (label, desc) = labelDesc
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = if (musicOnly == value) DarkSurfaceElevated else DarkSurface,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (musicOnly == value) AmberOrange else DarkSurfaceBorder)
                        ),
                        onClick = { musicOnly = value }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(desc, color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
            else -> {
                Text("Favourite artists?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Comma separated. Optional.", color = TextTertiary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = artists,
                    onValueChange = { artists = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("A.R. Rahman, Ilaiyaraaja, Anirudh", color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberOrange,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Back", color = TextSecondary) }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Button(
                onClick = { if (step < 3) step++ else finish() },
                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange, contentColor = DarkBackground)
            ) {
                Text(if (step < 3) "Next" else "Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
