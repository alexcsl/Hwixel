package edu.bluejack252.hwixel.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {
    @Test
    fun zeroAssignedTasksReturnsZero() {
        assertEquals(0f, ScoreCalculator.calculate(0, 0, 0))
    }

    @Test
    fun completedTasksApplyHighPriorityWeight() {
        assertEquals(140f, ScoreCalculator.calculate(2, 2, 2))
    }
}
