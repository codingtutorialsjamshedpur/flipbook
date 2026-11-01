package com.example.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.example.service.DemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Addendum §1 / §2 / §11: import entry point is labelled "Copy Or Upload", Hindi is never
 * presented as a file format (it is a language), the empty state is honest, and all eight
 * demo chips are present with format + language labels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryScreenLabelsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setLibrary(onLoadSample: (Int) -> Unit = {}) {
        composeRule.setContent {
            LibraryScreen(
                documents = emptyList(),
                onSelectDocument = {},
                onDeleteDocument = {},
                onFileSelected = {},
                onLoadSampleDocument = onLoadSample,
                onOpenPasteText = {}
            )
        }
    }

    @Test
    fun `import entry point is labelled Copy Or Upload everywhere`() {
        setLibrary()

        // FAB label
        composeRule.onAllNodesWithText("Copy Or Upload").assertCountEquals(1)

        // Dialog title (same label, centered title added by §3.A)
        composeRule.onNodeWithTag("copy_and_upload_button").performClick()
        composeRule.onAllNodesWithText("Copy Or Upload").assertCountEquals(2)

        composeRule.onNodeWithText("Upload File").assertExists()
        composeRule.onNodeWithText("Paste Text").assertExists()
    }

    @Test
    fun `hindi is not presented as a file format`() {
        setLibrary()

        composeRule.onAllNodes(hasText("HINDI/INDIC", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasText("INDIC", substring = true)).assertCountEquals(0)

        // The four real formats are shown
        listOf("PDF", "TXT", "EPUB", "DOCX").forEach { fmt ->
            composeRule.onNodeWithText(fmt).assertExists()
        }
    }

    @Test
    fun `all eight demo chips exist with format and language labels`() {
        var clickedIndex = -1
        setLibrary(onLoadSample = { clickedIndex = it })

        val descriptors = DemoCatalog.descriptors
        assertEquals(8, descriptors.size)

        // First chip is visible; its label carries format + language
        val first = descriptors.first()
        composeRule.onNodeWithText(first.chipLabel).assertExists()
        composeRule.onNodeWithTag("sample_chip_${first.id}").performClick()
        assertEquals(0, clickedIndex)

        // The LazyRow really contains all eight chips, including the Hindi fixtures
        composeRule.onNodeWithTag("sample_chips_row").performScrollToIndex(descriptors.lastIndex)
        composeRule.onNodeWithTag("sample_chip_${descriptors.last().id}").assertExists()
    }

    @Test
    fun `empty library shows the honest empty state`() {
        setLibrary()
        composeRule.onNodeWithText("No documents in your Library yet").assertExists()
    }
}
