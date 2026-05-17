package edu.bluejack252.hwixel.ui.project.files

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.FileLink
import edu.bluejack252.hwixel.data.repository.FileRepository
import kotlinx.coroutines.launch

class FileRepoViewModel(
    private val repository: FileRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<FileRepoUiState>(FileRepoUiState.Idle)
    val uiState: LiveData<FileRepoUiState> = _uiState

    val files: LiveData<List<FileLink>> = repository.observeFiles(projectId)

    val versionHistory: LiveData<List<FileLink>> = Transformations.map(files) { list ->
        list.filter { it.versionNotes.isNotBlank() }.sortedBy { it.id }
    }

    fun addFile(label: String, url: String, type: String, versionNotes: String) {
        if (label.isBlank() || url.isBlank()) {
            _uiState.value = FileRepoUiState.Error("empty_fields")
            return
        }
        _uiState.value = FileRepoUiState.Loading
        viewModelScope.launch {
            val file = FileLink(
                projectId = projectId,
                label = label.trim(),
                url = url.trim(),
                type = type,
                versionNotes = versionNotes.trim()
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
            }
        }
    }

    fun resetState() {
        _uiState.value = FileRepoUiState.Idle
    }
}
