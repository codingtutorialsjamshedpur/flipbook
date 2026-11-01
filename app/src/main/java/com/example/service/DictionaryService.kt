package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DictionaryInfoType(val label: String) {
    MEANING("Meaning"),
    SYNONYMS("Synonyms"),
    ANTONYMS("Antonyms"),
    EXAMPLE("Example Sentence"),
    SYNONYMS_AND_EXAMPLE("Synonyms + Example Sentence"),
    ANTONYMS_AND_EXAMPLE("Antonyms + Example Sentence"),
    MEANING_AND_EXAMPLE("Meaning + Example Sentence"),
    ALL("All (Meaning + Synonyms + Antonyms + Example)")
}

data class DictionaryResult(
    val word: String,
    val infoType: DictionaryInfoType,
    val meaning: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val exampleSentence: String? = null,
    val hindiMeaning: String? = null,
    val source: String,
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
)

object DictionaryService {

    /** Provider endpoint root (no API key required). Overridable for tests. */
    internal var baseUrl: String = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    /** Optional client override, used by tests to point at a local mock server. */
    internal var httpClientOverride: OkHttpClient? = null

    private val defaultHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val httpClient: OkHttpClient get() = httpClientOverride ?: defaultHttpClient

    private val cache = mutableMapOf<String, DictionaryResult>()

    /** Clears caches and overrides; simulates a fresh app start for tests. */
    internal fun resetForTests() {
        cache.clear()
        baseUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/"
        httpClientOverride = null
    }

    /**
     * Look up lexical information (Meaning, Synonyms, Antonyms, Example Sentence).
     * Connects to public Free Dictionary API with built-in lexical fallback.
     */
    suspend fun lookup(
        word: String,
        infoType: DictionaryInfoType,
        preferHindi: Boolean = false
    ): DictionaryResult = withContext(Dispatchers.IO) {
        val cleanWord = word.trim().lowercase()
        if (cleanWord.isBlank()) return@withContext unavailable(cleanWord, infoType, "Empty word")
        val cacheKey = "$cleanWord-${infoType.name}-$preferHindi"
        cache[cacheKey]?.let { return@withContext it }

        // 1. Try public Free Dictionary API
        val onlineResult = queryFreeDictionaryApi(cleanWord, infoType)
        val result = if (onlineResult != null) {
            val hindiEquiv = if (preferHindi) getHindiTranslation(cleanWord) else null
            onlineResult.copy(hindiMeaning = hindiEquiv)
        } else {
            // 2. Fallback to offline built-in lexical database
            getOfflineLexicalResult(cleanWord, infoType, preferHindi)
        }

        cache[cacheKey] = result
        result
    }

    /** Honest result: nothing was found — no meaning/example/Hindi is invented. */
    private fun unavailable(word: String, infoType: DictionaryInfoType, reason: String) = DictionaryResult(
        word = word,
        infoType = infoType,
        meaning = null,
        synonyms = emptyList(),
        antonyms = emptyList(),
        exampleSentence = null,
        hindiMeaning = null,
        source = "Unavailable — $reason"
    )

    private fun queryFreeDictionaryApi(word: String, infoType: DictionaryInfoType): DictionaryResult? {
        if (word.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "$baseUrl$encoded"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return null

            val firstEntry = jsonArray.getJSONObject(0)
            val meaningsArray = firstEntry.optJSONArray("meanings")

            var meaningText: String? = null
            var exampleText: String? = null
            val synonymsList = mutableListOf<String>()
            val antonymsList = mutableListOf<String>()

            if (meaningsArray != null) {
                for (i in 0 until meaningsArray.length()) {
                    val mObj = meaningsArray.getJSONObject(i)
                    val defs = mObj.optJSONArray("definitions")
                    if (defs != null && defs.length() > 0) {
                        for (d in 0 until defs.length()) {
                            val defObj = defs.getJSONObject(d)
                            if (meaningText == null && defObj.has("definition")) {
                                meaningText = defObj.getString("definition")
                            }
                            if (exampleText == null && defObj.has("example")) {
                                exampleText = defObj.getString("example")
                            }
                        }
                    }

                    val syns = mObj.optJSONArray("synonyms")
                    if (syns != null) {
                        for (s in 0 until syns.length()) {
                            val syn = syns.getString(s)
                            if (syn.isNotBlank() && !synonymsList.contains(syn)) {
                                synonymsList.add(syn)
                            }
                        }
                    }

                    val ants = mObj.optJSONArray("antonyms")
                    if (ants != null) {
                        for (a in 0 until ants.length()) {
                            val ant = ants.getString(a)
                            if (ant.isNotBlank() && !antonymsList.contains(ant)) {
                                antonymsList.add(ant)
                            }
                        }
                    }
                }
            }

            DictionaryResult(
                word = word,
                infoType = infoType,
                meaning = meaningText,
                synonyms = synonymsList.take(6),
                antonyms = antonymsList.take(4),
                exampleSentence = exampleText,
                source = "Free Dictionary API (api.dictionaryapi.dev)"
            )
        } catch (e: Exception) {
            Log.w("DictionaryService", "Online dictionary query failed: ${e.message}")
            null
        }
    }

    private fun getOfflineLexicalResult(
        word: String,
        infoType: DictionaryInfoType,
        preferHindi: Boolean
    ): DictionaryResult {
        val entry = offlineWordBook[word]
            ?: return unavailable(word, infoType, "No offline entry for this word and the online dictionary was unreachable")
        val hindi = if (preferHindi) entry.hindi else null

        return DictionaryResult(
            word = word,
            infoType = infoType,
            meaning = entry.meaning,
            synonyms = entry.synonyms,
            antonyms = entry.antonyms,
            exampleSentence = entry.example,
            hindiMeaning = hindi,
            source = "StoryFlip Lexical Database (Offline)"
        )
    }

    /** Returns null when no curated translation exists — never a placeholder string. */
    private fun getHindiTranslation(word: String): String? {
        return hindiTranslations[word]
    }

    private data class OfflineEntry(
        val meaning: String,
        val synonyms: List<String>,
        val antonyms: List<String>,
        val example: String,
        val hindi: String
    )

    private val hindiTranslations = mapOf(
        "endeavor" to "प्रयास / उद्यम (An earnest attempt)",
        "selfish" to "स्वार्थी / खुदगर्ज (Lacking consideration for others)",
        "resilience" to "लचीलापन / सहनशीलता (Capacity to recover quickly)",
        "serene" to "शांत / निर्मल (Calm, peaceful)",
        "courage" to "साहस / वीरता (Ability to face danger)",
        "alchemist" to "कीमियागर / रसवादी (One who transforms base matter)",
        "quantum" to "प्रमात्रा / मूल इकाई (A discrete quantity of energy)",
        "horizon" to "क्षितिज (The apparent boundary line)",
        "solitude" to "एकांत / अकेलापन (The state of being alone peacefully)"
    )

    private val offlineWordBook = mapOf(
        "endeavor" to OfflineEntry(
            meaning = "An earnest, prolonged, and purposeful effort to achieve a goal.",
            synonyms = listOf("strive", "venture", "undertake", "aspire", "pursue"),
            antonyms = listOf("abandon", "idle", "neglect", "surrender"),
            example = "She will endeavor to master the ancient manuscript before winter.",
            hindi = "प्रयास / उद्यम"
        ),
        "selfish" to OfflineEntry(
            meaning = "Lacking consideration for other people; concerned chiefly with one's own personal profit or pleasure.",
            synonyms = listOf("egocentric", "self-centered", "narcissistic", "greedy"),
            antonyms = listOf("altruistic", "generous", "unselfish", "benevolent"),
            example = "His selfish motives blinded him to the needs of his comrades.",
            hindi = "स्वार्थी / खुदगर्ज"
        ),
        "resilience" to OfflineEntry(
            meaning = "The capacity to withstand or recover quickly from difficulties; psychological toughness.",
            synonyms = listOf("fortitude", "endurance", "grit", "adaptability"),
            antonyms = listOf("fragility", "vulnerability", "weakness"),
            example = "The people of the besieged valley displayed extraordinary resilience.",
            hindi = "लचीलापन / सहनशक्ति"
        ),
        "serene" to OfflineEntry(
            meaning = "Calm, peaceful, and untroubled; tranquil.",
            synonyms = listOf("tranquil", "placid", "peaceful", "composed"),
            antonyms = listOf("turbulent", "agitated", "stormy", "chaotic"),
            example = "The lake remained serene beneath the moonlight.",
            hindi = "शांत / निर्मल"
        ),
        "courage" to OfflineEntry(
            meaning = "Strength in the face of pain, grief, or danger.",
            synonyms = listOf("bravery", "valor", "fearlessness", "intrepidity"),
            antonyms = listOf("cowardice", "timidity", "fearfulness"),
            example = "It took immense courage to speak the truth in the royal court.",
            hindi = "साहस / हिम्मत"
        )
    )
}
