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

object AdvancedPdfTranslator {

    private val FORMULA_FONT_PATTERN = Pattern.compile(
        "(CM[^R]|MS.M|XY|MT|BL|RM|EU|LA|RS|LINE|LCIRCLE|TeX-|rsfs|txsy|wasy|" +
        "stmary|.*Mono|.*Code|.*Sym|.*Math|.*Typewriter|Cousine|Consolas|Menlo|" +
        "Monaco|Inconsolata|Source.?Code|Fira.?Code|DejaVu.?Sans.?Mono|" +
        "Liberation.?Mono|Courier)",
        Pattern.CASE_INSENSITIVE
    )

    private val MATH_SYMBOL_PATTERN = Pattern.compile(
        "[=≤≥≈≠±×÷·∑∫√∞∝+*/^]|\\\\log|\\\\lim|\\\\sin|\\\\cos|\\\\tan|\\\\cot|\\\\frac|\\\\sqrt"
    )

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

            onLog("Trình dịch Nâng cao (Windows pdf2zh Technique) khởi chạy. Tổng số trang: $totalPages. Số trang dịch: ${selectedPages.size}")
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
                val pageExtractor = AdvancedLineBlockExtractor(pageNum)
                pageExtractor.sortByPosition = true
                pageExtractor.startPage = pageNum
                pageExtractor.endPage = pageNum
                pageExtractor.getText(document)

                val blocks = pageExtractor.blocks
                val translatedTexts = mutableListOf<String>()
                var translatedCount = 0
                var skippedFormulaCount = pageExtractor.skippedFormulasCount

                for (block in blocks) {
                    if (isCancelled()) throw TranslationCancelledException()
                    val srcText = block.text.trim()
                    if (srcText.isBlank() || block.isPureMath) {
                        translatedTexts.add(srcText)
                        continue
                    }

                    // 1. TeX formula placeholder protection (<b0></b0>)
                    val (encodedText, placeholders) = encodeFormulaPlaceholders(srcText, block.mathSpans)
                    try {
                        val rawTranslated = engine.translate(encodedText)
                        // 2. Validate placeholder tag safety post-translation
                        val restoredText = restoreFormulaPlaceholders(srcText, encodedText, rawTranslated, placeholders)
                        translatedTexts.add(restoredText)
                        translatedCount++
                    } catch (_: Exception) {
                        translatedTexts.add(srcText)
                    }
                }

                // 3. Render translated text with exact white-background masking to erase original English text (pdf2zh technique)
                renderMaskedTranslatedPage(document, page, blocks, translatedTexts, font)
                donePagesCount++

                onLog("Trang $pageNum/$totalPages (Nâng cao Windows Mode): ${pageExtractor.lineCount} dòng -> ${blocks.size} khối text. Đã dịch: $translatedCount, Bảo tồn công thức: $skippedFormulaCount")
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
        val expectedTags = placeholders.indices.map { "<b$it></b$it>" }
        val allTagsPresent = expectedTags.all { translated.contains(it) }

        if (!allTagsPresent) {
            return source
        }

        var result = translated
        placeholders.forEachIndexed { index, originalTag ->
            result = result.replace("<b$index></b$index>", originalTag)
        }
        return result
    }

    private fun renderMaskedTranslatedPage(
        doc: PDDocument,
        page: PDPage,
        blocks: List<AdvancedBlock>,
        translatedTexts: List<String>,
        font: PDFont
    ) {
        if (blocks.isEmpty()) return
        val pageHeight = page.mediaBox.height

        val stream = PDPageContentStream(
            doc,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        )

        for (i in blocks.indices) {
            val block = blocks[i]
            val translated = translatedTexts.getOrNull(i) ?: block.text
            if (translated.isBlank() || block.isPureMath) continue

            val sanitized = sanitizeForFont(translated, font)
            if (sanitized.isBlank()) continue

            // 1. Clean White Mask Bounding Box (pdf2zh technique to erase original text layer completely)
            val padX = 1.0f
            val padY = 1.0f
            val rectX = (block.x0 - padX).coerceAtLeast(0f)
            val rectY = (pageHeight - block.y1 - padY).coerceAtLeast(0f)
            val rectW = (block.width + padX * 2).coerceAtLeast(1f)
            val rectH = (block.height + padY * 2).coerceAtLeast(1f)

            stream.setNonStrokingColor(255, 255, 255)
            stream.addRect(rectX, rectY, rectW, rectH)
            stream.fill()

            // 2. Render Translated Text over clean white mask
            stream.beginText()
            stream.setFont(font, block.fontSize)
            stream.setNonStrokingColor(0, 0, 0)
            stream.newLineAtOffset(block.x0, pageHeight - block.y0 - (block.fontSize * 0.85f))
            stream.showText(sanitized)
            stream.endText()
        }

        stream.close()
    }

    private fun sanitizeForFont(text: String, font: PDFont): String {
        val sb = StringBuilder(text.length)
        for (char in text) {
            if (char == '\n' || char == '\r' || char == '\t') {
                sb.append(' ')
                continue
            }
            if (char.code < 32 || char == '¯' || char == '‾' || char == '\u02C9') continue
            try {
                font.encode(char.toString())
                sb.append(char)
            } catch (_: Exception) {
                when (char) {
                    '⁰' -> sb.append('0')
                    '¹' -> sb.append('1')
                    '²' -> sb.append('2')
                    '³' -> sb.append('3')
                    '⁴' -> sb.append('4')
                    '⁵' -> sb.append('5')
                    '⁶' -> sb.append('6')
                    '⁷' -> sb.append('7')
                    '⁸' -> sb.append('8')
                    '⁹' -> sb.append('9')
                    '⁺' -> sb.append('+')
                    '⁻' -> sb.append('-')
                    '∞' -> sb.append("inf")
                    '≤' -> sb.append("<=")
                    '≥' -> sb.append(">=")
                    '≠' -> sb.append("!=")
                    '±' -> sb.append("+/-")
                    '×' -> sb.append("*")
                    '÷' -> sb.append("/")
                    'π' -> sb.append("pi")
                    'α' -> sb.append("alpha")
                    'β' -> sb.append("beta")
                    'γ' -> sb.append("gamma")
                    'θ' -> sb.append("theta")
                    '₀' -> sb.append('0')
                    '₁' -> sb.append('1')
                    '₂' -> sb.append('2')
                    '₃' -> sb.append('3')
                    '₄' -> sb.append('4')
                    '₅' -> sb.append('5')
                    '₆' -> sb.append('6')
                    '₇' -> sb.append('7')
                    '₈' -> sb.append('8')
                    '₉' -> sb.append('9')
                    '₊' -> sb.append('+')
                    '₋' -> sb.append('-')
                    else -> {}
                }
            }
        }
        return sb.toString()
    }
}

data class AdvancedBlock(
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

class AdvancedLineBlockExtractor(private val pageNum: Int) : PDFTextStripper() {
    val blocks = mutableListOf<AdvancedBlock>()
    var lineCount = 0
    var skippedFormulasCount = 0

    private val FORMULA_FONT_PATTERN = Pattern.compile(
        "(CM[^R]|MS.M|XY|MT|BL|RM|EU|LA|RS|LINE|LCIRCLE|TeX-|rsfs|txsy|wasy|" +
        "stmary|.*Mono|.*Code|.*Sym|.*Math|.*Typewriter|Cousine|Consolas|Menlo|" +
        "Monaco|Inconsolata|Source.?Code|Fira.?Code|DejaVu.?Sans.?Mono|" +
        "Liberation.?Mono|Courier)",
        Pattern.CASE_INSENSITIVE
    )
    private val MATH_SYMBOL_PATTERN = Pattern.compile(
        "[=≤≥≈≠±×÷·∑∫√∞∝+*/^]|\\\\log|\\\\lim|\\\\sin|\\\\cos|\\\\tan|\\\\cot|\\\\frac|\\\\sqrt"
    )

    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        if (textPositions.isNullOrEmpty()) return
        lineCount++

        val first = textPositions.first()
        val last = textPositions.last()
        val lineText = text ?: ""

        val isMathFont = textPositions.any { pos ->
            val fontName = pos.font?.name ?: ""
            FORMULA_FONT_PATTERN.matcher(fontName).find()
        }
        val hasMathSymbols = MATH_SYMBOL_PATTERN.matcher(lineText).find()
        val isMathLine = isMathFont || hasMathSymbols
        val mathSpans = if (isMathLine) {
            skippedFormulasCount++
            listOf(lineText)
        } else emptyList()

        val x0 = first.xDirAdj
        val y0 = first.yDirAdj
        val x1 = last.xDirAdj + last.widthDirAdj
        val y1 = first.yDirAdj + first.heightDir
        val fontSize = first.fontSizeInPt
        val isPureMath = isMathLine && lineText.length < 15

        // Line-based clustering: Avoid page-wide over-merging by keeping blocks per physical line/paragraph
        blocks.add(
            AdvancedBlock(
                text = lineText,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                fontSize = fontSize,
                isPureMath = isPureMath,
                mathSpans = mathSpans
            )
        )
    }
}
