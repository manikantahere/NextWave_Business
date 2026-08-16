package com.example.myapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.myapp.ui.GalleryScreen
import com.example.myapp.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedVideoUri by remember { mutableStateOf(null) }

            if (selectedVideoUri == null) {
                GalleryScreen(
                    onVideoSelect = { video ->
                        selectedVideoUri = video.uri
                    }
                )
            } else {
                PlayerScreen(
                    videoUri = selectedVideoUri!!,
                    onBack = {
                        selectedVideoUri = null
                    }
                )
            }
        }
    }
}
