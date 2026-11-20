package com.contentagent.vibestream.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.contentagent.vibestream.data.auth.YtMusicAuth
import com.contentagent.vibestream.ui.theme.DarkBackground
import com.contentagent.vibestream.ui.theme.TextSecondary

/**
 * YouTube Music sign-in via WebView. The user signs in on Google's own pages;
 * VibeStream never sees the password. When a session cookie (SAPISID /
 * __Secure-3PAPISID) appears for music.youtube.com, it is saved encrypted
 * on-device and the screen closes.
 *
 * Note: this is an unofficial integration, shown to the user in the row below
 * the web content as well as before they open this screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicSignInScreen(onDone: () -> Unit) {
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
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // A desktop Chrome UA avoids Google's embedded-webview sign-in block
                    // ("disallowed_useragent") on accounts.google.com.
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val cookie = CookieManager.getInstance()
                                .getCookie("https://music.youtube.com") ?: return
                            if (cookie.contains("SAPISID=")) {
                                YtMusicAuth.saveCookie(cookie)
                                onDone()
                            }
                        }
                    }
                    loadUrl("https://music.youtube.com")
                }
            }
        )
        Text(
            "Unofficial integration. You sign in on Google's page; VibeStream stores only the session cookie on this device, encrypted, and you can sign out anytime.",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/** Wipes the stored session and the WebView cookies, so a fresh sign-in is clean. */
fun signOutOfYtMusic() {
    YtMusicAuth.signOut()
    try {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    } catch (e: Exception) {
        // No WebView created yet this process; nothing to clear.
    }
}
