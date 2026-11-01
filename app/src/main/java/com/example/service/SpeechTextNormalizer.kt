package com.example.service

/**
 * Unified Speech-Text Normalizer for TTS.
 *
 * Protects the original visible document: visible text remains completely unchanged.
 * Normalizes only the text passed to Text-to-Speech:
 * - Strips decorative and formatting symbols (markdown, asterisks, underscores, decorative dashes).
 * - Normalizes arithmetic and ratio operators contextually:
 *     "5/10" -> "5 out of 10"
 *     "5\10" -> "5 out of 10"
 *     "5÷10" -> "5 divided by 10"
 *     "5 + 2" -> "5 plus 2"
 *     "5 - 2" or "5 − 2" -> "5 minus 2"
 *     "-5" or "−5" -> "negative 5"
 *     "5–10" or "5-10" (range) -> "5 to 10"
 *     "cat-mouse" -> "cat mouse" (no "dash" pronounced)
 *
 * Every kept spoken token maps back to the index of the original source word it came
 * from, so karaoke highlighting and word seeking never shift indexes unpredictably.
 */
object SpeechTextNormalizer {

    data class NormalizedSpeech(
        val spokenText: String,
        val sourceWordMap: List<SourceWordMapping>
    )

    data class SourceWordMapping(
        val sourceWordIndex: Int,
        val originalWord: String,
        val spokenWord: String,
        val spokenCharStart: Int,
        val spokenCharEnd: Int
    )

    /**
     * Plan describing how a list of source words becomes spoken tokens.
     * [spokenTokenToSourceIndex] has exactly one entry per kept spoken token and holds
     * the index of the source word (in the ORIGINAL page word list) that produced it.
     */
    data class SpeechPlan(
        val spokenText: String,
        val spokenTokens: List<String>,
        val spokenTokenToSourceIndex: List<Int>
    )

    fun normalize(text: String): NormalizedSpeech {
        if (text.isBlank()) {
            return NormalizedSpeech("", emptyList())
        }

        val originalWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val plan = planSpeech(originalWords, 0)
        val mappings = mutableListOf<SourceWordMapping>()

        var cursor = 0
        for ((tokenIdx, token) in plan.spokenTokens.withIndex()) {
            val start = cursor
            if (tokenIdx > 0) {
                cursor++ // account for the joining space
            }
            val tokenStart = cursor
            cursor += token.length
            val tokenEnd = cursor
            mappings.add(
                SourceWordMapping(
                    sourceWordIndex = plan.spokenTokenToSourceIndex[tokenIdx],
                    originalWord = originalWords.getOrElse(plan.spokenTokenToSourceIndex[tokenIdx]) { token },
                    spokenWord = token,
                    spokenCharStart = tokenStart,
                    spokenCharEnd = tokenEnd
                )
            )
        }

        return NormalizedSpeech(plan.spokenText, mappings)
    }

    /**
     * Builds a speech plan for [sourceWords] where index 0 of [sourceWords] corresponds
     * to source word index [baseSourceIndex] within the full page word list.
     */
    fun planSpeech(sourceWords: List<String>, baseSourceIndex: Int): SpeechPlan {
        if (sourceWords.isEmpty()) {
            return SpeechPlan("", emptyList(), emptyList())
        }

        // Pass 1: context-aware joint pass for standalone operator tokens that sit
        // between two numeric tokens ("5 + 2", "5 / 10", "5 - 2"). Runs on the RAW
        // tokens first so standalone "-", "–", "/", "÷" are seen as operators before
        // the per-token pass can blank them as decorative characters.
        val joint = resolveStandaloneOperators(sourceWords)

        // Pass 2: normalize each token individually (strip markdown/decoration, fold
        // in-token numeric operators, decode negative numbers, etc.).
        val perToken = joint.map { normalizeSingleToken(it.text) }

        val keptTokens = mutableListOf<String>()
        val keptSourceIndexes = mutableListOf<Int>()
        for ((idx, token) in perToken.withIndex()) {
            if (token.isNotBlank()) {
                keptTokens.add(token)
                keptSourceIndexes.add(baseSourceIndex + joint[idx].ownerIndex)
            }
        }

        return SpeechPlan(
            spokenText = keptTokens.joinToString(" "),
            spokenTokens = keptTokens,
            spokenTokenToSourceIndex = keptSourceIndexes
        )
    }

    /**
     * A spoken token paired with the index of the SOURCE word it should highlight.
     * When operators merge, the merged phrase owns the LEFT operand's index so
     * karaoke highlights a real word instead of the operator glyph.
     */
    private data class JointToken(val text: String, val ownerIndex: Int)

    /**
     * Resolves standalone operator tokens ("+", "-", "−", "/", "\\", "÷", "–") that are
     * sandwiched between numeric tokens into spoken phrases, using context:
     * - "/" "\\" between numbers -> "out of" (ratio default)
     * - "÷" -> "divided by"
     * - "+" -> "plus"
     * - "-"/"−" -> "minus" when the right number is smaller or equal, "to" for ranges
     * - "–" (en dash) -> "to" (range)
     * Operators NOT between two numbers are left to per-token normalization.
     */
    private fun resolveStandaloneOperators(tokens: List<String>): List<JointToken> {
        if (tokens.size < 3) return tokens.mapIndexed { i, t -> JointToken(t, i) }
        val result = tokens.toMutableList()
        val owner = tokens.indices.toMutableList()

        fun isNumeric(tok: String) = tok.isNotEmpty() &&
            tok.all { it.isDigit() || it == '.' } &&
            tok.any { it.isDigit() }

        for (i in 1 until tokens.size - 1) {
            val op = result[i]
            val left = result[i - 1]
            val right = result[i + 1]
            if (left.isBlank() || right.isBlank()) continue
            if (!isNumeric(left) || !isNumeric(right)) continue

            val replacement = when (op) {
                "/" -> "$left out of $right"
                "\\" -> "$left out of $right"
                "⁄" -> "$left out of $right" // fraction slash
                "÷" -> "$left divided by $right"
                "+" -> "$left plus $right"
                "-", "−" -> {
                    val n1 = left.toDoubleOrNull()
                    val n2 = right.toDoubleOrNull()
                    if (n1 != null && n2 != null && n2 > n1) "$left to $right" else "$left minus $right"
                }
                "–", "—" -> "$left to $right"
                else -> null
            }

            if (replacement != null) {
                result[i] = replacement
                owner[i] = i - 1
                // Neutralize neighbours so they are not processed again as operators;
                // they stay numeric and are merged into this token's phrase.
                result[i - 1] = ""
                result[i + 1] = ""
            }
        }

        // Drop emptied neighbour slots (filtered again after per-token normalization).
        return result.mapIndexedNotNull { idx, text ->
            if (text.isNotBlank()) JointToken(text, owner[idx]) else null
        }
    }

    /**
     * Normalizes a single token/word contextually.
     */
    fun normalizeSingleToken(raw: String): String {
        var str = raw.trim()
        if (str.isEmpty()) return ""

        // 1. Remove markdown markers: **word**, *word*, __word__, _word_, `word`
        str = str.replace(Regex("^\\*\\*(.*?)\\*\\*$"), "$1")
        str = str.replace(Regex("^\\*(.*?)\\*$"), "$1")
        str = str.replace(Regex("^__(.*?)__$"), "$1")
        str = str.replace(Regex("^_(.*?)_$"), "$1")
        str = str.replace(Regex("^`(.*?)`$"), "$1")

        // 2. Decorative bullet points & formatting characters (whole-token only)
        if (str.matches(Regex("^[•·*~#\\-–—_]+$"))) {
            return ""
        }

        // 3. Negative numbers: -5 or −5 -> "negative 5"
        if (str.matches(Regex("^[-−]\\d+(\\.\\d+)?$"))) {
            return "negative " + str.substring(1)
        }

        // 4. Numeric operators & expressions inside a single token
        // Ratios: 5/10 or 5\10 -> "5 out of 10"
        if (str.contains("/") || str.contains("\\")) {
            str = str.replace(Regex("(\\d+)[/\\\\⁄](\\d+)"), "$1 out of $2")
        }

        // Division: 5÷10 -> "5 divided by 10"
        if (str.contains("÷")) {
            str = str.replace(Regex("(\\d+)÷(\\d+)"), "$1 divided by $2")
        }

        // Addition: 5+2 -> "5 plus 2"
        if (str.contains("+")) {
            str = str.replace(Regex("(\\d+)\\+(\\d+)"), "$1 plus $2")
        }

        // En-dash / em-dash numeric range: 5–10 -> "5 to 10"
        str = str.replace(Regex("(\\d+)[–—](\\d+)"), "$1 to $2")

        // Minus / subtraction vs hyphen range between numbers:
        if (str.contains("-") || str.contains("−")) {
            val hyphenMatch = Regex("(\\d+)[-−](\\d+)").find(str)
            if (hyphenMatch != null) {
                val n1 = hyphenMatch.groupValues[1].toIntOrNull()
                val n2 = hyphenMatch.groupValues[2].toIntOrNull()
                if (n1 != null && n2 != null) {
                    if (n2 > n1) {
                        str = str.replace(hyphenMatch.value, "$n1 to $n2")
                    } else {
                        str = str.replace(hyphenMatch.value, "$n1 minus $n2")
                    }
                }
            }
        }

        // 5. Hyphenated word separators: cat-mouse -> "cat mouse" (no "dash")
        if (str.matches(Regex("^[a-zA-Z]+[-–][a-zA-Z]+$"))) {
            str = str.replace(Regex("[-–]"), " ")
        }

        // 6. Clean out stray decorative symbols while preserving letters, numbers,
        // standard sentence punctuation and meaningful operators.
        str = str.replace(Regex("[*#_~`^|\\[\\]{}<>]"), "")

        return str.trim()
    }
}
