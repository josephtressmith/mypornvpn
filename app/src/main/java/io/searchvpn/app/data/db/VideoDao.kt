package io.searchvpn.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM downloaded_videos ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<DownloadedVideo>>

    @Query("SELECT * FROM downloaded_videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): DownloadedVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: DownloadedVideo): Long

    @Update
    suspend fun updateVideo(video: DownloadedVideo)

    @Delete
    suspend fun deleteVideo(video: DownloadedVideo)

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE downloaded_videos SET progress = :progress, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, status: String)

    @Query("UPDATE downloaded_videos SET title = :newTitle, fileName = :newFileName, filePath = :newPath WHERE id = :id")
    suspend fun renameVideo(id: Long, newTitle: String, newFileName: String, newPath: String)
}
