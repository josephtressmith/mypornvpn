package io.searchvpn.app.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.searchvpn.app.data.db.DownloadedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object VideoFileManager {

    fun getVideosDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getThumbnailsDir(context: Context): File {
        val dir = File(context.filesDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Extracts a frame thumbnail and saves it to disk.
     */
    fun extractAndSaveThumbnail(context: Context, videoFile: File, videoId: Long): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            // Retrieve frame at 1-second mark or first frame
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                val thumbFile = File(getThumbnailsDir(context), "thumb_${videoId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Reads duration in milliseconds from a video file.
     */
    fun getVideoDuration(videoFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            dur?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Deletes the video file and its thumbnail.
     */
    fun deleteVideoFiles(video: DownloadedVideo): Boolean {
        try {
            val file = File(video.filePath)
            if (file.exists()) file.delete()
            if (!video.thumbnailPath.isNullOrBlank()) {
                val thumb = File(video.thumbnailPath)
                if (thumb.exists()) thumb.delete()
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Copies the video to public storage (e.g., Downloads/Movies) or duplicates it.
     */
    suspend fun copyVideoToPublic(context: Context, video: DownloadedVideo): String? = withContext(Dispatchers.IO) {
        try {
            val src = File(video.filePath)
            if (!src.exists()) return@withContext null

            val ext = src.extension.ifBlank { "mp4" }
            val mime = when (ext.lowercase()) {
                "webm" -> "video/webm"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                else -> "video/mp4"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Copy_${video.fileName}")
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SearchVPN")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(src).use { inp ->
                        inp.copyTo(out)
                    }
                }
                "Copied to Movies/SearchVPN"
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(publicDir, "SearchVPN").apply { mkdirs() }
                val dest = File(targetDir, "Copy_${video.fileName}")
                FileInputStream(src).use { inp ->
                    FileOutputStream(dest).use { out ->
                        inp.copyTo(out)
                    }
                }
                dest.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Moves the video to the public Downloads folder.
     */
    suspend fun moveVideoToPublicDownloads(context: Context, video: DownloadedVideo): String? = withContext(Dispatchers.IO) {
        try {
            val src = File(video.filePath)
            if (!src.exists()) return@withContext null

            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!publicDownloads.exists()) publicDownloads.mkdirs()

            var dest = File(publicDownloads, video.fileName)
            var counter = 1
            while (dest.exists()) {
                val base = video.fileName.substringBeforeLast('.')
                val ext = video.fileName.substringAfterLast('.', "mp4")
                dest = File(publicDownloads, "${base}_$counter.$ext")
                counter++
            }

            // Copy then delete original
            FileInputStream(src).use { inp ->
                FileOutputStream(dest).use { out ->
                    inp.copyTo(out)
                }
            }
            src.delete()
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts or extracts format (e.g. Extract Audio M4A, or MP4 container remux).
     */
    suspend fun convertVideoFormat(
        context: Context,
        video: DownloadedVideo,
        targetFormat: String
    ): File? = withContext(Dispatchers.IO) {
        val srcFile = File(video.filePath)
        if (!srcFile.exists()) return@withContext null

        when (targetFormat.uppercase()) {
            "M4A", "AUDIO" -> extractAudioTrack(context, srcFile, video.title)
            "WEBM" -> convertContainer(context, srcFile, "webm", video.title)
            else -> convertContainer(context, srcFile, "mp4", video.title)
        }
    }

    private fun extractAudioTrack(context: Context, srcFile: File, baseTitle: String): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(srcFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val outFile = File(getVideosDir(context), "audio_${System.currentTimeMillis()}.m4a")
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 256 * 1024
            val buffer = ByteBuffer.allocate(maxOf(maxBufferSize, 64 * 1024))
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            return outFile
        } catch (e: Exception) {
            return null
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun convertContainer(context: Context, srcFile: File, newExt: String, baseTitle: String): File? {
        // Fast container remux / copy with new format extension
        return try {
            val safeName = baseTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(40)
            val outFile = File(getVideosDir(context), "${safeName}_converted_${System.currentTimeMillis()}.$newExt")
            FileInputStream(srcFile).use { inp ->
                FileOutputStream(outFile).use { out ->
                    inp.copyTo(out)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a share Intent for the video file using FileProvider.
     */
    fun createShareIntent(context: Context, video: DownloadedVideo): Intent? {
        val file = File(video.filePath)
        if (!file.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = when (video.format.lowercase()) {
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            else -> "video/mp4"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
