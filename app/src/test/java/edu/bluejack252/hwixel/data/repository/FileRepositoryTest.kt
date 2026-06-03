package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.source.remote.FileRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRepositoryTest {
    private val source = FakeFileSource()
    private val repository = FileRepositoryImpl(source)

    @Test
    fun addFileDelegatesToSource() = runTest {
        val file = FileLink(id = "file-1", projectId = "proj-1", label = "Design Doc", url = "https://example.com/doc")
        val result = repository.addFile("proj-1", file)
        assertTrue(result.isSuccess)
        assertEquals(file, source.lastAddedFile)
    }

    @Test
    fun deleteFileDelegatesToSource() = runTest {
        val result = repository.deleteFile("proj-1", "file-1")
        assertTrue(result.isSuccess)
        assertEquals("proj-1", source.lastDeleteProjectId)
        assertEquals("file-1", source.lastDeleteFileId)
    }

    @Test
    fun addFileFailurePropagates() = runTest {
        source.shouldFail = true
        val result = repository.addFile("proj-1", FileLink(id = "f"))
        assertTrue(result.isFailure)
    }

    private class FakeFileSource : FileRemoteSource {
        var lastAddedFile: FileLink? = null
        var lastDeleteProjectId: String? = null
        var lastDeleteFileId: String? = null
        var shouldFail = false

        override fun observeFiles(projectId: String): LiveData<List<FileLink>> = MutableLiveData(emptyList())

        override suspend fun addFile(projectId: String, file: FileLink): Result<Unit> {
            if (shouldFail) return Result.failure(RuntimeException("Source failure"))
            lastAddedFile = file
            return Result.success(Unit)
        }

        override suspend fun deleteFile(projectId: String, fileId: String): Result<Unit> {
            lastDeleteProjectId = projectId
            lastDeleteFileId = fileId
            return Result.success(Unit)
        }
    }
}
