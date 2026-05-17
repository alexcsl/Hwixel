package edu.bluejack252.hwixel.ui.project.evaluation

import edu.bluejack252.hwixel.data.model.EvaluationSubmission

sealed class PeerEvalUiState {
    object Idle : PeerEvalUiState()
    object Loading : PeerEvalUiState()
    object SubmitSuccess : PeerEvalUiState()
    object PeriodToggled : PeerEvalUiState()
    object PeriodCreated : PeerEvalUiState()
    data class Error(val message: String) : PeerEvalUiState()
    data class ReceivedLoaded(val submissions: List<EvaluationSubmission>) : PeerEvalUiState()
}
