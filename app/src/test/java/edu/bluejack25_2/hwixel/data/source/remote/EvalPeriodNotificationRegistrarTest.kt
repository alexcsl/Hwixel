package edu.bluejack25_2.hwixel.data.source.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EvalPeriodNotificationRegistrarTest {

    @Test
    fun firstSnapshotOnlySeedsStateWithoutFanOut() = runTest {
        val notifier = FakeEvalPeriodNotifier()
        val registrar = EvalPeriodNotificationRegistrar(database = null, notifier = notifier)

        registrar.handlePeriodStates(
            current = mapOf("project-1|period-1" to false),
            scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        assertEquals(emptyList<NotifyCall>(), notifier.calls)
    }

    @Test
    fun isOpenChangeFansOutForChangedProjectOnly() = runTest {
        val notifier = FakeEvalPeriodNotifier()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val registrar = EvalPeriodNotificationRegistrar(database = null, notifier = notifier)

        registrar.handlePeriodStates(
            current = mapOf(
                "project-1|period-1" to false,
                "project-2|period-1" to true
            ),
            scope = scope
        )
        registrar.handlePeriodStates(
            current = mapOf(
                "project-1|period-1" to true,
                "project-2|period-1" to true
            ),
            scope = scope
        )
        advanceUntilIdle()

        assertEquals(listOf(NotifyCall("project-1", true)), notifier.calls)
    }

    private class FakeEvalPeriodNotifier : EvalPeriodNotifier {
        val calls = mutableListOf<NotifyCall>()

        override suspend fun notifyProjectMembers(projectId: String, isOpen: Boolean) {
            calls.add(NotifyCall(projectId, isOpen))
        }
    }

    private data class NotifyCall(
        val projectId: String,
        val isOpen: Boolean
    )
}
