package org.zenith.tvos.core.search

import org.zenith.tvos.ui.dock.AppItem

/**
 * Ultra-fast fuzzy & subsequence application search engine for Zenith TV Spotlight.
 * Ranks installed applications by exact prefix, word boundary, substring, or ordered character subsequence,
 * allowing instant launch from 2-3 keystrokes via TV remote or Wi-Fi Phone Remote keyboard.
 */
class FuzzyAppSearchEngine {

    data class ScoredApp(
        val app: AppItem,
        val score: Int
    )

    /**
     * Searches and ranks [apps] against [rawQuery].
     * Returns all apps in original order when [rawQuery] is blank.
     */
    fun search(apps: List<AppItem>, rawQuery: String): List<AppItem> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) return apps

        return apps.mapNotNull { app ->
            val score = computeMatchScore(app, query)
            if (score > 0) ScoredApp(app, score) else null
        }
            .sortedWith(compareByDescending<ScoredApp> { it.score }.thenBy { it.app.label.lowercase() })
            .map { it.app }
    }

    /**
     * Computes a deterministic relevance score (> 0 if matched, 0 if no match).
     */
    fun computeMatchScore(app: AppItem, normalizedQuery: String): Int {
        if (normalizedQuery.isEmpty()) return 1
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        val category = app.category.displayName.lowercase()

        // 1. Exact label match
        if (label == normalizedQuery) return 1000

        // 2. Prefix match on label
        if (label.startsWith(normalizedQuery)) {
            return 850 - (label.length - normalizedQuery.length).coerceAtMost(100)
        }

        // 3. Word-boundary prefix match (e.g. "vid" matching "Prime Video")
        val words = label.split(" ", "-", "_", ".")
        if (words.any { it.startsWith(normalizedQuery) }) {
            return 720
        }

        // 4. Contiguous substring match in label
        val labelIdx = label.indexOf(normalizedQuery)
        if (labelIdx >= 0) {
            return 600 - labelIdx.coerceAtMost(80)
        }

        // 5. Acronym / Initials match (e.g. "pv" matching "Prime Video", "yt" matching "YouTube TV")
        val initials = words.filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
        if (initials.length > 1 && initials.startsWith(normalizedQuery)) {
            return 550
        }

        // 6. Ordered character subsequence in label (e.g. "ntflx" -> "netflix", "ytb" -> "youtube")
        val subseqPenalty = subsequenceGapPenalty(label, normalizedQuery)
        if (subseqPenalty != null) {
            return (450 - subseqPenalty * 8).coerceAtLeast(150)
        }

        // 7. Category or package name substring match
        if (category.contains(normalizedQuery)) return 220
        if (pkg.contains(normalizedQuery)) return 180

        return 0
    }

    /**
     * Checks if all characters of [query] appear in order within [target].
     * @return total skipped character gaps if matched, or null if not a subsequence.
     */
    private fun subsequenceGapPenalty(target: String, query: String): Int? {
        var qIdx = 0
        var lastMatchIdx = -1
        var totalGaps = 0

        for (tIdx in target.indices) {
            if (target[tIdx] == query[qIdx]) {
                if (lastMatchIdx != -1) {
                    totalGaps += (tIdx - lastMatchIdx - 1)
                }
                lastMatchIdx = tIdx
                qIdx++
                if (qIdx == query.length) return totalGaps
            }
        }
        return null
    }
}
