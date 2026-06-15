package edu.bluejack25_2.hwixel.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun returnsZeroWhenNoTasksAreAssigned() {
        assertEquals(0f, ScoreCalculator.calculate(completedTasks = 3, totalAssigned = 0, highPriorityCompleted = 2))
    }

    @Test
    fun calculatesBaseCompletionPercentage() {
        assertEquals(50f, ScoreCalculator.calculate(completedTasks = 2, totalAssigned = 4, highPriorityCompleted = 0))
    }

    @Test
    fun appliesHighPriorityWeightFactor() {
        assertEquals(140f, ScoreCalculator.calculate(completedTasks = 5, totalAssigned = 5, highPriorityCompleted = 2))
    }
}
