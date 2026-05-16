package edu.bluejack252.hwixel.data.mapper

import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.source.local.ProjectEntity

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    description = description,
    goals = goals,
    dueDate = dueDate,
    createdBy = createdBy,
    completionPercentage = completionPercentage
)

fun Project.toEntity(lastSynced: Long = System.currentTimeMillis()): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    goals = goals,
    dueDate = dueDate,
    createdBy = createdBy,
    completionPercentage = completionPercentage,
    lastSynced = lastSynced
)
