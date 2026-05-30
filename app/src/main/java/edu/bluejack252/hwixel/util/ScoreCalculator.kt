package edu.bluejack252.hwixel.util

object ScoreCalculator {
    fun calculate(completedTasks: Int, totalAssigned: Int, highPriorityCompleted: Int): Float {
        if (totalAssigned == 0) return 0f
        val weightFactor = 1f + (highPriorityCompleted * 0.2f)
        return ((completedTasks.toFloat() / totalAssigned) * 100f * weightFactor).coerceAtMost(100f)
    }
}
