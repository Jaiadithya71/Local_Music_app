package com.contentagent.vibestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.local.DownloadManager
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.local.OfflineStorage
import com.contentagent.vibestream.player.MusicPlayerController
import com.contentagent.vibestream.ui.screens.HomeScreen
import com.contentagent.vibestream.ui.theme.DarkBackground
import com.contentagent.vibestream.ui.theme.VibeStreamTheme

/**
 * Main Activity for VibeStream:
 * Completely autonomous, on-device YouTube music player and offline downloader.
 */
class MainActivity : ComponentActivity() {

    private lateinit var extractor: YouTubeExtractor
    private lateinit var offlineStorage: OfflineStorage
    private lateinit var library: LibraryStore
    private lateinit var downloadManager: DownloadManager
    private lateinit var playerController: MusicPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize on-device components (zero external server required)
        extractor = YouTubeExtractor()
        offlineStorage = OfflineStorage(applicationContext)
        library = LibraryStore(applicationContext)
        downloadManager = DownloadManager(applicationContext, extractor, offlineStorage)
        playerController = MusicPlayerController(applicationContext, extractor)

        setContent {
            VibeStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    HomeScreen(
                        extractor = extractor,
                        offlineStorage = offlineStorage,
                        downloadManager = downloadManager,
                        playerController = playerController,
                        library = library
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.release()
    }
}
