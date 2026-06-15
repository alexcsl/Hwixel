package edu.bluejack25_2.hwixel.util

import edu.bluejack25_2.hwixel.data.model.ProjectMember
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.util.constants.Constants

object BadgeEngine {
    const val BADGE_TOP_CONTRIBUTOR = "top_contributor"
    const val BADGE_DEADLINE_CRUSHER = "deadline_crusher"
    const val DEADLINE_CRUSHER_THRESHOLD = 5

    fun shouldAwardTopContributor(actorId: String, members: Map<String, ProjectMember>): Boolean {
        val actorScore = members[actorId]?.contributionScore ?: return false
        val peerScores = members
            .filterKeys { it != actorId }
            .values
            .map { it.contributionScore }
        return peerScores.isNotEmpty() && peerScores.all { actorScore > it }
    }

    fun shouldAwardDeadlineCrusher(actorId: String, tasks: List<Task>): Boolean {
        return tasks.count { task ->
            task.status == Constants.STATUS_DONE &&
                task.assignees.contains(actorId) &&
                task.deadline > 0L &&
                task.completedAt > 0L &&
                task.completedAt <= task.deadline
        } >= DEADLINE_CRUSHER_THRESHOLD
    }

    fun withBadge(existing: List<String>, badge: String): List<String> {
        return if (existing.contains(badge)) existing else existing + badge
    }
}
