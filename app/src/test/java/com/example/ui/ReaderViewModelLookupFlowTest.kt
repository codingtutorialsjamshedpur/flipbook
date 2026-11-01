package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.service.DictionaryInfoType
import com.example.service.DictionaryService
import com.example.service.VocabularyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Addendum §6 / §11 (#16, #17): the dictionary lookup flow of the real ViewModel —
 * partial success persists, word association stays stable across lookups, and failures
 * are recorded honestly without discarding successful results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderViewModelLookupFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        VocabularyManager.clearCacheForTests()
        DictionaryService.resetForTests()

        server = MockWebServer()
        server.start()
        DictionaryService.baseUrl = server.url("/api/v2/entries/en/").toString()
        DictionaryService.httpClientOverride = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = ReaderViewModel(app)
    }

    @After
    fun tearDown() {
        server.shutdown()
        DictionaryService.resetForTests()
        Dispatchers.resetMain()
    }

    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `lookup persists partial success and keeps stable word association`() {
        viewModel.createBookFromPastedText(
            title = "VM Lookup Book",
            author = "Tester",
            text = "The serene lake mirrored the sky.\nA zzqword appears in this line."
        )
        waitUntil { viewModel.activeDocument.value?.title == "VM Lookup Book" }

        viewModel.saveWordToVocabulary(1, "serene")
        viewModel.saveWordToVocabulary(1, "zzqword")
        waitUntil { viewModel.vocabularyEntries.value.size == 2 }

        val idsBefore = viewModel.vocabularyEntries.value.map { it.id }.toSet()

        // Provider: first word succeeds, second has no entry anywhere
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"word":"serene","meanings":[{"definitions":[{"definition":"Calm and untroubled."}]}]}]"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"title":"No Definitions Found"}"""))

        viewModel.lookupVocabularyEntries(idsBefore, DictionaryInfoType.MEANING, preferHindi = false)
        waitUntil {
            !viewModel.isLookingUpDictionary.value &&
                viewModel.lookupStatusText.value.contains("word(s)")
        }

        val byWord = viewModel.vocabularyEntries.value.associateBy { it.word }

        // Same entries (same ids) — lookup must not recreate or reorder words (#17)
        assertEquals(idsBefore, viewModel.vocabularyEntries.value.map { it.id }.toSet())

        val serene = byWord.getValue("serene")
        assertEquals("Calm and untroubled.", serene.meaning)
        assertTrue(serene.lookupSource!!.startsWith("Free Dictionary API"))
        assertNotNull(serene.lookupTimestamp)

        // The failed word records an honest failure and keeps no invented data
        val missing = byWord.getValue("zzqword")
        assertNull(missing.meaning)
        assertTrue(missing.lookupSource.orEmpty().startsWith("Unavailable —"))

        assertEquals(
            "1 word(s) updated; 1 word(s) unavailable. Existing notes were kept.",
            viewModel.lookupStatusText.value
        )

        // Persisted result is visible after a simulated restart
        VocabularyManager.clearCacheForTests()
        val doc = viewModel.activeDocument.value!!
        val reloaded = VocabularyManager.getEntries(
            ApplicationProvider.getApplicationContext(),
            doc.id,
            doc.title
        ).associateBy { it.word }
        assertEquals("Calm and untroubled.", reloaded.getValue("serene").meaning)
    }
}
