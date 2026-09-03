package com.vitranslate.pdf.repository

import java.util.regex.Matcher
import java.util.regex.Pattern

class FormulaPlaceholderException(message: String) : Exception(message)

object FormulaPlaceholder {
    private val PLACEHOLDER_PATTERN = Pattern.compile("</?b\\d+>")
    private val INTERNAL_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\s*v(\\d+)\\s*\\}", Pattern.CASE_INSENSITIVE)
    private val PAIRED_PLACEHOLDER_PATTERN = Pattern.compile("<b(\\d+)></b\\1>")
    private val STYLE_TAG_PATTERN = Pattern.compile("<(/?)s([123])>", Pattern.CASE_INSENSITIVE)

    /**
     * Remove control characters that cannot be emitted safely into PDF text.
     */
    fun removeControlCharacters(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            val type = Character.getType(ch)
            if (type != Character.CONTROL.toInt() &&
                type != Character.FORMAT.toInt() &&
                type != Character.PRIVATE_USE.toInt() &&
                type != Character.SURROGATE.toInt() &&
                type != Character.UNASSIGNED.toInt()
            ) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Convert converter-internal `{vN}` markers into translator-safe tag pairs `<bN></bN>`.
     * Port of Windows `encode_formula_placeholders` in translator.py:183.
     */
    fun encodeFormulaPlaceholders(text: String): String {
        val matcher = INTERNAL_PLACEHOLDER_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val num = matcher.group(1)?.replace(" ", "") ?: "0"
            matcher.appendReplacement(sb, "<b$num></b$num>")
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Extract formula placeholder tags in order, e.g. ["<b0>", "</b0>"].
     */
    fun getPlaceholders(text: String): List<String> {
        val list = mutableListOf<String>()
        val matcher = PLACEHOLDER_PATTERN.matcher(text)
        while (matcher.find()) {
            list.add(matcher.group())
        }
        return list
    }

    /**
     * Validate style tag balance in text.
     */
    fun validateStyleTags(source: String, translated: String) {
        if (getStyleTagCounts(source) != getStyleTagCounts(translated)) {
            throw FormulaPlaceholderException("style tags changed during translation")
        }
    }

    private fun getStyleTagCounts(text: String): Map<String, Int> {
        val stack = mutableListOf<String>()
        val counts = mutableMapOf<String, Int>()
        val matcher = STYLE_TAG_PATTERN.matcher(text)
        while (matcher.find()) {
            val closing = matcher.group(1)?.isNotEmpty() == true
            val id = matcher.group(2) ?: ""
            if (!closing) {
                stack.add(id)
            } else {
                if (stack.isEmpty() || stack.last() != id) {
                    throw FormulaPlaceholderException("style tags are malformed or cross-nested")
                }
                stack.removeAt(stack.size - 1)
                counts[id] = (counts[id] ?: 0) + 1
            }
        }
        if (stack.isNotEmpty()) {
            throw FormulaPlaceholderException("style tags are not closed")
        }
        return counts
    }

    /**
     * Validate translator output and restore tags to converter markers `{vN}`.
     * Port of Windows `restore_formula_placeholders` in translator.py:191.
     */
    fun restoreFormulaPlaceholders(source: String, translated: String): String {
        val encodedSource = encodeFormulaPlaceholders(source)
        val sourcePlaceholders = getPlaceholders(encodedSource)
        val translatedPlaceholders = getPlaceholders(translated)
        if (sourcePlaceholders != translatedPlaceholders) {
            throw FormulaPlaceholderException("Formula placeholders were altered during translation")
        }

        val matcher = PAIRED_PLACEHOLDER_PATTERN.matcher(translated)
        val sb = StringBuffer()
        while (matcher.find()) {
            val num = matcher.group(1) ?: "0"
            matcher.appendReplacement(sb, "{v$num}")
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Restores `{vN}` markers in text with original formula strings from formulaVars list.
     */
    fun restoreFormulaVars(text: String, formulaVars: List<String>): String {
        if (formulaVars.isEmpty()) return text
        val matcher = INTERNAL_PLACEHOLDER_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val idx = matcher.group(1)?.toIntOrNull() ?: -1
            val replacement = if (idx in formulaVars.indices) formulaVars[idx] else matcher.group()
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }
}
