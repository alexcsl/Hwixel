package edu.bluejack252.hwixel.data.source.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EvalPeriodNotificationRegistrar(
    private val database: FirebaseDatabase? = FirebaseDatabase.getInstance(),
    private val notifier: EvalPeriodNotifier = FirebaseEvalPeriodNotifier()
) {
    private val states = mutableMapOf<String, Boolean>()
    private var initialized = false
    private var listener: ValueEventListener? = null

    fun register(scope: CoroutineScope) {
        if (listener != null) return
        val ref = requireNotNull(database) { "Firebase database is not configured." }
            .reference
            .child("evaluations")
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val current = snapshot.children.flatMap { projectSnapshot ->
                    val projectId = projectSnapshot.key.orEmpty()
                    projectSnapshot.children.mapNotNull { periodSnapshot ->
                        val periodId = periodSnapshot.key.orEmpty()
                        val isOpen = periodSnapshot.child("isOpen").getValue(Boolean::class.java)
                        if (projectId.isBlank() || periodId.isBlank() || isOpen == null) {
                            null
                        } else {
                            "$projectId|$periodId" to isOpen
                        }
                    }
                }.toMap()

                handlePeriodStates(current, scope)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    internal fun handlePeriodStates(current: Map<String, Boolean>, scope: CoroutineScope) {
        if (!initialized) {
            states.clear()
            states.putAll(current)
            initialized = true
            return
        }

        current.forEach { (key, isOpen) ->
            if (states[key] != isOpen) {
                val projectId = key.substringBefore("|")
                scope.launch {
                    notifier.notifyProjectMembers(projectId, isOpen)
                }
            }
        }
        states.clear()
        states.putAll(current)
    }
}

interface EvalPeriodNotifier {
    suspend fun notifyProjectMembers(projectId: String, isOpen: Boolean)
}

class FirebaseEvalPeriodNotifier(
    private val notificationSource: NotificationFirebaseSource = NotificationFirebaseSource()
) : EvalPeriodNotifier {
    override suspend fun notifyProjectMembers(projectId: String, isOpen: Boolean) {
        val type = if (isOpen) TYPE_EVAL_OPEN else TYPE_EVAL_CLOSE
        val message = if (isOpen) {
            "Peer evaluation period is now open"
        } else {
            "Peer evaluation period has closed"
        }
        notificationSource.fetchProjectMemberIds(projectId).forEach { userId ->
            runCatching {
                notificationSource.writeNotification(userId, type, message, projectId)
            }
        }
    }

    private companion object {
        const val TYPE_EVAL_OPEN = "eval_open"
        const val TYPE_EVAL_CLOSE = "eval_close"
    }
}
