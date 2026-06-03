package edu.bluejack252.hwixel.data.mapper

import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectMapperTest {
    private val member = ProjectMember(userId = "u1", role = "owner", status = "active", contributionScore = 85.5f)

    private val project = Project(
        id = "proj-1",
        name = "My Project",
        description = "A test project",
        goals = "Ship v1",
        dueDate = 2_000L,
        createdBy = "u1",
        completionPercentage = 0.5f,
        members = mapOf("u1" to member)
    )

    @Test
    fun domainToEntityAndBackPreservesScalarFields() {
        val result = project.toEntity(lastSynced = 999L).toDomain()
        assertEquals(project.id, result.id)
        assertEquals(project.name, result.name)
        assertEquals(project.description, result.description)
        assertEquals(project.goals, result.goals)
        assertEquals(project.dueDate, result.dueDate)
        assertEquals(project.createdBy, result.createdBy)
        assertEquals(project.completionPercentage, result.completionPercentage, 0.001f)
    }

    @Test
    fun domainToEntityAndBackPreservesMembers() {
        val result = project.toEntity(lastSynced = 999L).toDomain()
        assertEquals(project.members, result.members)
    }

    @Test
    fun emptyMembersRoundTrip() {
        val empty = project.copy(members = emptyMap())
        assertTrue(empty.toEntity(lastSynced = 0L).toDomain().members.isEmpty())
    }

    @Test
    fun memberWithSpecialCharactersInRoleRoundTrip() {
        val specialMember = ProjectMember(userId = "u2", role = "co|leader", status = "active", contributionScore = 0f)
        val p = project.copy(members = mapOf("u2" to specialMember))
        assertEquals(specialMember, p.toEntity().toDomain().members["u2"])
    }

    @Test
    fun multipleMembersRoundTrip() {
        val members = mapOf(
            "u1" to ProjectMember(userId = "u1", role = "owner", status = "active", contributionScore = 90f),
            "u2" to ProjectMember(userId = "u2", role = "member", status = "active", contributionScore = 75f)
        )
        val p = project.copy(members = members)
        assertEquals(members, p.toEntity().toDomain().members)
    }
}
