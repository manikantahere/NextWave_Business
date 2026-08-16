package com.example.myapp.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long
)

fun fetchLocalVideos(context: Context): List {
    val videos = mutableListOf()
    val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE
    )

    val cursor = context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )

    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val name = it.getString(nameColumn) ?: "Unknown"
            val duration = it.getLong(durationColumn)
            val size = it.getLong(sizeColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            videos.add(VideoItem(id, contentUri, name, duration, size))
        }
    }
    return videos
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format("%02d:%02d", remainingMinutes, seconds)
    }
}
