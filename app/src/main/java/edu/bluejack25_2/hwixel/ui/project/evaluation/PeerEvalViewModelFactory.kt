package edu.bluejack25_2.hwixel.ui.project.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.bluejack25_2.hwixel.data.repository.EvalRepository

class PeerEvalViewModelFactory(
    private val repository: EvalRepository,
    private val projectId: String,
    private val currentUserId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeerEvalViewModel::class.java)) {
            return PeerEvalViewModel(repository, projectId, currentUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
