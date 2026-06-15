package edu.bluejack25_2.hwixel.ui.project.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.FileRepository

class FileRepoViewModelFactory(
    private val repository: FileRepository,
    private val projectId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FileRepoViewModel(repository, projectId) as T
}
