package edu.bluejack25_2.hwixel.data.source.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun observeAll(): LiveData<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}
