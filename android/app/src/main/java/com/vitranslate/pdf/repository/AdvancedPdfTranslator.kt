package com.vitranslate.pdf.repository

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.regex.Pattern

internal val DESKTOP_FORMULA_FONT_PATTERN = Pattern.compile(
    "(CM[^R]|MS.M|XY|MT|BL|RM|EU|LA|RS|LINE|LCIRCLE|TeX-|rsfs|txsy|wasy|" +
    "stmary|.*Mono|.*Code|.*Sym|.*Math|.*Typewriter|Cousine|Consolas|Menlo|" +
    "Monaco|Inconsolata|Source.?Code|Fira.?Code|DejaVu.?Sans.?Mono|" +
    "Liberation.?Mono|Courier)",
    Pattern.CASE_INSENSITIVE
)

internal val DESKTOP_MATH_SYMBOL_PATTERN = Pattern.compile(
    "[=≤≥≈≠±×÷·∑∫√∞∝+*/^]|\\\\log|\\\\lim|\\\\sin|\\\\cos|\\\\tan|\\\\cot|\\\\frac|\\\\sqrt"
)

object AdvancedPdfTranslator {

    fun translatePdfAdvanced(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetLang: String,
        pageSelectionInput: String = "all",
        customEngine: TranslateEngine? = null,
        onProgress: (donePages: Int, totalPages: Int, logMsg: String) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean
    ): Result<String> {
        val engine = customEngine ?: GoogleTranslateEngine(sourceLang = "auto", targetLang = targetLang)
        var inputStream: InputStream? = null
        var document: PDDocument? = null

        return try {
            inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return Result.failure(Exception("Cannot open input PDF stream"))
            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            val selectedPages = PageSelectionParser.parsePageSelection(pageSelectionInput, totalPages)
            val selectedSet = selectedPages.toSet()

            onLog("Trình dịch Nâng cao (PyMuPDF / Desktop 1:1 Mode) khởi chạy. Tổng số trang: $totalPages. Số trang dịch: ${selectedPages.size}")
            onProgress(0, selectedPages.size, "Đang khởi tạo Trình dịch Nâng cao...")

            val font = PdfLayoutPreserver(context).loadBundledFont(document)
            var donePagesCount = 0

            for (pageIndex in 0 until totalPages) {
                if (isCancelled()) throw TranslationCancelledException()
                val pageNum = pageIndex + 1

                if (!selectedSet.contains(pageNum)) {
                    onLog("Trang $pageNum: Giữ nguyên trang gốc (bỏ qua theo thiết lập).")
                    continue
                }

                val page = document.getPage(pageIndex)
                val pageExtractor = DesktopParagraphExtractor(pageNum)
                pageExtractor.sortByPosition = true
                pageExtractor.startPage = pageNum
                pageExtractor.endPage = pageNum
                pageExtractor.getText(document)

                val paragraphs = pageExtractor.paragraphs
                val translatedParagraphs = mutableListOf<String>()
                var translatedCount = 0
                var skippedFormulaCount = pageExtractor.skippedFormulasCount

                for (paragraph in paragraphs) {
                    if (isCancelled()) throw TranslationCancelledException()
                    val srcText = paragraph.text.trim()
                    if (srcText.isBlank() || paragraph.isPureMath) {
                        translatedParagraphs.add(srcText)
                        continue
                    }

                    // 1. Encode TeX formulas into translator-safe <b0></b0> tags (pdf2zh desktop algorithm: translator.py:183)
                    val (encodedText, placeholders) = encodeFormulaPlaceholders(srcText, paragraph.mathSpans)
                    try {
                        val rawTranslated = engine.translate(encodedText)
                        // 2. Validate placeholder tags (pdf2zh desktop safety rule: translator.py:191)
                        // If tags were altered or corrupted by the translator, return original English verbatim
                        val restoredText = restoreFormulaPlaceholders(srcText, encodedText, rawTranslated, placeholders)
                        translatedParagraphs.add(restoredText)
                        translatedCount++
                    } catch (_: Exception) {
                        translatedParagraphs.add(srcText)
                    }
                }

                // 3. Render translated text using exact matrix baseline positioning (Tm, TJ)
                renderTranslatedPage(document, page, paragraphs, translatedParagraphs, font)
                donePagesCount++

                onLog("Trang $pageNum/$totalPages (Trình dịch Nâng cao Desktop): ${pageExtractor.lineCount} dòng gộp thành ${paragraphs.size} đoạn. Đã dịch: $translatedCount, Bảo tồn công thức: $skippedFormulaCount")
                onProgress(donePagesCount, selectedPages.size, "Đã xử lý trang $pageNum/$totalPages")
            }

            val fos = FileOutputStream(outputFile)
            document.save(fos)
            fos.close()
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { document?.close() }
            runCatching { inputStream?.close() }
        }
    }

    private fun encodeFormulaPlaceholders(sourceText: String, mathSpans: List<String>): Pair<String, List<String>> {
        var result = sourceText
        val placeholders = mutableListOf<String>()
        mathSpans.forEachIndexed { index, tag ->
            if (result.contains(tag)) {
                val tagMarker = "<b$index></b$index>"
                result = result.replace(tag, tagMarker)
                placeholders.add(tag)
            }
        }
        return Pair(result, placeholders)
    }

    private fun restoreFormulaPlaceholders(
        source: String,
        encodedSource: String,
        translated: String,
        placeholders: List<String>
    ): String {
        // Validate placeholder counts match expected tags (pdf2zh desktop safety rule: translator.py:194)
        val expectedTags = placeholders.indices.map { "<b$it></b$it>" }
        val allTagsPresent = expectedTags.all { translated.contains(it) }

        if (!allTagsPresent) {
            // Tag restoration failed or altered during translation -> return source verbatim to prevent tag leaks or math distortions
            return source
        }

        var result = translated
        placeholders.forEachIndexed { index, originalTag ->
            result = result.replace("<b$index></b$index>", originalTag)
        }
        return result
    }

    private fun renderTranslatedPage(
        doc: PDDocument,
        page: PDPage,
        paragraphs: List<DesktopParagraph>,
        translatedTexts: List<String>,
        font: PDFont
    ) {
        if (paragraphs.isEmpty()) return
        val pageHeight = page.mediaBox.height

        val stream = PDPageContentStream(
            doc,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        )

        for (i in paragraphs.indices) {
            val paragraph = paragraphs[i]
            val translated = translatedTexts.getOrNull(i) ?: paragraph.text
            if (translated.isBlank() || paragraph.isPureMath) continue

            // White mask padding
            val padX = 0.5f
            val padY = 0.2f
            val rectX = (paragraph.x0 - padX).coerceAtLeast(0f)
            val rectY = (pageHeight - paragraph.y1 - padY).coerceAtLeast(0f)
            val rectW = paragraph.width + (padX * 2)
            val rectH = paragraph.height + (padY * 2)

            stream.setNonStrokingColor(255, 255, 255)
            stream.addRect(rectX, rectY, rectW, rectH)
            stream.fill()

            stream.beginText()
            stream.setFont(font, paragraph.fontSize)
            stream.setNonStrokingColor(0, 0, 0)
            stream.newLineAtOffset(paragraph.x0, pageHeight - paragraph.y0 - paragraph.fontSize)
            stream.showText(translated)
            stream.endText()
        }

        stream.close()
    }
}

data class DesktopParagraph(
    val text: String,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val fontSize: Float,
    val isPureMath: Boolean,
    val mathSpans: List<String>
) {
    val width: Float get() = (x1 - x0).coerceAtLeast(1f)
    val height: Float get() = (y1 - y0).coerceAtLeast(1f)
}

class DesktopParagraphExtractor(private val pageNum: Int) : PDFTextStripper() {
    val paragraphs = mutableListOf<DesktopParagraph>()
    var lineCount = 0
    var skippedFormulasCount = 0

    private var currentParagraphText = StringBuilder()
    private var pX0 = Float.MAX_VALUE
    private var pY0 = Float.MAX_VALUE
    private var pX1 = Float.MIN_VALUE
    private var pY1 = Float.MIN_VALUE
    private var pFontSize = 10f
    private val pMathSpans = mutableListOf<String>()

    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        if (textPositions.isNullOrEmpty()) return
        lineCount++

        val first = textPositions.first()
        val last = textPositions.last()
        val lineText = text ?: ""

        val isMathFont = textPositions.any { pos ->
            val fontName = pos.font?.name ?: ""
            DESKTOP_FORMULA_FONT_PATTERN.matcher(fontName).find()
        }
        val hasMathSymbols = DESKTOP_MATH_SYMBOL_PATTERN.matcher(lineText).find()
        val isMathLine = isMathFont || hasMathSymbols

        if (isMathLine) {
            skippedFormulasCount++
            pMathSpans.add(lineText)
        }

        // Paragraph Assembly: Merge continuous lines into the same paragraph block (pdf2zh converter.py:920)
        if (currentParagraphText.isNotEmpty()) {
            val yGap = first.yDirAdj - pY1
            // If line is close vertically and in same column block, merge into continuous paragraph string
            if (yGap < first.heightDir * 2.0f && first.xDirAdj >= (pX0 - 15f)) {
                currentParagraphText.append(" ").append(lineText)
                pX0 = minOf(pX0, first.xDirAdj)
                pY0 = minOf(pY0, first.yDirAdj)
                pX1 = maxOf(pX1, last.xDirAdj + last.widthDirAdj)
                pY1 = maxOf(pY1, first.yDirAdj + first.heightDir)
                return
            } else {
                flushParagraph()
            }
        }

        currentParagraphText.append(lineText)
        pX0 = first.xDirAdj
        pY0 = first.yDirAdj
        pX1 = last.xDirAdj + last.widthDirAdj
        pY1 = first.yDirAdj + first.heightDir
        pFontSize = first.fontSizeInPt
    }

    override fun writePage() {
        super.writePage()
        flushParagraph()
    }

    private fun flushParagraph() {
        if (currentParagraphText.isEmpty()) return
        val fullText = currentParagraphText.toString().trim()
        val isPureMath = fullText.startsWith("A.") || fullText.startsWith("B.") || fullText.startsWith("C.") || fullText.startsWith("D.") || fullText.length < 12

        paragraphs.add(
            DesktopParagraph(
                text = fullText,
                x0 = pX0,
                y0 = pY0,
                x1 = pX1,
                y1 = pY1,
                fontSize = pFontSize,
                isPureMath = isPureMath,
                mathSpans = pMathSpans.toList()
            )
        )

        currentParagraphText.clear()
        pX0 = Float.MAX_VALUE
        pY0 = Float.MAX_VALUE
        pX1 = Float.MIN_VALUE
        pY1 = Float.MIN_VALUE
        pMathSpans.clear()
    }
}
