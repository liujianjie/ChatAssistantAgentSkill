@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.stylemirror.feature.overlay.candidate

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.stylemirror.feature.realtime.matching.FakeStyleEngine
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant

private val T0: Instant = Instant.parse("2026-05-31T10:00:00Z")

private fun ctxWithTheirs(text: String): ConversationContext =
    ConversationContext(
        partnerId = PartnerId("p"),
        messages =
            listOf(
                Message.Theirs(
                    id = MessageId("t1"),
                    content = text,
                    sentAt = T0,
                    displayName = "对方",
                ),
            ),
    )

private fun ctxWithOnlyMine(): ConversationContext =
    ConversationContext(
        partnerId = PartnerId("p"),
        messages = listOf(Message.Mine(MessageId("m1"), "自言", T0)),
    )

private fun newGenerator() =
    CandidateGenerator(
        llmProvider = FakeLLMProvider(),
        styleEngine = FakeStyleEngine(),
    )

class OverlayCandidateControllerTest : StringSpec({

    "starts in Idle" {
        val scope = TestScope(StandardTestDispatcher())
        val ctrl =
            OverlayCandidateController(
                candidateGenerator = newGenerator(),
                scope = scope,
                snapshotProvider = { null },
            )
        ctrl.state.value shouldBe OverlayCandidateController.UiState.Idle
    }

    "trigger with no snapshot becomes Empty" {
        val scope = TestScope(StandardTestDispatcher())
        val ctrl =
            OverlayCandidateController(
                candidateGenerator = newGenerator(),
                scope = scope,
                snapshotProvider = { null },
            )
        ctrl.trigger()
        ctrl.state.value shouldBe OverlayCandidateController.UiState.Empty
    }

    "trigger with no Theirs messages becomes Empty (don't burn LLM tokens)" {
        val scope = TestScope(StandardTestDispatcher())
        val ctrl =
            OverlayCandidateController(
                candidateGenerator = newGenerator(),
                scope = scope,
                snapshotProvider = { ctxWithOnlyMine() },
            )
        ctrl.trigger()
        ctrl.state.value shouldBe OverlayCandidateController.UiState.Empty
    }

    "trigger with valid snapshot transitions Loading then Ready with 3 candidates" {
        runTest {
            val ctrl =
                OverlayCandidateController(
                    candidateGenerator = newGenerator(),
                    scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                    snapshotProvider = { ctxWithTheirs("最近怎么样") },
                )
            ctrl.trigger()
            // UnconfinedTestDispatcher: launched coroutines run inline, so by
            // the time trigger() returns the Ready state is already set.
            val state = ctrl.state.value
            state.shouldBeInstanceOf<OverlayCandidateController.UiState.Ready>()
            state.candidates shouldHaveSize 3
        }
    }

    "trigger() while already Loading is a no-op (no concurrent fan-out)" {
        runTest {
            var calls = 0
            val ctrl =
                OverlayCandidateController(
                    candidateGenerator = newGenerator(),
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                    snapshotProvider = {
                        calls++
                        ctxWithTheirs("hi")
                    },
                )
            ctrl.trigger()
            ctrl.state.value shouldBe OverlayCandidateController.UiState.Loading
            ctrl.trigger() // should be coalesced
            calls shouldBe 1
            testScheduler.advanceUntilIdle()
            ctrl.state.value.shouldBeInstanceOf<OverlayCandidateController.UiState.Ready>()
        }
    }

    "dismiss() resets state to Idle even when Ready" {
        runTest {
            val ctrl =
                OverlayCandidateController(
                    candidateGenerator = newGenerator(),
                    scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                    snapshotProvider = { ctxWithTheirs("yo") },
                )
            ctrl.trigger()
            ctrl.state.value.shouldBeInstanceOf<OverlayCandidateController.UiState.Ready>()
            ctrl.dismiss()
            ctrl.state.value shouldBe OverlayCandidateController.UiState.Idle
        }
    }

    "dismiss() while Loading cancels the in-flight job" {
        runTest {
            val ctrl =
                OverlayCandidateController(
                    candidateGenerator = newGenerator(),
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                    snapshotProvider = { ctxWithTheirs("yo") },
                )
            ctrl.trigger()
            ctrl.state.value shouldBe OverlayCandidateController.UiState.Loading
            ctrl.dismiss()
            ctrl.state.value shouldBe OverlayCandidateController.UiState.Idle
            testScheduler.advanceUntilIdle()
            ctrl.state.value shouldBe OverlayCandidateController.UiState.Idle
        }
    }

    "trigger again after Ready re-runs and lands back on Ready" {
        runTest {
            val ctrl =
                OverlayCandidateController(
                    candidateGenerator = newGenerator(),
                    scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                    snapshotProvider = { ctxWithTheirs("yo") },
                )
            ctrl.trigger()
            ctrl.state.value.shouldBeInstanceOf<OverlayCandidateController.UiState.Ready>()
            ctrl.trigger()
            ctrl.state.value.shouldBeInstanceOf<OverlayCandidateController.UiState.Ready>()
        }
    }
})
