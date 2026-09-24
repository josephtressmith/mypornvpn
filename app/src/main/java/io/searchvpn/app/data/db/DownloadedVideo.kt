package io.searchvpn.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val sourceUrl: String,
    val siteName: String,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val format: String = "MP4", // MP4, WEBM, M4A
    val quality: String = "720p", // 1080p, 720p, 480p, 360p, Audio
    val durationMs: Long = 0L,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED", // DOWNLOADING, COMPLETED, FAILED
    val progress: Int = 100 // 0 to 100
)
