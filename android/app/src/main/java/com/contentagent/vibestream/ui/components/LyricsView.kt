package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.data.lyrics.LyricsRepository
import com.contentagent.vibestream.ui.theme.*

/** Synced lyrics with the active line highlighted, plain-lyrics fallback. */
@Composable
fun LyricsView(
    loading: Boolean,
    lyrics: LyricsRepository.Lyrics?,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .padding(16.dp)
    ) {
        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = AmberOrange, strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Finding lyrics…", color = TextSecondary, fontSize = 13.sp)
                }
            }
            lyrics == null -> {
                Text("No lyrics found for this track.", color = TextTertiary, fontSize = 13.sp)
            }
            !lyrics.synced.isNullOrEmpty() -> {
                val lines = lyrics.synced!!
                val currentIndex = lines.indexOfLast { it.t <= positionMs }
                val listState = rememberLazyListState()
                LaunchedEffect(currentIndex) {
                    if (currentIndex > 2) listState.animateScrollToItem(currentIndex - 2)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                ) {
                    items(lines.size) { i ->
                        val active = i == currentIndex
                        Text(
                            text = lines[i].text.ifEmpty { "♪" },
                            color = if (active) AmberOrange else TextSecondary,
                            fontSize = if (active) 16.sp else 14.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )
                    }
                }
            }
            !lyrics.plain.isNullOrBlank() -> {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    item {
                        Text(
                            text = lyrics.plain!!,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
