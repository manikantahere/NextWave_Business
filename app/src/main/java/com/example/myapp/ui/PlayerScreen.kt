package com.example.myapp.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

// Simple in-memory playback position cache for auto-resume
object PlaybackCache {
    val positionMap = mutableMapOf()
}

@Composable
fun PlayerScreen(videoUri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // State toggles
    var isLocked by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf(null) }
    var screenWidth by remember { mutableIntStateOf(1) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()

            // Auto-resume position
            val savedPos = PlaybackCache.positionMap[videoUri.toString()] ?: 0L
            if (savedPos > 0) seekTo(savedPos)

            playWhenReady = true
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            PlaybackCache.positionMap[videoUri.toString()] = exoPlayer.currentPosition
            exoPlayer.release()
        }
    }

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
        // Player Engine
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = !isLocked
                    resizeMode = currentResizeMode
                }
            },
            update = { view ->
                view.resizeMode = currentResizeMode
                view.useController = !isLocked
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay (Disabled when locked)
        if (!isLocked) {
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
                                activity?.window?.let { window ->
                                    val lp = window.attributes
                                    val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                    val newBrightness = (currentBrightness - (dragAmount * sensitivity)).coerceIn(0.01f, 1.0f)
                                    lp.screenBrightness = newBrightness
                                    window.attributes = lp
                                    overlayText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                }
                            } else {
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
        }

        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            if (!isLocked) {
                Row {
                    // Aspect Ratio Toggle Button
                    IconButton(onClick = {
                        currentResizeMode = when (currentResizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        overlayText = when (currentResizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Aspect: Fill/Crop"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Aspect: Zoom"
                            else -> "Aspect: Fit 16:9"
                        }
                    }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }

                    // Playback Speed Button
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        currentSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        overlayText = "Speed: ${speed}x"
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Lock Screen Toggle Button
            IconButton(onClick = {
                isLocked = !isLocked
                overlayText = if (isLocked) "Controls Locked" else "Controls Unlocked"
            }) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock Controls",
                    tint = if (isLocked) Color.Red else Color.White
                )
            }
        }

        // Overlay Feedback Toast
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
                    fontSize = 20.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
