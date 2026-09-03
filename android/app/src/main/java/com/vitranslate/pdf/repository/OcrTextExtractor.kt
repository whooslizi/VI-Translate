package com.vitranslate.pdf.repository

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.tasks.await
import kotlin.math.max

object OcrTextExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Renders a scanned PDF page into a bitmap and runs Google ML Kit Text Recognition offline.
     * Converts recognized OCR bounding boxes into TextBlock runs.
     */
    suspend fun extractOcrTextBlocks(
        document: PDDocument,
        page: PDPage,
        pageIndex: Int,
        renderDpi: Float = 200f
    ): List<PageTextCollector.TextBlock> {
        val renderer = PDFRenderer(document)
        val scale = renderDpi / 72f
        val bitmap: Bitmap = try {
            renderer.renderImage(pageIndex, scale)
        } catch (_: Exception) {
            return emptyList()
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result: Text = try {
            recognizer.process(inputImage).await()
        } catch (_: Exception) {
            return emptyList()
        }

        val textBlocks = mutableListOf<PageTextCollector.TextBlock>()
        val pageWidth = page.cropBox.width
        val pageHeight = page.cropBox.height
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()

        val scaleX = pageWidth / bitmapWidth
        val scaleY = pageHeight / bitmapHeight

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineBox = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isBlank()) continue

                // Convert bitmap pixel coordinates to PDF point coordinates (PDF Y coordinates count up from bottom)
                val pdfX = lineBox.left * scaleX
                val pdfY = pageHeight - (lineBox.bottom * scaleY)
                val pdfWidth = max(1f, lineBox.width() * scaleX)
                val pdfHeight = max(6f, lineBox.height() * scaleY)

                val fontSize = max(8f, pdfHeight * 0.75f)

                textBlocks.add(
                    PageTextCollector.TextBlock(
                        x = pdfX,
                        y = pdfY,
                        width = pdfWidth,
                        height = pdfHeight,
                        text = text,
                        fontSize = fontSize,
                        fontName = "OCR",
                        isBold = false,
                        isItalic = false,
                        colorRgb = intArrayOf(0, 0, 0)
                    )
                )
            }
        }

        return textBlocks
    }
}
