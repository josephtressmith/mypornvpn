package io.searchvpn.app.data.db

import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {

    val allVideos: Flow<List<DownloadedVideo>> = videoDao.getAllVideos()

    suspend fun getVideoById(id: Long): DownloadedVideo? = videoDao.getVideoById(id)

    suspend fun insertVideo(video: DownloadedVideo): Long = videoDao.insertVideo(video)

    suspend fun updateVideo(video: DownloadedVideo) = videoDao.updateVideo(video)

    suspend fun deleteVideo(video: DownloadedVideo) = videoDao.deleteVideo(video)

    suspend fun deleteById(id: Long) = videoDao.deleteById(id)

    suspend fun updateProgress(id: Long, progress: Int, status: String) =
        videoDao.updateProgress(id, progress, status)

    suspend fun renameVideo(id: Long, newTitle: String, newFileName: String, newPath: String) =
        videoDao.renameVideo(id, newTitle, newFileName, newPath)
}
