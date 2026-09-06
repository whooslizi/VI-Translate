package com.vitranslate.pdf.repository

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrTextLine(
    val text: String,
    val boundingBox: RectF? = null,
    val confidence: Float = 1.0f
)

data class OcrResult(
    val fullText: String,
    val lines: List<OcrTextLine>
)

object OcrEngine {
    private const val TAG = "OcrEngine"

    /**
     * Extracts text from a bitmap image using ML Kit Text Recognition with fail-safe error handling.
     * If OCR processing fails or ML Kit is unavailable on device, returns a successful Result with an empty OcrResult.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): Result<OcrResult> {
        return runCatching {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                val visionText: Text = suspendCancellableCoroutine { continuation ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { text ->
                            if (continuation.isActive) continuation.resume(text)
                        }
                        .addOnFailureListener { exception ->
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                }

                val lineList = mutableListOf<OcrTextLine>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val rect = line.boundingBox?.let { RectF(it) }
                        lineList.add(OcrTextLine(text = line.text, boundingBox = rect))
                    }
                }
                OcrResult(fullText = visionText.text, lines = lineList)
            } catch (e: Throwable) {
                Log.w(TAG, "OCR recognition failed or ML Kit unavailable on device: ${e.message}", e)
                OcrResult(fullText = "", lines = emptyList())
            }
        }
    }
}
