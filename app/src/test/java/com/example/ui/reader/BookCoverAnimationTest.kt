package com.example.ui.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Addendum §4 / §11: the cover opening transition must report its phases, must never start
 * two reader sessions (duplicate taps, §4.E), and the demo "Add to Library" action must be
 * available exactly when requested (§1.D).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookCoverAnimationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun document() = DocumentEntity(
        id = "cover_doc_1",
        title = "Cover Test Book",
        author = "Tester",
        format = DocumentFormat.TXT,
        rawContent = "Some reading content for the cover test.",
        totalPages = 4
    )

    @Test
    fun `tapping cover plays opening once and ignores duplicate taps`() {
        val phases = mutableListOf<BookCoverPhase>()
        var opened = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BookCoverView(
                document = document(),
                onOpenBook = { opened++ },
                onPhaseChange = { phases.add(it) }
            )
        }

        composeRule.onNodeWithTag("book_cover_surface").performClick()
        composeRule.mainClock.advanceTimeBy(120)

        // Duplicate tap while the animation is still running must be ignored (§4.E)
        composeRule.onNodeWithTag("book_cover_surface").performClick()
        composeRule.mainClock.advanceTimeBy(900)

        assertEquals(1, opened)
        assertEquals(listOf(BookCoverPhase.OPENING, BookCoverPhase.OPEN), phases)

        // A tap after the transition must not create a second reader session either
        composeRule.onNodeWithTag("book_cover_surface").performClick()
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals(1, opened)
        assertEquals(2, phases.size)
    }

    @Test
    fun `add to library action is visible and fires only when enabled`() {
        var added = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BookCoverView(
                document = document().copy(isDemo = true),
                onOpenBook = {},
                showAddToLibrary = true,
                onAddToLibrary = { added++ }
            )
        }

        composeRule.onNodeWithTag("add_to_library_button").performClick()
        assertEquals(1, added)
    }

    @Test
    fun `add to library action is absent for documents already in the library`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BookCoverView(
                document = document().copy(isDemo = false),
                onOpenBook = {},
                showAddToLibrary = false,
                onAddToLibrary = {}
            )
        }

        composeRule.onNodeWithTag("add_to_library_button").assertDoesNotExist()
    }
}
