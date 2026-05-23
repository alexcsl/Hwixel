package edu.bluejack252.hwixel.util

import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.util.constants.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {
    @Test
    fun topContributorRequiresActorToBeatEveryPeer() {
        val members = mapOf(
            "actor" to ProjectMember(userId = "actor", contributionScore = 92f),
            "peer-a" to ProjectMember(userId = "peer-a", contributionScore = 70f),
            "peer-b" to ProjectMember(userId = "peer-b", contributionScore = 91f)
        )

        assertTrue(BadgeEngine.shouldAwardTopContributor("actor", members))
    }

    @Test
    fun topContributorDoesNotAwardOnTie() {
        val members = mapOf(
            "actor" to ProjectMember(userId = "actor", contributionScore = 92f),
            "peer" to ProjectMember(userId = "peer", contributionScore = 92f)
        )

        assertFalse(BadgeEngine.shouldAwardTopContributor("actor", members))
    }

    @Test
    fun deadlineCrusherRequiresFiveOnTimeCompletions() {
        val tasks = (1..5).map { index ->
            Task(
                id = "task-$index",
                status = Constants.STATUS_DONE,
                deadline = 2_000L,
                completedAt = 1_000L,
                assignees = listOf("actor")
            )
        }

        assertTrue(BadgeEngine.shouldAwardDeadlineCrusher("actor", tasks))
    }

    @Test
    fun deadlineCrusherIgnoresLateTasks() {
        val tasks = (1..5).map { index ->
            Task(
                id = "task-$index",
                status = Constants.STATUS_DONE,
                deadline = 1_000L,
                completedAt = 2_000L,
                assignees = listOf("actor")
            )
        }

        assertFalse(BadgeEngine.shouldAwardDeadlineCrusher("actor", tasks))
    }
}
