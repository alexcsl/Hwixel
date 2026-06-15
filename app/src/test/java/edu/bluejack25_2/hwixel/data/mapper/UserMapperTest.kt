package edu.bluejack25_2.hwixel.data.mapper

import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.source.local.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMapperTest {
    private val entity = UserEntity(
        id = "user-1",
        name = "Alice",
        studentId = "STU001",
        email = "alice@example.com",
        phone = "08123",
        avatarUrl = "https://example.com/avatar.jpg",
        totalProjectsCompleted = 5,
        averagePeerRating = 4.2f,
        badgesJson = "gold\nsilver",
        lastSynced = 999L
    )

    @Test
    fun entityToDomainPreservesScalarFields() {
        val result = entity.toDomain()
        assertEquals("user-1", result.id)
        assertEquals("Alice", result.name)
        assertEquals("STU001", result.studentId)
        assertEquals("alice@example.com", result.email)
        assertEquals("08123", result.phone)
        assertEquals("https://example.com/avatar.jpg", result.avatarUrl)
        assertEquals(5, result.totalProjectsCompleted)
        assertEquals(4.2f, result.averagePeerRating, 0.001f)
    }

    @Test
    fun entityToDomainDecodesBadgesList() {
        assertEquals(listOf("gold", "silver"), entity.toDomain().badges)
    }

    @Test
    fun domainToEntityRoundTripIsIdempotent() {
        val result = entity.toDomain().toEntity(lastSynced = 999L)
        assertEquals(entity, result)
    }

    @Test
    fun emptyBadgesRoundTrip() {
        assertEquals(emptyList<String>(), entity.copy(badgesJson = "").toDomain().badges)
    }

    @Test
    fun badgesWithSpecialCharactersRoundTrip() {
        val badges = listOf("Top Performer", "5-Star Reviewer")
        val user = User(id = "u", badges = badges)
        assertEquals(badges, user.toEntity().toDomain().badges)
    }
}
