package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.EvaluationSubmission
import kotlinx.coroutines.tasks.await

class EvalFirebaseSource {

    private val db = FirebaseDatabase.getInstance().reference

    /** Observe whether the given period is open. */
    fun observePeriodOpen(projectId: String, periodId: String): LiveData<Boolean> {
        val liveData = MutableLiveData<Boolean>(false)
        db.child("evaluations").child(projectId).child(periodId).child("isOpen")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    liveData.postValue(snapshot.getValue(Boolean::class.java) ?: false)
                }
                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(false)
                }
            })
        return liveData
    }

    /** Observe all submissions made by evaluatorId for this period. */
    fun observeSubmittedByMe(
        projectId: String,
        periodId: String,
        evaluatorId: String
    ): LiveData<Map<String, EvaluationSubmission>> {
        val liveData = MutableLiveData<Map<String, EvaluationSubmission>>(emptyMap())
        db.child("evaluations").child(projectId).child(periodId)
            .child("submissions").child(evaluatorId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = snapshot.children.mapNotNull { child ->
                        val evaluateeId = child.key ?: return@mapNotNull null
                        val sub = parseSubmission(child, projectId, periodId, evaluatorId, evaluateeId)
                        evaluateeId to sub
                    }.toMap()
                    liveData.postValue(map)
                }
                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(emptyMap())
                }
            })
        return liveData
    }

    /** Observe evaluations received by evaluateeId for this period. */
    fun observeReceivedEvals(
        projectId: String,
        periodId: String,
        evaluateeId: String
    ): LiveData<List<EvaluationSubmission>> {
        val liveData = MutableLiveData<List<EvaluationSubmission>>(emptyList())
        db.child("evaluations").child(projectId).child(periodId)
            .child("submissions")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<EvaluationSubmission>()
                    snapshot.children.forEach { evaluatorSnap ->
                        val evaluatorId = evaluatorSnap.key ?: return@forEach
                        evaluatorSnap.child(evaluateeId).takeIf { it.exists() }?.let { child ->
                            list.add(parseSubmission(child, projectId, periodId, evaluatorId, evaluateeId))
                        }
                    }
                    liveData.postValue(list)
                }
                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(emptyList())
                }
            })
        return liveData
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
        val ref = db.child("evaluations").child(projectId).push()
        val periodId = ref.key ?: throw IllegalStateException("Failed to generate period ID")
        ref.child("isOpen").setValue(true).await()
        periodId
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
        val liveData = MutableLiveData<List<String>>(emptyList())
        db.child("evaluations").child(projectId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    liveData.postValue(snapshot.children.mapNotNull { it.key })
                }
                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(emptyList())
                }
            })
        return liveData
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
