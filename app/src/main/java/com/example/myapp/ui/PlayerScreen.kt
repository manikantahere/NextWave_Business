package com.example.myapp.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(sampleVideoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    var screenWidth by remember { mutableStateOf(1) }
    var overlayText by remember { mutableStateOf(null) }

    // Automatically hide on-screen feedback text after 1.5 seconds
    LaunchedEffect(overlayText) {
        if (overlayText != null) {
            delay(1500)
            overlayText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { screenWidth = it.width }
    ) {
        // Native PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent gesture overlay on top of player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < screenWidth / 2
                            val seekAmount = if (isLeft) -10_000L else 10_000L
                            val newPos = (exoPlayer.currentPosition + seekAmount).coerceIn(0L, exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                            overlayText = if (isLeft) "« 10s" else "10s »"
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val isLeft = change.position.x < screenWidth / 2
                        val sensitivity = 0.005f

                        if (isLeft) {
                            // Brightness Control (Left Side)
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                val newBrightness = (currentBrightness - (dragAmount * sensitivity)).coerceIn(0.01f, 1.0f)
                                lp.screenBrightness = newBrightness
                                window.attributes = lp
                                overlayText = "Brightness: ${(newBrightness * 100).toInt()}%"
                            }
                        } else {
                            // Volume Control (Right Side)
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val volChange = if (dragAmount < 0) 1 else -1
                            val newVol = (currentVol + volChange).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            overlayText = "Volume: ${(newVol * 100 / maxVol)}%"
                        }
                    }
                }
        )

        // Feedback Text Overlay (Shows when double tapping or dragging)
        AnimatedVisibility(
            visible = overlayText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            overlayText?.let { text ->
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
