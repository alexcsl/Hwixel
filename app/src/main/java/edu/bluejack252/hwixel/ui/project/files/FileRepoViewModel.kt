package edu.bluejack252.hwixel.ui.project.files

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.repository.FileRepository
import kotlinx.coroutines.launch
import java.net.URI

class FileRepoViewModel(
    private val repository: FileRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<FileRepoUiState>(FileRepoUiState.Idle)
    val uiState: LiveData<FileRepoUiState> = _uiState

    val files: LiveData<List<FileLink>> = repository.observeFiles(projectId)

    private val selectedType = MutableLiveData<String?>(null)
    private val _filteredFiles = MediatorLiveData<List<FileLink>>()
    val filteredFiles: LiveData<List<FileLink>> = _filteredFiles

    val versionHistory: LiveData<List<FileLink>> = files.map { list ->
        list.filter { it.versionNotes.isNotBlank() }
            .sortedWith(compareBy<FileLink> { it.createdAt.takeIf { createdAt -> createdAt > 0L } ?: Long.MAX_VALUE }.thenBy { it.id })
    }

    init {
        _filteredFiles.value = emptyList()
        _filteredFiles.addSource(files) { publishFilteredFiles() }
        _filteredFiles.addSource(selectedType) { publishFilteredFiles() }
    }

    private fun publishFilteredFiles() {
        val type = selectedType.value
        val list = files.value.orEmpty().filter { it.versionNotes.isBlank() }
        _filteredFiles.value = if (type.isNullOrBlank()) {
            list
        } else {
            list.filter { it.type.equals(type, ignoreCase = true) }
        }
    }

    fun addFile(label: String, url: String, type: String, versionNotes: String) {
        if (label.isBlank() || url.isBlank()) {
            _uiState.value = FileRepoUiState.Error("empty_fields")
            return
        }
        val parsedUrl = runCatching { URI(url.trim()) }.getOrNull()
        if (parsedUrl?.scheme !in setOf("http", "https")) {
            _uiState.value = FileRepoUiState.Error("invalid_url")
            return
        }
        _uiState.value = FileRepoUiState.Loading
        viewModelScope.launch {
            val file = FileLink(
                projectId = projectId,
                label = label.trim(),
                url = url.trim(),
                type = type,
                versionNotes = versionNotes.trim(),
                createdAt = System.currentTimeMillis()
            )
            val result = repository.addFile(projectId, file)
            _uiState.value = if (result.isSuccess) {
                FileRepoUiState.AddSuccess
            } else {
                FileRepoUiState.Error(result.exceptionOrNull()?.message ?: "")
            }
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            val result = repository.deleteFile(projectId, fileId)
            if (result.isSuccess) {
                _uiState.value = FileRepoUiState.DeleteSuccess
            } else {
                _uiState.value = FileRepoUiState.Error(result.exceptionOrNull()?.message ?: "")
            }
        }
    }

    fun setTypeFilter(type: String?) {
        selectedType.value = type?.takeIf { it.isNotBlank() }
    }

    fun resetState() {
        _uiState.value = FileRepoUiState.Idle
    }
}
