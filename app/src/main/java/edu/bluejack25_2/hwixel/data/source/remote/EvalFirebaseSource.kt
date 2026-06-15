package edu.bluejack25_2.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import edu.bluejack25_2.hwixel.data.model.EvaluationSubmission
import kotlinx.coroutines.tasks.await

class EvalFirebaseSource {

    private val db = FirebaseDatabase.getInstance().reference

    /** Observe whether the given period is open. */
    fun observePeriodOpen(projectId: String, periodId: String): LiveData<Boolean> {
        return FirebaseValueLiveData(db.child("evaluations").child(projectId).child(periodId).child("isOpen")) {
            it.getValue(Boolean::class.java) ?: false
        }
    }

    /** Observe all submissions made by evaluatorId for this period. */
    fun observeSubmittedByMe(
        projectId: String,
        periodId: String,
        evaluatorId: String
    ): LiveData<Map<String, EvaluationSubmission>> {
        return FirebaseValueLiveData(
            db.child("evaluations").child(projectId).child(periodId)
                .child("submissions").child(evaluatorId)
        ) { snapshot ->
            snapshot.children.mapNotNull { child ->
                val evaluateeId = child.key ?: return@mapNotNull null
                val sub = parseSubmission(child, projectId, periodId, evaluatorId, evaluateeId)
                evaluateeId to sub
            }.toMap()
        }
    }

    /** Observe evaluations received by evaluateeId for this period. */
    fun observeReceivedEvals(
        projectId: String,
        periodId: String,
        evaluateeId: String
    ): LiveData<List<EvaluationSubmission>> {
        return FirebaseValueLiveData(
            db.child("evaluations").child(projectId).child(periodId).child("submissions")
        ) { snapshot ->
            val list = mutableListOf<EvaluationSubmission>()
            snapshot.children.forEach { evaluatorSnap ->
                val evaluatorId = evaluatorSnap.key ?: return@forEach
                evaluatorSnap.child(evaluateeId).takeIf { it.exists() }?.let { child ->
                    list.add(parseSubmission(child, projectId, periodId, evaluatorId, evaluateeId))
                }
            }
            list
        }
    }

    suspend fun submitEvaluation(submission: EvaluationSubmission): Result<Unit> = runCatching {
        val data = mapOf(
            "communication" to submission.communication,
            "quality" to submission.quality,
            "reliability" to submission.reliability,
            "effort" to submission.effort,
            "feedback" to submission.feedback
        )
        db.child("evaluations")
            .child(submission.projectId)
            .child(submission.periodId)
            .child("submissions")
            .child(submission.evaluatorId)
            .child(submission.evaluateeId)
            .setValue(data)
            .await()
    }

    suspend fun setPeriodOpen(projectId: String, periodId: String, isOpen: Boolean): Result<Unit> = runCatching {
        db.child("evaluations").child(projectId).child(periodId).child("isOpen")
            .setValue(isOpen).await()
    }

    suspend fun createPeriod(projectId: String): Result<String> = runCatching {
        val periodsRef = db.child("evaluations").child(projectId)
        val existing = periodsRef.get().await().children.firstOrNull {
            it.child("isOpen").getValue(Boolean::class.java) == true
        }
        if (existing != null) return@runCatching existing.key
            ?: throw IllegalStateException("Failed to read period ID")

        val ref = periodsRef.push()
        val periodId = ref.key ?: throw IllegalStateException("Failed to generate period ID")
        ref.updateChildren(
            mapOf(
                "isOpen" to true,
                "createdAt" to ServerValue.TIMESTAMP
            )
        ).await()
        periodId
    }

    suspend fun updateAveragePeerRating(userId: String, average: Float): Result<Unit> = runCatching {
        db.child("users").child(userId).child("averagePeerRating").setValue(average).await()
    }

    /** Fetch all submissions ever received by evaluateeId across all periods for rating recompute. */
    suspend fun fetchAllReceivedSubmissions(projectId: String, evaluateeId: String): List<EvaluationSubmission> {
        val snapshot = db.child("evaluations").child(projectId).get().await()
        val list = mutableListOf<EvaluationSubmission>()
        snapshot.children.forEach { periodSnap ->
            val periodId = periodSnap.key ?: return@forEach
            periodSnap.child("submissions").children.forEach { evaluatorSnap ->
                val evaluatorId = evaluatorSnap.key ?: return@forEach
                evaluatorSnap.child(evaluateeId).takeIf { it.exists() }?.let { child ->
                    list.add(parseSubmission(child, projectId, periodId, evaluatorId, evaluateeId))
                }
            }
        }
        return list
    }

    /** Observe the list of available period IDs for this project. */
    fun observePeriods(projectId: String): LiveData<List<String>> {
        return FirebaseValueLiveData(db.child("evaluations").child(projectId)) { snapshot ->
            snapshot.children
                .sortedWith(compareBy<DataSnapshot> {
                    it.child("createdAt").getValue(Long::class.java) ?: Long.MIN_VALUE
                }.thenBy { it.key.orEmpty() })
                .mapNotNull { it.key }
        }
    }

    private fun parseSubmission(
        snap: DataSnapshot,
        projectId: String,
        periodId: String,
        evaluatorId: String,
        evaluateeId: String
    ) = EvaluationSubmission(
        id = "$evaluatorId-$evaluateeId",
        projectId = projectId,
        periodId = periodId,
        evaluatorId = evaluatorId,
        evaluateeId = evaluateeId,
        communication = snap.child("communication").getValue(Int::class.java) ?: 0,
        quality = snap.child("quality").getValue(Int::class.java) ?: 0,
        reliability = snap.child("reliability").getValue(Int::class.java) ?: 0,
        effort = snap.child("effort").getValue(Int::class.java) ?: 0,
        feedback = snap.child("feedback").getValue(String::class.java) ?: ""
    )

}
