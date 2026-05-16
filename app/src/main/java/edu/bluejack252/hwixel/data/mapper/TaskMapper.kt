package edu.bluejack252.hwixel.data.mapper

import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.data.source.local.ListStringConverter
import edu.bluejack252.hwixel.data.source.local.TaskEntity

private val listConverter = ListStringConverter()

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    deadline = deadline,
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
    assigneesJson = listConverter.fromList(assignees),
    lastSynced = lastSynced
)
