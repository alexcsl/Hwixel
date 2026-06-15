package edu.bluejack25_2.hwixel.data.mapper

import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.data.source.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskMapperTest {
    private val entity = TaskEntity(
        id = "task-1",
        projectId = "proj-1",
        title = "Design UI",
        description = "Create wireframes",
        status = "todo",
        priority = "high",
        deadline = 1_000L,
        completedAt = 0L,
        assigneesJson = "user-1\nuser-2",
        lastSynced = 999L
    )

    @Test
    fun entityToDomainPreservesScalarFields() {
        val result = entity.toDomain()
        assertEquals("task-1", result.id)
        assertEquals("proj-1", result.projectId)
        assertEquals("Design UI", result.title)
        assertEquals("Create wireframes", result.description)
        assertEquals("todo", result.status)
        assertEquals("high", result.priority)
        assertEquals(1_000L, result.deadline)
        assertEquals(0L, result.completedAt)
    }

    @Test
    fun entityToDomainDecodesAssigneesList() {
        assertEquals(listOf("user-1", "user-2"), entity.toDomain().assignees)
    }

    @Test
    fun domainToEntityRoundTripIsIdempotent() {
        val result = entity.toDomain().toEntity(lastSynced = 999L)
        assertEquals(entity, result)
    }

    @Test
    fun emptyAssigneesRoundTrip() {
        assertEquals(emptyList<String>(), entity.copy(assigneesJson = "").toDomain().assignees)
    }

    @Test
    fun assigneesWithSpecialCharactersRoundTrip() {
        val assignees = listOf("user name", "user@domain.com")
        val task = Task(id = "t", projectId = "p", assignees = assignees)
        assertEquals(assignees, task.toEntity().toDomain().assignees)
    }
}
