package edu.bluejack252.hwixel.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val goals: String,
    val dueDate: Long,
    val createdBy: String,
    val completionPercentage: Float,
    val membersJson: String,
    val lastSynced: Long
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val deadline: Long,
    val assigneesJson: String,
    val lastSynced: Long
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val studentId: String,
    val email: String,
    val phone: String,
    val avatarUrl: String,
    val totalProjectsCompleted: Int,
    val averagePeerRating: Float,
    val badgesJson: String,
    val lastSynced: Long
)
