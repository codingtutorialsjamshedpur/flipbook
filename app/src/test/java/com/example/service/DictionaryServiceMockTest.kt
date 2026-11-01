package com.example.service

import kotlinx.coroutines.test.runTest
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
 * Addendum §6 / §11: dictionary lookups against a local mock provider — real data is
 * parsed, failures degrade to an honest, labeled "Unavailable" result (or the labeled
 * offline fallback), and identical lookups are served from cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionaryServiceMockTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        DictionaryService.resetForTests()
        server = MockWebServer()
        server.start()
        DictionaryService.baseUrl = server.url("/api/v2/entries/en/").toString()
        DictionaryService.httpClientOverride = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        DictionaryService.resetForTests()
        server.shutdown()
    }

    @Test
    fun `successful lookup parses meaning synonyms antonyms and example`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "word": "alphaword",
                  "meanings": [{
                    "definitions": [{"definition": "The first test word.", "example": "Alpha came first."}],
                    "synonyms": ["beta", "gamma"],
                    "antonyms": ["omega"]
                  }]
                }]
                """.trimIndent()
            )
        )

        val result = DictionaryService.lookup("alphaword", DictionaryInfoType.ALL)

        assertEquals("The first test word.", result.meaning)
        assertEquals(listOf("beta", "gamma"), result.synonyms)
        assertEquals(listOf("omega"), result.antonyms)
        assertEquals("Alpha came first.", result.exampleSentence)
        assertTrue("source must name the real provider", result.source.startsWith("Free Dictionary API"))

        val request = server.takeRequest()
        assertEquals("/api/v2/entries/en/alphaword", request.path)
    }

    @Test
    fun `404 with no offline entry yields an honest unavailable result`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"title":"No Definitions Found"}"""))

        val result = DictionaryService.lookup("zzqword", DictionaryInfoType.ALL)

        assertNull(result.meaning)
        assertTrue(result.synonyms.isEmpty())
        assertTrue(result.antonyms.isEmpty())
        assertNull(result.exampleSentence)
        assertNull(result.hindiMeaning)
        assertTrue(
            "failure must be labeled honestly, got: ${result.source}",
            result.source.startsWith("Unavailable —")
        )
    }

    @Test
    fun `malformed provider response degrades honestly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json at all"))

        val result = DictionaryService.lookup("zzqword", DictionaryInfoType.MEANING)

        assertNull(result.meaning)
        assertTrue(result.source.startsWith("Unavailable —"))
    }

    @Test
    fun `provider outage falls back to the labeled offline database for known words`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = DictionaryService.lookup("serene", DictionaryInfoType.ALL)

        assertEquals("StoryFlip Lexical Database (Offline)", result.source)
        assertNotNull(result.meaning)
        assertTrue(result.synonyms.isNotEmpty())
    }

    @Test
    fun `prefer hindi returns a curated translation only when one exists`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val withHindi = DictionaryService.lookup("serene", DictionaryInfoType.ALL, preferHindi = true)
        assertNotNull("serene has a curated Hindi translation", withHindi.hindiMeaning)

        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val withoutHindi = DictionaryService.lookup("zzqword", DictionaryInfoType.ALL, preferHindi = true)
        assertNull("no invented Hindi for unknown words", withoutHindi.hindiMeaning)
        assertTrue(withoutHindi.source.startsWith("Unavailable —"))
    }

    @Test
    fun `identical repeated lookups are served from cache without a second request`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"word":"cacheword","meanings":[{"definitions":[{"definition":"Cached definition."}]}]}]"""
            )
        )

        val first = DictionaryService.lookup("cacheword", DictionaryInfoType.MEANING)
        val second = DictionaryService.lookup("cacheword", DictionaryInfoType.MEANING)

        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }
}
