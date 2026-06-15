package edu.bluejack25_2.hwixel.data.mapper

import edu.bluejack25_2.hwixel.data.model.Project
import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.data.source.local.ProjectEntity
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    description = description,
    goals = goals,
    dueDate = dueDate,
    createdBy = createdBy,
    completionPercentage = completionPercentage,
    members = decodeMembers(membersJson)
)

fun Project.toEntity(lastSynced: Long = System.currentTimeMillis()): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    goals = goals,
    dueDate = dueDate,
    createdBy = createdBy,
    completionPercentage = completionPercentage,
    membersJson = encodeMembers(members),
    lastSynced = lastSynced
)

private fun encodeMembers(members: Map<String, ProjectMember>): String {
    return members.entries.joinToString(separator = "\n") { (userId, member) ->
        listOf(userId, member.role, member.status, member.contributionScore.toString())
            .joinToString(separator = "|") { value -> encode(value) }
    }
}

private fun decodeMembers(value: String): Map<String, ProjectMember> {
    if (value.isBlank()) return emptyMap()
    return value.lines().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 4) return@mapNotNull null
        val userId = decode(parts[0])
        userId to ProjectMember(
            userId = userId,
            role = decode(parts[1]),
            status = decode(parts[2]),
            contributionScore = decode(parts[3]).toFloatOrNull() ?: 0f
        )
    }.toMap()
}

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
