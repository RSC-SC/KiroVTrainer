package com.vtrainer.app.presentation.history

import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.TrainingLog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock

/**
 * Property-based test for training log data completeness in history.
 *
 * **Validates: Requirements 7.2**
 *
 * Property 14: Training Log Data Completeness in History
 *
 * For any list of TrainingLogs returned by the repository, the TrainingHistoryViewModel
 * must preserve ALL logs without dropping any — the count of logs in the state must equal
 * the count returned by the repository, and every logId from the repository must appear
 * in the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingLogDataCompletenessPropertyTest : FunSpec({

    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    fun makeViewModel(logs: List<TrainingLog>): TrainingHistoryViewModel {
        val mockRepo = mockk<TrainingLogRepository>(relaxed = true)
        every { mockRepo.getTrainingHistory() } returns flowOf(logs)
        return TrainingHistoryViewModel(
            trainingLogRepository = mockRepo,
            clock = Clock.System
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 1: No logs are dropped — state.logs.size equals repository count
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 14: No logs are dropped — state count equals repository count").config(
        invocations = 1
    ) {
        runTest(testDispatcher) {
            checkAll(1, Arb.trainingLogList()) { logs ->
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                state.logs.size shouldBe logs.size
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Every logId from the repository appears in state.logs
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 14: Every logId from repository appears in state").config(
        invocations = 1
    ) {
        runTest(testDispatcher) {
            checkAll(1, Arb.trainingLogList()) { logs ->
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                val stateLogIds = state.logs.map { it.logId }.toSet()
                val repoLogIds = logs.map { it.logId }.toSet()

                repoLogIds.forEach { id ->
                    stateLogIds.contains(id) shouldBe true
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: No duplicate logIds are introduced by the ViewModel
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 14: No duplicate logIds are introduced by ViewModel").config(
        invocations = 1
    ) {
        runTest(testDispatcher) {
            checkAll(1, Arb.trainingLogList()) { logs ->
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                val stateLogIds = state.logs.map { it.logId }
                val uniqueStateLogIds = stateLogIds.toSet()

                stateLogIds.size shouldBe uniqueStateLogIds.size
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Empty list from repository results in empty state.logs
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 14: Empty repository list results in empty state logs").config(
        invocations = 1
    ) {
        runTest(testDispatcher) {
            val vm = makeViewModel(emptyList())
            advanceUntilIdle()

            val state = vm.state.value
            state.logs.shouldBeEmpty()
        }
    }
})
