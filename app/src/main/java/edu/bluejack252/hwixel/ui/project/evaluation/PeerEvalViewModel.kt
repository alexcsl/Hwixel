package edu.bluejack252.hwixel.ui.project.evaluation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bluejack252.hwixel.data.model.EvaluationSubmission
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.repository.EvalRepository
import edu.bluejack252.hwixel.util.constants.Constants
import kotlinx.coroutines.launch
import kotlin.math.round

class PeerEvalViewModel(
    private val repository: EvalRepository,
    val projectId: String,
    val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<PeerEvalUiState>(PeerEvalUiState.Idle)
    val uiState: LiveData<PeerEvalUiState> = _uiState

    private val _periods = MutableLiveData<List<String>>(emptyList())
    val periods: LiveData<List<String>> = _periods

    private val _isPeriodOpen = MutableLiveData<Boolean>(false)
    val isPeriodOpen: LiveData<Boolean> = _isPeriodOpen

    private val _submittedByMe = MutableLiveData<Map<String, EvaluationSubmission>>(emptyMap())
    val submittedByMe: LiveData<Map<String, EvaluationSubmission>> = _submittedByMe

    private val _receivedEvals = MutableLiveData<List<EvaluationSubmission>>(emptyList())
    val receivedEvals: LiveData<List<EvaluationSubmission>> = _receivedEvals

    /** Set by the hosting Fragment after loading project members. */
    var projectMembers: List<ProjectMember> = emptyList()
    var memberNames: Map<String, String> = emptyMap()
    var currentUserRole: String = ""

    /** Tracks which period is currently active (most-recent). */
    var activePeriodId: String = ""
        private set

    private val periodsMediator = MediatorLiveData<List<String>>()
    private val periodsSource = repository.observePeriods(projectId)
    private var periodOpenSource: LiveData<Boolean>? = null
    private var submittedSource: LiveData<Map<String, EvaluationSubmission>>? = null
    private var receivedSource: LiveData<List<EvaluationSubmission>>? = null

    init {
        periodsMediator.addSource(periodsSource) { ids ->
            _periods.value = ids
            val latest = ids.lastOrNull()
            if (latest != null && latest != activePeriodId) {
                activePeriodId = latest
                subscribeToPeriod(latest)
            }
        }
    }

    private fun subscribeToPeriod(periodId: String) {
        periodOpenSource?.let { periodsMediator.removeSource(it) }
        submittedSource?.let { periodsMediator.removeSource(it) }
        receivedSource?.let { periodsMediator.removeSource(it) }

        val openSrc = repository.observePeriodOpen(projectId, periodId)
        periodOpenSource = openSrc
        periodsMediator.addSource(openSrc) { open -> _isPeriodOpen.value = open }

        val submittedSrc = repository.observeSubmittedByMe(projectId, periodId, currentUserId)
        submittedSource = submittedSrc
        periodsMediator.addSource(submittedSrc) { map -> _submittedByMe.value = map }

        val receivedSrc = repository.observeReceivedEvals(projectId, periodId, currentUserId)
        receivedSource = receivedSrc
        periodsMediator.addSource(receivedSrc) { list -> _receivedEvals.value = list }
    }

    fun createPeriod() {
        viewModelScope.launch {
            _uiState.value = PeerEvalUiState.Loading
            repository.createPeriod(projectId)
                .onSuccess { _uiState.value = PeerEvalUiState.PeriodCreated }
                .onFailure { _uiState.value = PeerEvalUiState.Error(it.message ?: "") }
        }
    }

    fun togglePeriodOpen() {
        if (currentUserRole != Constants.ROLE_TEAM_LEAD) {
            _uiState.value = PeerEvalUiState.Error("lead_only")
            return
        }
        val currentlyOpen = _isPeriodOpen.value ?: false
        viewModelScope.launch {
            _uiState.value = PeerEvalUiState.Loading
            repository.setPeriodOpen(projectId, activePeriodId, !currentlyOpen)
                .onSuccess { _uiState.value = PeerEvalUiState.PeriodToggled }
                .onFailure { _uiState.value = PeerEvalUiState.Error(it.message ?: "") }
        }
    }

    fun submitEvaluation(
        evaluateeId: String,
        communication: Int,
        quality: Int,
        reliability: Int,
        effort: Int,
        feedback: String
    ) {
        if (!(_isPeriodOpen.value ?: false)) {
            _uiState.value = PeerEvalUiState.Error("period_closed")
            return
        }
        if (evaluateeId == currentUserId) {
            _uiState.value = PeerEvalUiState.Error("self_eval")
            return
        }
        if (feedback.isBlank()) {
            _uiState.value = PeerEvalUiState.Error("empty_feedback")
            return
        }

        val submission = EvaluationSubmission(
            id = "$currentUserId-$evaluateeId",
            projectId = projectId,
            periodId = activePeriodId,
            evaluatorId = currentUserId,
            evaluateeId = evaluateeId,
            communication = communication,
            quality = quality,
            reliability = reliability,
            effort = effort,
            feedback = feedback
        )

        viewModelScope.launch {
            _uiState.value = PeerEvalUiState.Loading
            repository.submitEvaluation(submission)
                .onSuccess {
                    recomputeAverageRating(evaluateeId)
                    _uiState.value = PeerEvalUiState.SubmitSuccess
                }
                .onFailure { _uiState.value = PeerEvalUiState.Error(it.message ?: "") }
        }
    }

    private suspend fun recomputeAverageRating(evaluateeId: String) {
        repository.fetchAllReceivedSubmissions(projectId, evaluateeId)
            .onSuccess { submissions ->
                if (submissions.isNotEmpty()) {
                    val rawAvg = submissions.map { sub ->
                        (sub.communication + sub.quality + sub.reliability + sub.effort) / 4f
                    }.average().toFloat()
                    val avg = round(rawAvg * 10f) / 10f
                    repository.updateAveragePeerRating(evaluateeId, avg)
                }
            }
    }

    fun computeMyAverageReceived(): Float {
        val evals = _receivedEvals.value ?: return 0f
        if (evals.isEmpty()) return 0f
        return evals.map { (it.communication + it.quality + it.reliability + it.effort) / 4f }.average().toFloat()
    }

    fun hasAlreadySubmittedFor(evaluateeId: String): Boolean =
        _submittedByMe.value?.containsKey(evaluateeId) == true

    override fun onCleared() {
        super.onCleared()
        periodOpenSource?.let { periodsMediator.removeSource(it) }
        submittedSource?.let { periodsMediator.removeSource(it) }
        receivedSource?.let { periodsMediator.removeSource(it) }
        periodsMediator.removeSource(periodsSource)
    }
}
