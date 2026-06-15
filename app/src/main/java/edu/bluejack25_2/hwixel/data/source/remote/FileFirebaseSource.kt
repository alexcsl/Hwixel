package edu.bluejack25_2.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack25_2.hwixel.data.model.FileLink
import kotlinx.coroutines.tasks.await

class FileFirebaseSource : FileRemoteSource {

    private val db = FirebaseDatabase.getInstance().reference

    override fun observeFiles(projectId: String): LiveData<List<FileLink>> {
        return object : LiveData<List<FileLink>>() {
            private val ref = db.child("files").child(projectId)
            private var listener: ValueEventListener? = null

            override fun onActive() {
                if (listener != null) return
                listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val files = snapshot.children.mapNotNull { child ->
                            val label = child.child("label").getValue(String::class.java) ?: return@mapNotNull null
                            val url = child.child("url").getValue(String::class.java) ?: return@mapNotNull null
                            val type = child.child("type").getValue(String::class.java) ?: "other"
                            val versionNotes = child.child("versionNotes").getValue(String::class.java) ?: ""
                            val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                            FileLink(
                                id = child.key ?: "",
                                projectId = projectId,
                                label = label,
                                url = url,
                                type = type,
                                versionNotes = versionNotes,
                                createdAt = createdAt
                            )
                        }
                        postValue(files)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        postValue(emptyList())
                    }
                }.also { ref.addValueEventListener(it) }
            }

            override fun onInactive() {
                listener?.let(ref::removeEventListener)
                listener = null
            }
        }
    }

    override suspend fun addFile(projectId: String, file: FileLink): Result<Unit> {
        return try {
            val ref = db.child("files").child(projectId).push()
            val data = mapOf(
                "label" to file.label,
                "url" to file.url,
                "type" to file.type,
                "versionNotes" to file.versionNotes,
                "createdAt" to file.createdAt
            )
            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(projectId: String, fileId: String): Result<Unit> {
        return try {
            db.child("files").child(projectId).child(fileId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
