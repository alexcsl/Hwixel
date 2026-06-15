package edu.bluejack25_2.hwixel.data.repository

import edu.bluejack25_2.hwixel.data.model.TeamHealthResult
import edu.bluejack25_2.hwixel.data.source.remote.TeamHealthSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamHealthRepositoryTest {
    private val source = FakeTeamHealthSource()
    private val repository = TeamHealthRepositoryImpl(source)

    @Test
    fun analyzeReturnsSourceResult() = runTest {
        val expected = TeamHealthResult(
            status = "healthy",
            summary = "Team is performing well",
            recommendations = listOf("Keep sprint rhythm", "Improve code reviews")
        )
        source.result = Result.success(expected)
        val result = repository.analyze("team data prompt")
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun analyzeForwardsPromptToSource() = runTest {
        repository.analyze("my custom prompt")
        assertEquals("my custom prompt", source.lastPrompt)
    }

    @Test
    fun analyzeReturnsFailureWhenSourceFails() = runTest {
        source.result = Result.failure(RuntimeException("Network error"))
        val result = repository.analyze("team data prompt")
        assertTrue(result.isFailure)
    }

    private class FakeTeamHealthSource : TeamHealthSource {
        var result: Result<TeamHealthResult> = Result.success(TeamHealthResult())
        var lastPrompt: String? = null

        override suspend fun analyze(prompt: String): Result<TeamHealthResult> {
            lastPrompt = prompt
            return result
        }
    }
}
