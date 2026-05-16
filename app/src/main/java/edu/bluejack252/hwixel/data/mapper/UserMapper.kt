package edu.bluejack252.hwixel.data.mapper

import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.source.local.ListStringConverter
import edu.bluejack252.hwixel.data.source.local.UserEntity

private val badgesConverter = ListStringConverter()

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    studentId = studentId,
    email = email,
    avatarUrl = avatarUrl,
    totalProjectsCompleted = totalProjectsCompleted,
    averagePeerRating = averagePeerRating,
    badges = badgesConverter.toList(badgesJson)
)

fun User.toEntity(lastSynced: Long = System.currentTimeMillis()): UserEntity = UserEntity(
    id = id,
    name = name,
    studentId = studentId,
    email = email,
    avatarUrl = avatarUrl,
    totalProjectsCompleted = totalProjectsCompleted,
    averagePeerRating = averagePeerRating,
    badgesJson = badgesConverter.fromList(badges),
    lastSynced = lastSynced
)
