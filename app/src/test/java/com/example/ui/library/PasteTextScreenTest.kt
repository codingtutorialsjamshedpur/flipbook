package com.example.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.service.DocumentParserService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Addendum §5 / §11: the paste screen accepts an optional author and passes it through;
 * the parser applies an honest fallback when the author is left blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasteTextScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `create book passes the optional author through`() {
        var captured: Triple<String, String, String>? = null
        composeRule.setContent {
            PasteTextScreen(
                onCreateBook = { title, author, text -> captured = Triple(title, author, text) },
                onBackToLibrary = {}
            )
        }

        composeRule.onNodeWithTag("paste_title_input").performTextInput("My Poem Collection")
        composeRule.onNodeWithTag("paste_author_input").performTextInput("Munshi Premchand")
        composeRule.onNodeWithTag("paste_text_input").performTextInput("Some pasted text for the book body.")
        composeRule.onNodeWithTag("create_book_from_paste_button").performClick()

        val result = captured
        assertEquals("My Poem Collection", result?.first)
        assertEquals("Munshi Premchand", result?.second)
        assertEquals("Some pasted text for the book body.", result?.third)
    }

    @Test
    fun `blank author is passed through as empty and the parser applies an honest fallback`() {
        var captured: Triple<String, String, String>? = null
        composeRule.setContent {
            PasteTextScreen(
                onCreateBook = { title, author, text -> captured = Triple(title, author, text) },
                onBackToLibrary = {}
            )
        }

        composeRule.onNodeWithTag("paste_text_input").performTextInput("Body only, no author given.")
        composeRule.onNodeWithTag("create_book_from_paste_button").performClick()

        assertEquals("", captured?.second)

        val doc = DocumentParserService.createDocumentFromText(
            title = "Untitled Paste",
            author = captured?.second ?: "",
            content = "Body only, no author given.",
            format = com.example.data.model.DocumentFormat.TXT
        )
        assertEquals("Unknown Author", doc.author)
        assertTrue(doc.rawContent.contains("Body only"))
    }
}
