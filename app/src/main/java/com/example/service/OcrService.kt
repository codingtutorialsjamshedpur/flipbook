package com.example.service

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.TextBlockInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of a real on-device ML Kit OCR pass on a single page bitmap.
 */
data class OcrPageResult(
    val text: String,
    val blocks: List<TextBlockInfo>,
    val averageConfidence: Float,
    val engine: String,
    val languages: String
) {
    /** Rough count of alphanumerics — used to judge whether the page has usable text. */
    val usableCharCount: Int get() = text.count { it.isLetterOrDigit() }
}

/**
 * Real on-device OCR using Google ML Kit Text Recognition (bundled models).
 * Devanagari recognizer (Hindi/Marathi) with Latin-script fallback.
 *
 * ML Kit requires Google Play services on-device; when unavailable (e.g. unit tests,
 * devices without Play services) callers receive a typed failure and must surface an
 * honest "OCR unavailable" state instead of fabricating text.
 */
object OcrService {

    /** Minimum alphanumerics considered "sufficient" text for a page. */
    const val MIN_USABLE_CHARS = 10

    private const val TAG = "OcrService"

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val devanagariRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    fun isNativeTextUsable(text: String?): Boolean {
        val count = text?.count { it.isLetterOrDigit() } ?: 0
        return count >= MIN_USABLE_CHARS
    }

    /**
     * Runs OCR on [bitmap]. Attempts the Devanagari recognizer first when [preferIndic]
     * is true (it also covers Latin digits/letters mixed with Hindi), then falls back to
     * the Latin recognizer.
     * Throws [OcrUnavailableException] when the OCR engine cannot run.
     */
    suspend fun recognize(bitmap: Bitmap, preferIndic: Boolean = true): OcrPageResult {
        val image = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (e: Exception) {
            throw OcrUnavailableException("Could not prepare image for OCR: ${e.message}")
        }

        val visionText: Text = try {
            if (preferIndic) {
                try {
                    process(devanagariRecognizer, image)
                } catch (e: Exception) {
                    Log.w(TAG, "Devanagari OCR failed, falling back to Latin: ${e.message}")
                    process(latinRecognizer, image)
                }
            } else {
                process(latinRecognizer, image)
            }
        } catch (e: Exception) {
            Log.w(TAG, "OCR engines unavailable: ${e.message}")
            throw OcrUnavailableException("OCR engine unavailable: ${e.message}")
        }

        val blocks = mutableListOf<TextBlockInfo>()
        val fullText = visionText.text
        var charCursor = 0
        var confidenceSum = 0f
        var confidenceCount = 0

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val elText = element.text
                    if (elText.isBlank()) continue
                    val box = element.boundingBox
                    val start = fullText.indexOf(elText, charCursor)
                    val wordStart = if (start >= 0) start else charCursor
                    val wordEnd = wordStart + elText.length
                    charCursor = wordEnd

                    val conf = element.confidence
                    if (!conf.isNaN()) {
                        confidenceSum += conf
                        confidenceCount++
                    }

                    blocks.add(
                        TextBlockInfo(
                            text = elText,
                            wordStartChar = wordStart,
                            wordEndChar = wordEnd,
                            leftFraction = (box?.left ?: 0).toFloat(),
                            topFraction = (box?.top ?: 0).toFloat(),
                            rightFraction = (box?.right ?: 0).toFloat(),
                            bottomFraction = (box?.bottom ?: 0).toFloat(),
                            confidence = if (conf.isNaN()) 0.5f else conf
                        )
                    )
                }
            }
        }

        return OcrPageResult(
            text = fullText.trim(),
            blocks = blocks,
            averageConfidence = if (confidenceCount > 0) confidenceSum / confidenceCount else 0f,
            engine = "Google ML Kit Text Recognition (on-device)",
            languages = if (preferIndic) "Latin + Devanagari (Hindi)" else "Latin (English)"
        )
    }

    private suspend fun process(recognizer: TextRecognizer, image: InputImage): Text {
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    fun shutdown() {
        try { latinRecognizer.close() } catch (_: Exception) {}
        try { devanagariRecognizer.close() } catch (_: Exception) {}
    }

    class OcrUnavailableException(message: String) : Exception(message)
}
