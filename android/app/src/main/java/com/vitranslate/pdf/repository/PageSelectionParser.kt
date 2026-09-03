package com.vitranslate.pdf.repository

object PageSelectionParser {

    /**
     * Parses a page selection string (e.g. "1", "1,3,5", "1-5", "1-3,7,10-12") against totalPages (1-indexed).
     * Returns a sorted, distinct list of valid 1-based page numbers.
     * Returns all pages (1..totalPages) if raw input is blank, invalid, or "all".
     */
    fun parsePageSelection(rawInput: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = rawInput.trim()
        if (trimmed.isBlank() || trimmed.equals("all", ignoreCase = true)) {
            return (1..totalPages).toList()
        }

        val resultSet = mutableSetOf<Int>()
        val parts = trimmed.split(",")

        for (part in parts) {
            val token = part.trim()
            if (token.isBlank()) continue

            if (token.contains("-")) {
                val bounds = token.split("-")
                if (bounds.size == 2) {
                    val start = bounds[0].trim().toIntOrNull()
                    val end = bounds[1].trim().toIntOrNull()
                    if (start != null && end != null && start > 0 && end > 0) {
                        val from = minOf(start, end).coerceIn(1, totalPages)
                        val to = maxOf(start, end).coerceIn(1, totalPages)
                        for (p in from..to) {
                            resultSet.add(p)
                        }
                    }
                }
            } else {
                val pageNum = token.toIntOrNull()
                if (pageNum != null && pageNum in 1..totalPages) {
                    resultSet.add(pageNum)
                }
            }
        }

        val sorted = resultSet.sorted()
        return if (sorted.isEmpty()) (1..totalPages).toList() else sorted
    }
}
