package edu.bluejack25_2.hwixel.ui.project.evaluation

sealed class PeerEvalUiState {
    object Idle : PeerEvalUiState()
    object Loading : PeerEvalUiState()
    object SubmitSuccess : PeerEvalUiState()
    object PeriodToggled : PeerEvalUiState()
    object PeriodCreated : PeerEvalUiState()
    data class Error(val message: String) : PeerEvalUiState()
}
