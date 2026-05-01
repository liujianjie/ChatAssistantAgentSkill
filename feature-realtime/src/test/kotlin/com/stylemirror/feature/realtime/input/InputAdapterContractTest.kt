package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.error.DomainError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Contract test: every adapter that doesn't have a real implementation yet
 * MUST surface `DomainError.NotImplemented` (wrapped in [DomainErrorException])
 * the moment a consumer actually collects from it. We deliberately do NOT
 * write a deeper happy-path test for these — when each adapter ships its real
 * implementation (T17 / T18 / share-sheet task), the corresponding placeholder
 * is replaced and gets its own focused test suite there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InputAdapterContractTest : StringSpec({

    "ScreenshotInput is unimplemented and surfaces NotImplemented" {
        runTest(UnconfinedTestDispatcher()) {
            val adapter = ScreenshotInput()
            val ex =
                shouldThrow<DomainErrorException> {
                    adapter.receive().first()
                }
            ex.domainError.shouldBeInstanceOf<DomainError.NotImplemented>()
            ex.domainError shouldBe DomainError.NotImplemented
        }
    }

    "OverlayInput is unimplemented and surfaces NotImplemented" {
        runTest(UnconfinedTestDispatcher()) {
            val adapter = OverlayInput()
            val ex =
                shouldThrow<DomainErrorException> {
                    adapter.receive().first()
                }
            ex.domainError shouldBe DomainError.NotImplemented
        }
    }

    "ShareSheetInput is unimplemented and surfaces NotImplemented" {
        runTest(UnconfinedTestDispatcher()) {
            val adapter = ShareSheetInput()
            val ex =
                shouldThrow<DomainErrorException> {
                    adapter.receive().first()
                }
            ex.domainError shouldBe DomainError.NotImplemented
        }
    }
})
