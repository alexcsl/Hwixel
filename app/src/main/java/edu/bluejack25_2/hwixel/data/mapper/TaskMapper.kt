package edu.bluejack25_2.hwixel.data.mapper

import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.data.source.local.ListStringConverter
import edu.bluejack25_2.hwixel.data.source.local.TaskEntity

private val listConverter = ListStringConverter()

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    deadline = deadline,
    completedAt = completedAt,
    assignees = listConverter.toList(assigneesJson)
)

fun Task.toEntity(lastSynced: Long = System.currentTimeMillis()): TaskEntity = TaskEntity(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    deadline = deadline,
    completedAt = completedAt,
    assigneesJson = listConverter.fromList(assignees),
    lastSynced = lastSynced
)
