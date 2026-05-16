package edu.bluejack252.hwixel.data.source.local

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}
