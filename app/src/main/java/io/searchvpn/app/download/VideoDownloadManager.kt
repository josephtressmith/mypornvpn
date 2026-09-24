package io.searchvpn.app.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.searchvpn.app.data.db.AppDatabase
import io.searchvpn.app.data.db.DownloadedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object VideoDownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueueDownload(
        context: Context,
        title: String,
        sourceUrl: String,
        streamUrl: String,
        format: String,
        quality: String,
        siteName: String
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val db = AppDatabase.getInstance(appContext)
            val dao = db.videoDao()

            val cleanTitle = title.ifBlank { "Video_${System.currentTimeMillis()}" }
            val ext = format.lowercase().ifBlank { "mp4" }
            val safeFileName = cleanTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(50) +
                    "_${quality}_${System.currentTimeMillis()}.$ext"

            val targetFile = File(VideoFileManager.getVideosDir(appContext), safeFileName)

            val initialVideo = DownloadedVideo(
                title = cleanTitle,
                sourceUrl = sourceUrl,
                siteName = siteName,
                filePath = targetFile.absolutePath,
                fileName = safeFileName,
                fileSizeBytes = 0L,
                format = format.uppercase(),
                quality = quality,
                durationMs = 0L,
                thumbnailPath = null,
                createdAt = System.currentTimeMillis(),
                status = "DOWNLOADING",
                progress = 0
            )

            val videoId = dao.insertVideo(initialVideo)

            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "Downloading: $cleanTitle ($quality $format)", Toast.LENGTH_SHORT).show()
            }

            try {
                downloadFile(
                    streamUrl = streamUrl,
                    referer = sourceUrl,
                    targetFile = targetFile,
                    onProgress = { percent ->
                        scope.launch {
                            dao.updateProgress(videoId, percent, "DOWNLOADING")
                        }
                    }
                )

                // Download completed: post-processing
                val fileSize = targetFile.length()
                val duration = VideoFileManager.getVideoDuration(targetFile)
                val thumbPath = VideoFileManager.extractAndSaveThumbnail(appContext, targetFile, videoId)

                val completedVideo = initialVideo.copy(
                    id = videoId,
                    fileSizeBytes = fileSize,
                    durationMs = duration,
                    thumbnailPath = thumbPath,
                    status = "COMPLETED",
                    progress = 100
                )
                dao.updateVideo(completedVideo)

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Download Complete: $cleanTitle", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                dao.updateProgress(videoId, 0, "FAILED")
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadFile(
        streamUrl: String,
        referer: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) {
        val url = URL(streamUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            if (referer.isNotEmpty()) {
                setRequestProperty("Referer", referer)
            }
        }

        conn.connect()
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw RuntimeException("HTTP $responseCode: ${conn.responseMessage}")
        }

        val totalBytes = conn.contentLengthLong
        var downloadedBytes = 0L
        var lastPercent = 0

        conn.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                        if (percent >= lastPercent + 5 || percent == 100) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }
        conn.disconnect()
    }
}
