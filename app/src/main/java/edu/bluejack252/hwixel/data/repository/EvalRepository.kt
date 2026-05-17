package edu.bluejack252.hwixel.data.repository

import androidx.lifecycle.LiveData
import com.google.firebase.database.FirebaseDatabase
import edu.bluejack252.hwixel.data.model.EvaluationSubmission
import edu.bluejack252.hwixel.data.source.remote.EvalFirebaseSource
import kotlinx.coroutines.tasks.await

interface EvalRepository {
    fun observePeriodOpen(projectId: String, periodId: String): LiveData<Boolean>
    fun observePeriods(projectId: String): LiveData<List<String>>
    fun observeSubmittedByMe(projectId: String, periodId: String, evaluatorId: String): LiveData<Map<String, EvaluationSubmission>>
    fun observeReceivedEvals(projectId: String, periodId: String, evaluateeId: String): LiveData<List<EvaluationSubmission>>
    suspend fun submitEvaluation(submission: EvaluationSubmission): Result<Unit>
    suspend fun setPeriodOpen(projectId: String, periodId: String, isOpen: Boolean): Result<Unit>
    suspend fun createPeriod(projectId: String): Result<String>
    suspend fun fetchAllReceivedSubmissions(projectId: String, evaluateeId: String): Result<List<EvaluationSubmission>>
    suspend fun updateAveragePeerRating(userId: String, average: Float): Result<Unit>
}

class EvalRepositoryImpl(
    private val firebaseSource: EvalFirebaseSource
) : EvalRepository {

    private val usersRef = FirebaseDatabase.getInstance().reference.child("users")

    override fun observePeriodOpen(projectId: String, periodId: String) =
        firebaseSource.observePeriodOpen(projectId, periodId)

    override fun observePeriods(projectId: String) =
        firebaseSource.observePeriods(projectId)

    override fun observeSubmittedByMe(projectId: String, periodId: String, evaluatorId: String) =
        firebaseSource.observeSubmittedByMe(projectId, periodId, evaluatorId)

    override fun observeReceivedEvals(projectId: String, periodId: String, evaluateeId: String) =
        firebaseSource.observeReceivedEvals(projectId, periodId, evaluateeId)

    override suspend fun submitEvaluation(submission: EvaluationSubmission) =
        firebaseSource.submitEvaluation(submission)

    override suspend fun setPeriodOpen(projectId: String, periodId: String, isOpen: Boolean) =
        firebaseSource.setPeriodOpen(projectId, periodId, isOpen)

    override suspend fun createPeriod(projectId: String) =
        firebaseSource.createPeriod(projectId)

    override suspend fun fetchAllReceivedSubmissions(projectId: String, evaluateeId: String) =
        runCatching { firebaseSource.fetchAllReceivedSubmissions(projectId, evaluateeId) }

    override suspend fun updateAveragePeerRating(userId: String, average: Float): Result<Unit> = runCatching {
        usersRef.child(userId).child("averagePeerRating").setValue(average).await()
    }
}
