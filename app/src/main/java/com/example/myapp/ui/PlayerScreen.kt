package com.example.myapp.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(videoUri: Uri, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // State variables
    var showRewindOverlay by remember { mutableStateOf(false) }
    var showForwardOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    var brightnessLevel by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness.let {
                if (it == null || it < 0f) 0.5f else it
            }
        )
    }
    var volumeLevel by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
        )
    }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            
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
            PlaybackCache.positionMap[videoUri.toString()] = exoPlayer.currentPosition
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent Touch Overlay split into Left (Brightness / Rewind) and Right (Volume / FastForward)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                coroutineScope.launch {
                                    showRewindOverlay = true
                                    delay(600)
                                    showRewindOverlay = false
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showBrightnessOverlay = true },
                            onDragEnd = {
                                coroutineScope.launch {
                                    delay(800)
                                    showBrightnessOverlay = false
                                }
                            },
                            onDragCancel = { showBrightnessOverlay = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                activity?.window?.attributes?.let { lp ->
                                    val delta = -dragAmount / 600f
                                    val newBrightness = (brightnessLevel + delta).coerceIn(0.05f, 1.0f)
                                    brightnessLevel = newBrightness
                                    lp.screenBrightness = newBrightness
                                    activity.window.attributes = lp
                                }
                            }
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                coroutineScope.launch {
                                    showForwardOverlay = true
                                    delay(600)
                                    showForwardOverlay = false
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showVolumeOverlay = true },
                            onDragEnd = {
                                coroutineScope.launch {
                                    delay(800)
                                    showVolumeOverlay = false
                                }
                            },
                            onDragCancel = { showVolumeOverlay = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / 400f
                                val newVolumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                volumeLevel = newVolumeLevel
                                val targetVol = (newVolumeLevel * maxVolume).toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            }
                        )
                    }
            )
        }

        // Top Control Bar (Back Button + Speed Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { showSpeedDialog = true },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback Speed",
                    tint = Color.White
                )
            }
        }

        // --- OVERLAY INDICATORS ---

        AnimatedVisibility(
            visible = showRewindOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        ) {
            SeekIndicator(icon = Icons.Default.FastRewind, text = "-10s")
        }

        AnimatedVisibility(
            visible = showForwardOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        ) {
            SeekIndicator(icon = Icons.Default.FastForward, text = "+10s")
        }

        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            LevelIndicator(icon = Icons.Default.Brightness7, value = brightnessLevel, label = "Brightness")
        }

        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            LevelIndicator(icon = Icons.Default.VolumeUp, value = volumeLevel, label = "Volume")
        }

        // --- SPEED SELECTION DIALOG ---
        if (showSpeedDialog) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Playback Speed", color = Color.White) },
                containerColor = Color(0xFF252525),
                text = {
                    Column {
                        speedOptions.forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentSpeed == speed),
                                    onClick = {
                                        currentSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF5722))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Cancel", color = Color(0xFFFF5722))
                    }
                }
            )
        }
    }
}

@Composable
private fun SeekIndicator(icon: ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), shape = CircleShape)
            .padding(20.dp)
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun LevelIndicator(icon: ImageVector, value: Float, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFFFF5722),
            trackColor = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
