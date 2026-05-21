package edu.bluejack252.hwixel.ui.project.files

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import edu.bluejack252.hwixel.MainDispatcherRule
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.repository.FileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileRepoViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeFileRepository
    private lateinit var viewModel: FileRepoViewModel
    private val observers = mutableListOf<Pair<LiveData<*>, Observer<Any>>>()

    @Before
    fun setUp() {
        repository = FakeFileRepository()
        viewModel = FileRepoViewModel(repository, PROJECT_ID)
        observe(viewModel.filteredFiles)
        observe(viewModel.versionHistory)
        observe(viewModel.uiState)
    }

    @After
    fun tearDown() {
        observers.forEach { (liveData, observer) -> liveData.removeObserver(observer) }
        observers.clear()
    }

    @Test
    fun typeFilteringShowsOnlySelectedTypeAndAllResetsFilter() {
        repository.files.value = listOf(
            file(id = "drive-1", type = "drive"),
            file(id = "github-1", type = "github"),
            file(id = "other-1", type = "other"),
            file(id = "github-version", type = "github", versionNotes = "v2")
        )

        viewModel.setTypeFilter("github")

        assertEquals(listOf("github-1"), viewModel.filteredFiles.value.orEmpty().map { it.id })

        viewModel.setTypeFilter(null)

        assertEquals(3, viewModel.filteredFiles.value.orEmpty().size)
    }

    @Test
    fun mainListExcludesFilesThatBelongToVersionHistory() {
        repository.files.value = listOf(
            file(id = "shared-link", versionNotes = ""),
            file(id = "versioned-link", versionNotes = "v2")
        )

        assertEquals(listOf("shared-link"), viewModel.filteredFiles.value.orEmpty().map { it.id })
        assertEquals(listOf("versioned-link"), viewModel.versionHistory.value.orEmpty().map { it.id })
    }

    @Test
    fun addAndRemoveCallRepositoryWithExpectedPayload() = runTest {
        viewModel.addFile(" Spec ", " https://example.com/spec.pdf ", "drive", " v2 ")
        advanceUntilIdle()

        val added = repository.addedFiles.single()
        assertEquals(PROJECT_ID, repository.addProjectIds.single())
        assertEquals("Spec", added.label)
        assertEquals("https://example.com/spec.pdf", added.url)
        assertEquals("drive", added.type)
        assertEquals("v2", added.versionNotes)
        assertTrue(added.createdAt > 0L)
        assertEquals(FileRepoUiState.AddSuccess, viewModel.uiState.value)

        viewModel.deleteFile("file-1")
        advanceUntilIdle()

        assertEquals(PROJECT_ID to "file-1", repository.deletedFiles.single())
        assertEquals(FileRepoUiState.DeleteSuccess, viewModel.uiState.value)
    }

    @Test
    fun deleteFailureEmitsErrorState() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException("permission denied"))

        viewModel.deleteFile("file-1")
        advanceUntilIdle()

        assertEquals(FileRepoUiState.Error("permission denied"), viewModel.uiState.value)
    }

    @Test
    fun versionNotesDerivationUsesNonEmptyNotesAndCreatedAtOrder() {
        repository.files.value = listOf(
            file(id = "plain", versionNotes = "", createdAt = 10L),
            file(id = "newer", versionNotes = "v2", createdAt = 30L),
            file(id = "older", versionNotes = "v1", createdAt = 20L)
        )

        assertEquals(listOf("older", "newer"), viewModel.versionHistory.value.orEmpty().map { it.id })
    }

    private fun <T> observe(liveData: LiveData<T>) {
        @Suppress("UNCHECKED_CAST")
        val observer = Observer<T> {} as Observer<Any>
        liveData.observeForever(observer as Observer<T>)
        observers.add(liveData to observer)
    }

    private fun file(
        id: String,
        type: String = "drive",
        versionNotes: String = "",
        createdAt: Long = 0L
    ) = FileLink(
        id = id,
        projectId = PROJECT_ID,
        label = id,
        url = "https://example.com/$id",
        type = type,
        versionNotes = versionNotes,
        createdAt = createdAt
    )

    private class FakeFileRepository : FileRepository {
        val files = MutableLiveData<List<FileLink>>(emptyList())
        val addProjectIds = mutableListOf<String>()
        val addedFiles = mutableListOf<FileLink>()
        val deletedFiles = mutableListOf<Pair<String, String>>()
        var addResult: Result<Unit> = Result.success(Unit)
        var deleteResult: Result<Unit> = Result.success(Unit)

        override fun observeFiles(projectId: String): LiveData<List<FileLink>> = files

        override suspend fun addFile(projectId: String, file: FileLink): Result<Unit> {
            addProjectIds.add(projectId)
            addedFiles.add(file)
            return addResult
        }

        override suspend fun deleteFile(projectId: String, fileId: String): Result<Unit> {
            deletedFiles.add(projectId to fileId)
            return deleteResult
        }
    }

    private companion object {
        const val PROJECT_ID = "project-1"
    }
}
