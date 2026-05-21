package edu.bluejack252.hwixel.ui.project.evaluation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import edu.bluejack252.hwixel.data.model.EvaluationSubmission
import edu.bluejack252.hwixel.data.repository.EvalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class PeerEvalViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeEvalRepository
    private lateinit var viewModel: PeerEvalViewModel
    private val observers = mutableListOf<Pair<LiveData<*>, Observer<Any>>>()

    @Before
    fun setUp() {
        repository = FakeEvalRepository()
        viewModel = PeerEvalViewModel(repository, PROJECT_ID, CURRENT_USER_ID)
        observe(viewModel.periods)
        observe(viewModel.uiState)
    }

    @After
    fun tearDown() {
        observers.forEach { (liveData, observer) -> liveData.removeObserver(observer) }
        observers.clear()
    }

    @Test
    fun computeMyAverageReceivedAveragesAllFourRatingCategories() {
        repository.periods.value = listOf(PERIOD_ID)
        repository.receivedLiveData(PERIOD_ID, CURRENT_USER_ID).value = listOf(
            submission(evaluatorId = "u2", evaluateeId = CURRENT_USER_ID, communication = 5, quality = 5, reliability = 3, effort = 3),
            submission(evaluatorId = "u3", evaluateeId = CURRENT_USER_ID, communication = 1, quality = 1, reliability = 3, effort = 3)
        )

        assertEquals(3f, viewModel.computeMyAverageReceived(), 0.001f)
    }

    @Test
    fun submitEvaluationOverwritesPreviousSubmissionForSameEvaluatee() = runTest {
        repository.periods.value = listOf(PERIOD_ID)
        repository.openLiveData(PERIOD_ID).value = true

        viewModel.submitEvaluation("u2", 1, 2, 3, 4, "first")
        advanceUntilIdle()
        viewModel.submitEvaluation("u2", 5, 5, 5, 5, "second")
        advanceUntilIdle()

        val stored = repository.submissions.values.filter { it.evaluateeId == "u2" }
        assertEquals(1, stored.size)
        assertEquals("second", stored.single().feedback)
        assertEquals(5, stored.single().communication)
    }

    @Test
    fun submitEvaluationIsBlockedWhenPeriodIsClosed() = runTest {
        repository.periods.value = listOf(PERIOD_ID)
        repository.openLiveData(PERIOD_ID).value = false

        viewModel.submitEvaluation("u2", 5, 5, 5, 5, "blocked")
        advanceUntilIdle()

        assertTrue(repository.submissions.isEmpty())
        assertEquals(PeerEvalUiState.Error("period_closed"), viewModel.uiState.value)
    }

    @Test
    fun submitEvaluationRecomputesAveragePeerRatingAcrossReceivedSubmissions() = runTest {
        repository.periods.value = listOf(PERIOD_ID)
        repository.openLiveData(PERIOD_ID).value = true
        repository.submissions["old-a-u2"] = submission(
            id = "old-a-u2",
            evaluatorId = "old-a",
            evaluateeId = "u2",
            communication = 4,
            quality = 4,
            reliability = 4,
            effort = 4
        )
        repository.submissions["old-b-u2"] = submission(
            id = "old-b-u2",
            evaluatorId = "old-b",
            evaluateeId = "u2",
            communication = 2,
            quality = 2,
            reliability = 2,
            effort = 2
        )

        viewModel.submitEvaluation("u2", 5, 5, 5, 5, "new")
        advanceUntilIdle()

        assertEquals(3.7f, repository.averagePeerRatings["u2"] ?: 0f, 0.001f)
    }

    private fun <T> observe(liveData: LiveData<T>) {
        @Suppress("UNCHECKED_CAST")
        val observer = Observer<T> {} as Observer<Any>
        liveData.observeForever(observer as Observer<T>)
        observers.add(liveData to observer)
    }

    private fun submission(
        id: String = "",
        evaluatorId: String,
        evaluateeId: String,
        communication: Int,
        quality: Int,
        reliability: Int,
        effort: Int,
        feedback: String = ""
    ) = EvaluationSubmission(
        id = id.ifBlank { "$evaluatorId-$evaluateeId" },
        projectId = PROJECT_ID,
        periodId = PERIOD_ID,
        evaluatorId = evaluatorId,
        evaluateeId = evaluateeId,
        communication = communication,
        quality = quality,
        reliability = reliability,
        effort = effort,
        feedback = feedback
    )

    private class FakeEvalRepository : EvalRepository {
        val periods = MutableLiveData<List<String>>(emptyList())
        val submissions = linkedMapOf<String, EvaluationSubmission>()
        val averagePeerRatings = mutableMapOf<String, Float>()
        private val openByPeriod = mutableMapOf<String, MutableLiveData<Boolean>>()
        private val submittedByMe = mutableMapOf<String, MutableLiveData<Map<String, EvaluationSubmission>>>()
        private val receivedByEvaluatee = mutableMapOf<String, MutableLiveData<List<EvaluationSubmission>>>()

        override fun observePeriodOpen(projectId: String, periodId: String): LiveData<Boolean> {
            return openLiveData(periodId)
        }

        override fun observePeriods(projectId: String): LiveData<List<String>> = periods

        override fun observeSubmittedByMe(
            projectId: String,
            periodId: String,
            evaluatorId: String
        ): LiveData<Map<String, EvaluationSubmission>> {
            return submittedLiveData(periodId, evaluatorId)
        }

        override fun observeReceivedEvals(
            projectId: String,
            periodId: String,
            evaluateeId: String
        ): LiveData<List<EvaluationSubmission>> {
            return receivedLiveData(periodId, evaluateeId)
        }

        override suspend fun submitEvaluation(submission: EvaluationSubmission): Result<Unit> {
            submissions[submission.id] = submission
            submittedLiveData(submission.periodId, submission.evaluatorId).value = submissions.values
                .filter { it.periodId == submission.periodId && it.evaluatorId == submission.evaluatorId }
                .associateBy { it.evaluateeId }
            receivedLiveData(submission.periodId, submission.evaluateeId).value = submissions.values
                .filter { it.periodId == submission.periodId && it.evaluateeId == submission.evaluateeId }
            return Result.success(Unit)
        }

        override suspend fun setPeriodOpen(projectId: String, periodId: String, isOpen: Boolean): Result<Unit> {
            openLiveData(periodId).value = isOpen
            return Result.success(Unit)
        }

        override suspend fun createPeriod(projectId: String): Result<String> {
            val id = "period-${(periods.value?.size ?: 0) + 1}"
            periods.value = periods.value.orEmpty() + id
            openLiveData(id).value = true
            return Result.success(id)
        }

        override suspend fun fetchAllReceivedSubmissions(
            projectId: String,
            evaluateeId: String
        ): Result<List<EvaluationSubmission>> {
            return Result.success(submissions.values.filter { it.evaluateeId == evaluateeId })
        }

        override suspend fun updateAveragePeerRating(userId: String, average: Float): Result<Unit> {
            averagePeerRatings[userId] = average
            return Result.success(Unit)
        }

        fun openLiveData(periodId: String): MutableLiveData<Boolean> {
            return openByPeriod.getOrPut(periodId) { MutableLiveData(false) }
        }

        fun receivedLiveData(periodId: String, evaluateeId: String): MutableLiveData<List<EvaluationSubmission>> {
            return receivedByEvaluatee.getOrPut("$periodId:$evaluateeId") { MutableLiveData(emptyList()) }
        }

        private fun submittedLiveData(
            periodId: String,
            evaluatorId: String
        ): MutableLiveData<Map<String, EvaluationSubmission>> {
            return submittedByMe.getOrPut("$periodId:$evaluatorId") { MutableLiveData(emptyMap()) }
        }
    }

    private companion object {
        const val PROJECT_ID = "p1"
        const val PERIOD_ID = "period-1"
        const val CURRENT_USER_ID = "u1"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
