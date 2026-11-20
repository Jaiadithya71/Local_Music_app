package com.contentagent.vibestream.player

import kotlinx.coroutines.*

/** Timer logic independent of MediaController so expiry/cancel can be tested. */
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tick: suspend () -> Unit = { delay(250) },
    private val onDeadline: (Long?) -> Unit,
    private val onVolume: (Float) -> Unit,
    private val onStop: () -> Unit
) {
    private var job: Job? = null
    fun start(minutes: Int) {
        require(minutes > 0)
        cancel()
        val deadline = now() + minutes * 60_000L
        onDeadline(deadline)
        job = scope.launch {
            while (isActive) {
                val remaining = deadline - now()
                if (remaining <= 0) break
                if (remaining <= 15_000L) onVolume((remaining / 15_000f).coerceIn(0f, 1f))
                tick()
            }
            onStop()
            onVolume(1f)
            onDeadline(null)
        }
    }
    fun cancel() {
        job?.cancel(); job = null
        onDeadline(null); onVolume(1f)
    }
}
