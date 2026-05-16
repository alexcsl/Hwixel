package edu.bluejack252.hwixel.data.repository

import edu.bluejack252.hwixel.data.model.TeamHealthResult
import edu.bluejack252.hwixel.data.source.remote.TeamHealthSource

interface TeamHealthRepository {
    suspend fun analyze(prompt: String): Result<TeamHealthResult>
}

class TeamHealthRepositoryImpl(
    private val source: TeamHealthSource
) : TeamHealthRepository {
    override suspend fun analyze(prompt: String): Result<TeamHealthResult> {
        return source.analyze(prompt)
    }
}
