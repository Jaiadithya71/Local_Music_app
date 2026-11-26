package com.contentagent.vibestream.data.local

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.Mood
import com.contentagent.vibestream.data.recommendation.MoodEngine
import com.contentagent.vibestream.data.recommendation.MoodRanker
import com.contentagent.vibestream.data.recommendation.MoodState
import java.util.Calendar
import kotlin.math.sqrt

data class MoodInfo(
    val plays: Int, val mood: Mood?, val stateLabel: String, val motion: String,
    val notFeeling: Boolean, val motionPermission: Boolean
)

/**
 * Coarse motion: still / moving / walking. The accelerometer needs no permission.
 * Walking is confirmed from the step counter only if he allowed ACTIVITY_RECOGNITION.
 * Without it the engine still works, using the accelerometer alone.
 */
object MotionSensor : SensorEventListener {
    @Volatile var state: String = "unknown"
        private set
    private val mags = ArrayList<Double>()
    private var sampling = false
    private var lastSteps = -1f
    private var lastStepsAt = 0L
    private var stepDelta = 0f
    private var stepWindowOk = false

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Samples for 4 seconds at song start; the result is used from the next play on. */
    fun sample(ctx: Context) {
        try {
            if (sampling) return
            val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val acc = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sampling = true
            synchronized(mags) { mags.clear() }
            stepDelta = 0f
            stepWindowOk = false
            m.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
            if (hasPermission(ctx)) {
                m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            Handler(Looper.getMainLooper()).postDelayed({ finish(m) }, 4000L)
        } catch (e: Exception) {
            sampling = false
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = e.values[0].toDouble(); val y = e.values[1].toDouble(); val z = e.values[2].toDouble()
            synchronized(mags) { mags.add(sqrt(x * x + y * y + z * z)) }
        } else if (e.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val now = System.currentTimeMillis()
            val v = e.values[0]
            if (lastSteps >= 0f && now - lastStepsAt in 20_000L..300_000L) {
                stepDelta = v - lastSteps
                stepWindowOk = true
            }
            if (lastSteps < 0f || now - lastStepsAt > 20_000L) { lastSteps = v; lastStepsAt = now }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun finish(m: SensorManager) {
        try { m.unregisterListener(this) } catch (e: Exception) { }
        sampling = false
        val copy = synchronized(mags) { ArrayList(mags) }
        if (stepWindowOk && stepDelta >= 15f) { state = "walking"; return }
        if (copy.size < 10) return
        val mean = copy.average()
        val sd = sqrt(copy.map { (it - mean) * (it - mean) }.average())
        state = when {
            sd < 0.12 -> "still"
            sd < 0.8 -> "moving"
            else -> "walking"
        }
    }
}

/** App-wide handle on the play log and the ranker. Safe to call before init: it does nothing. */
object MoodRuntime {
    @Volatile private var store: PlayEventStore? = null
    @Volatile private var appCtx: Context? = null

    fun init(context: Context) {
        if (store == null) {
            appCtx = context.applicationContext
            store = PlayEventStore(context.applicationContext)
            ListeningSync.init(context.applicationContext)
        }
    }

    fun store(): PlayEventStore? = store

    fun snapshot(): PlaySnapshot {
        val c = appCtx ?: return PlaySnapshot()
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return PlaySnapshot(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY,
            route = route(c), charging = charging(c), motion = MotionSensor.state
        )
    }

    private fun route(ctx: Context): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        when {
            types.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } -> "bluetooth"
            types.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_WIRED_HEADSET || it == AudioDeviceInfo.TYPE_USB_HEADSET } -> "wired"
            else -> "speaker"
        }
    } catch (e: Exception) { "speaker" }

    private fun charging(ctx: Context): Boolean = try {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    } catch (e: Exception) { false }

    fun currentState(): MoodState {
        val s = snapshot()
        return MoodState(MoodEngine.bucket(s.hour), s.route, s.motion)
    }

    fun newTracker(isLiked: (String) -> Boolean): PlayEventTracker = PlayEventTracker(
        sink = { e -> store?.append(e); ListeningSync.schedule() },
        snapshot = { snapshot() },
        isLiked = isLiked
    )

    /** Called when a song starts: refreshes motion for the next play. */
    fun onSongStart() { appCtx?.let { MotionSensor.sample(it) } }

    fun ranker(now: Long = System.currentTimeMillis()): MoodRanker? {
        val s = store ?: return null
        val events = s.all()
        if (events.size < MoodEngine.MIN_PLAYS) return null
        val state = currentState()
        return MoodRanker(events, now, state, MoodEngine.currentMood(events, now, state, s.overrides()))
    }

    fun rankFn(): ((List<Song>) -> List<Song>)? {
        val r = ranker() ?: return null
        return { list -> r.rank(list) }
    }

    fun info(now: Long = System.currentTimeMillis()): MoodInfo {
        val s = store
        val c = appCtx
        val events = s?.all().orEmpty()
        val state = currentState()
        val mood = if (s == null) null else MoodEngine.currentMood(events, now, state, s.overrides())
        return MoodInfo(
            plays = events.size, mood = mood, stateLabel = state.label, motion = state.motion,
            notFeeling = MoodEngine.notFeelingThis(events, now),
            motionPermission = c != null && MotionSensor.hasPermission(c)
        )
    }

    fun setMood(mood: Mood) { store?.setOverride(currentState().key, mood.name) }
}
