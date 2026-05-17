package edu.bluejack252.hwixel.ui.project.files

sealed class FileRepoUiState {
    object Idle : FileRepoUiState()
    object Loading : FileRepoUiState()
    object AddSuccess : FileRepoUiState()
    object DeleteSuccess : FileRepoUiState()
    data class Error(val message: String) : FileRepoUiState()
}
