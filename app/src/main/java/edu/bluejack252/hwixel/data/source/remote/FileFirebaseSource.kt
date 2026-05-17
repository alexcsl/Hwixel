package edu.bluejack252.hwixel.data.source.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.bluejack252.hwixel.data.model.FileLink
import kotlinx.coroutines.tasks.await

class FileFirebaseSource {

    private val db = FirebaseDatabase.getInstance().reference

    fun observeFiles(projectId: String): LiveData<List<FileLink>> {
        val liveData = MutableLiveData<List<FileLink>>()
        db.child("files").child(projectId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val files = snapshot.children.mapNotNull { child ->
                        val label = child.child("label").getValue(String::class.java) ?: return@mapNotNull null
                        val url = child.child("url").getValue(String::class.java) ?: return@mapNotNull null
                        val type = child.child("type").getValue(String::class.java) ?: "other"
                        val versionNotes = child.child("versionNotes").getValue(String::class.java) ?: ""
                        FileLink(
                            id = child.key ?: "",
                            projectId = projectId,
                            label = label,
                            url = url,
                            type = type,
                            versionNotes = versionNotes
                        )
                    }
                    liveData.postValue(files)
                }

                override fun onCancelled(error: DatabaseError) {
                    liveData.postValue(emptyList())
                }
            })
        return liveData
    }

    suspend fun addFile(projectId: String, file: FileLink): Result<Unit> {
        return try {
            val ref = db.child("files").child(projectId).push()
            val data = mapOf(
                "label" to file.label,
                "url" to file.url,
                "type" to file.type,
                "versionNotes" to file.versionNotes
            )
            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(projectId: String, fileId: String): Result<Unit> {
        return try {
            db.child("files").child(projectId).child(fileId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
