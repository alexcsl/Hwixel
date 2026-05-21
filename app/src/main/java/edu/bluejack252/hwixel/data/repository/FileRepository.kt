package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.source.remote.FileRemoteSource

interface FileRepository {
    fun observeFiles(projectId: String): LiveData<List<FileLink>>
    suspend fun addFile(projectId: String, file: FileLink): Result<Unit>
    suspend fun deleteFile(projectId: String, fileId: String): Result<Unit>
}

class FileRepositoryImpl(
    private val firebaseSource: FileRemoteSource
) : FileRepository {
    override fun observeFiles(projectId: String) = firebaseSource.observeFiles(projectId)
    override suspend fun addFile(projectId: String, file: FileLink) = firebaseSource.addFile(projectId, file)
    override suspend fun deleteFile(projectId: String, fileId: String) = firebaseSource.deleteFile(projectId, fileId)
}
