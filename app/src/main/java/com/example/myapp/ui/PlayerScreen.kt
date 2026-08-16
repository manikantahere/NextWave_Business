package com.example.myapp.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(videoUri: Uri, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Seek overlay animation state
    var showRewindOverlay by remember { mutableStateOf(false) }
    var showForwardOverlay by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            
            // Auto-resume from saved position
            val savedPos = PlaybackCache.positionMap[videoUri.toString()] ?: 0L
            if (savedPos > 0) {
                seekTo(savedPos)
            }
            
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Save current playback position
            PlaybackCache.positionMap[videoUri.toString()] = exoPlayer.currentPosition
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            // Double-tap left side: Seek Back 10s
                            val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            
                            coroutineScope.launch {
                                showRewindOverlay = true
                                delay(600)
                                showRewindOverlay = false
                            }
                        } else {
                            // Double-tap right side: Seek Forward 10s
                            val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                            
                            coroutineScope.launch {
                                showForwardOverlay = true
                                delay(600)
                                showForwardOverlay = false
                            }
                        }
                    }
                )
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Double-Tap Rewind (-10s) Overlay
        AnimatedVisibility(
            visible = showRewindOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        ) {
            SeekIndicator(icon = Icons.Default.FastRewind, text = "-10s")
        }

        // Double-Tap Fast Forward (+10s) Overlay
        AnimatedVisibility(
            visible = showForwardOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        ) {
            SeekIndicator(icon = Icons.Default.FastForward, text = "+10s")
        }
    }
}

@Composable
private fun SeekIndicator(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
            .padding(20.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
