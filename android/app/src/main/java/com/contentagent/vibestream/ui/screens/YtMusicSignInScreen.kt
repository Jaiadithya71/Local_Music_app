package com.contentagent.vibestream.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.data.auth.GoogleDeviceAuth
import com.contentagent.vibestream.data.auth.YtMusicAuth
import com.contentagent.vibestream.ui.theme.AmberOrange
import com.contentagent.vibestream.ui.theme.DarkBackground
import com.contentagent.vibestream.ui.theme.DarkSurface
import com.contentagent.vibestream.ui.theme.TextPrimary
import com.contentagent.vibestream.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * YouTube Music sign-in via Google's OAuth device flow ("TVs and limited input
 * devices"). The app asks Google for a short code, the user enters it on
 * google.com/device in their own browser and approves, and the app receives
 * OAuth tokens. No WebView, no cookies, no password handling.
 *
 * The integration is unofficial, as the note on this screen and on the Library
 * row says.
 */
@Composable
fun YtMusicSignInScreen(onDone: () -> Unit) {
    var phase by remember { mutableStateOf(SignInPhase.Requesting) }
    var deviceCode by remember { mutableStateOf<GoogleDeviceAuth.DeviceCode?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var flowJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun startFlow() {
        flowJob?.cancel()
        phase = SignInPhase.Requesting
        errorText = null
        val job = scope.launch(Dispatchers.IO) {
            val dc = GoogleDeviceAuth.requestDeviceCode(YtMusicAuth.clientId)
            if (dc == null) {
                withContext(Dispatchers.Main) {
                    errorText = "Could not reach Google. Check your connection and try again."
                    phase = SignInPhase.Error
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                deviceCode = dc
                phase = SignInPhase.WaitingForApproval
            }
            var interval = dc.intervalSec.coerceIn(1, 60)
            while (isActive) {
                delay(interval * 1000L)
                when (val result = GoogleDeviceAuth.pollToken(YtMusicAuth.clientId, YtMusicAuth.clientSecret, dc)) {
                    is GoogleDeviceAuth.PollResult.Success -> {
                        YtMusicAuth.saveTokens(result.accessToken, result.refreshToken, result.expiresInSec)
                        withContext(Dispatchers.Main) { onDone() }
                        return@launch
                    }
                    GoogleDeviceAuth.PollResult.Pending -> Unit
                    GoogleDeviceAuth.PollResult.SlowDown -> interval += 5
                    is GoogleDeviceAuth.PollResult.Failed -> {
                        withContext(Dispatchers.Main) {
                            errorText = when (result.error) {
                                "expired_token" -> "The code expired. Try again for a fresh one."
                                "access_denied" -> "Sign-in was cancelled on Google's page."
                                else -> "Sign-in failed (${result.error}). Try again."
                            }
                            phase = SignInPhase.Error
                        }
                        return@launch
                    }
                }
            }
        }
        flowJob = job
    }

    LaunchedEffect(Unit) {
        if (!YtMusicAuth.hasSecureStorage()) {
            phase = SignInPhase.StorageUnavailable
        } else if (!YtMusicAuth.isConfigured()) {
            phase = SignInPhase.NotConfigured
        } else {
            startFlow()
        }
    }
    DisposableEffect(Unit) {
        onDispose { flowJob?.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
            Text("Sign in to YouTube Music", color = TextSecondary, fontSize = 15.sp)
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            when (phase) {
                SignInPhase.Requesting -> {
                    CircularProgressIndicator(color = AmberOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Asking Google for a sign-in code…", color = TextSecondary, fontSize = 14.sp)
                }
                SignInPhase.WaitingForApproval -> deviceCode?.let { dc ->
                    DeviceCodeCard(
                        userCode = dc.userCode,
                        verificationUrl = dc.verificationUrl,
                        expiresAtMs = dc.expiresAtMs,
                        onOpen = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUrl)))
                            }
                        }
                    )
                }
                SignInPhase.Error -> {
                    Text(errorText ?: "Something went wrong.", color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { startFlow() },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange)
                    ) { Text("Try again", color = DarkBackground) }
                }
                SignInPhase.StorageUnavailable -> {
                    Text(
                        "Encrypted sign-in storage is unavailable on this device. Restart VibeStream and try again.",
                        color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
                SignInPhase.NotConfigured -> {
                    Text(
                        "Sign-in is not set up in this build yet.",
                        color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            "Unofficial integration. You approve the sign-in on Google's own site; VibeStream stores only the sign-in token on this device, encrypted, and you can sign out anytime.",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

private enum class SignInPhase { Requesting, WaitingForApproval, Error, NotConfigured, StorageUnavailable }

/** The approval-code body of the device-flow sign-in, split out so tests can render it. */
@Composable
fun DeviceCodeCard(
    userCode: String,
    verificationUrl: String,
    expiresAtMs: Long,
    onOpen: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000L); nowMs = System.currentTimeMillis() }
    }
    val remainingMin = ((expiresAtMs - nowMs) / 60000L).coerceAtLeast(0)

    Text("1. Tap the button below - it opens Google's sign-in page.", color = TextPrimary, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(8.dp))
    Text("2. Enter this code and approve:", color = TextPrimary, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            userCode,
            color = AmberOrange,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("expires in about $remainingMin min", color = TextSecondary, fontSize = 11.sp)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("3. Come back here - this screen closes by itself.", color = TextPrimary, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onOpen,
        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange)
    ) { Text("Open Google sign-in", color = DarkBackground) }
    Spacer(modifier = Modifier.height(8.dp))
    Text(verificationUrl, color = TextSecondary, fontSize = 11.sp)
}
