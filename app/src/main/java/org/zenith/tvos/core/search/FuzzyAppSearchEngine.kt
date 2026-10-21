package org.zenith.tvos.core.search

import org.zenith.tvos.ui.dock.AppItem
import kotlin.math.abs
import kotlin.math.min

/**
 * Ultra-fast fuzzy, subsequence & Damerau-Levenshtein typo-tolerant application search engine
 * for Zenith TV Spotlight. Ranks installed applications by exact prefix, word boundary, substring,
 * initials, Damerau-Levenshtein edit distance (1-2 typos/transpositions), or ordered character
 * subsequence, with MRU recency and Favorites Dock boosting.
 */
class FuzzyAppSearchEngine {

    data class ScoredApp(
        val app: AppItem,
        val score: Int
    )

    /**
     * Searches and ranks [apps] against [rawQuery], applying optional MRU [recentPackages]
     * and [favoritePackages] boosts.
     * When [rawQuery] is blank, returns apps prioritized by MRU recency and favorites.
     */
    fun search(
        apps: List<AppItem>,
        rawQuery: String,
        recentPackages: List<String> = emptyList(),
        favoritePackages: Set<String> = emptySet()
    ): List<AppItem> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) {
            if (recentPackages.isEmpty() && favoritePackages.isEmpty()) return apps
            return apps.sortedWith(
                compareByDescending<AppItem> { app ->
                    val recentIdx = recentPackages.indexOf(app.packageName)
                    val recencyBoost = if (recentIdx >= 0) (100 - recentIdx * 5).coerceAtLeast(20) else 0
                    val favBoost = if (favoritePackages.contains(app.packageName)) 10 else 0
                    recencyBoost + favBoost
                }
            )
        }

        return apps.mapNotNull { app ->
            val score = computeMatchScore(app, query, recentPackages, favoritePackages)
            if (score > 0) ScoredApp(app, score) else null
        }
            .sortedWith(compareByDescending<ScoredApp> { it.score }.thenBy { it.app.label.lowercase() })
            .map { it.app }
    }

    /**
     * Computes a deterministic relevance score (> 0 if matched, 0 if no match).
     */
    fun computeMatchScore(
        app: AppItem,
        normalizedQuery: String,
        recentPackages: List<String> = emptyList(),
        favoritePackages: Set<String> = emptySet()
    ): Int {
        if (normalizedQuery.isEmpty()) return 1
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        val category = app.category.displayName.lowercase()
        val words = label.split(" ", "-", "_", ".").filter { it.isNotEmpty() }

        val baseScore = computeBaseLexicalScore(label, words, pkg, category, normalizedQuery)
        if (baseScore <= 0) return 0

        val recentIdx = recentPackages.indexOf(app.packageName)
        val recencyBoost = if (recentIdx >= 0) (40 - recentIdx * 4).coerceAtLeast(10) else 0
        val favBoost = if (favoritePackages.contains(app.packageName)) 20 else 0

        return baseScore + recencyBoost + favBoost
    }

    private fun computeBaseLexicalScore(
        label: String,
        words: List<String>,
        pkg: String,
        category: String,
        normalizedQuery: String
    ): Int {
        // 1. Exact label match
        if (label == normalizedQuery) return 1000

        // 2. Prefix match on label
        if (label.startsWith(normalizedQuery)) {
            return 850 - (label.length - normalizedQuery.length).coerceAtMost(100)
        }

        // 3. Word-boundary prefix match (e.g. "vid" matching "Prime Video")
        if (words.any { it.startsWith(normalizedQuery) }) {
            return 720
        }

        // 4. Contiguous substring match in label
        val labelIdx = label.indexOf(normalizedQuery)
        if (labelIdx >= 0) {
            return 600 - labelIdx.coerceAtMost(80)
        }

        // 5. Acronym / Initials match (e.g. "pv" matching "Prime Video", "yt" matching "YouTube TV")
        val initials = words.joinToString("") { it.take(1) }
        if (initials.length > 1 && initials.startsWith(normalizedQuery)) {
            return 550
        }

        // 6. Typo-tolerant Damerau-Levenshtein match (handles 1-2 typos or adjacent transpositions like "netlfix")
        if (normalizedQuery.length >= 4) {
            val candidates = buildList {
                add(label)
                addAll(words.filter { it.length >= 4 })
            }
            var bestDist = Int.MAX_VALUE
            for (cand in candidates) {
                val d = computeDamerauLevenshteinDistance(normalizedQuery, cand, maxDistance = 2)
                if (d < bestDist) bestDist = d
            }
            if (bestDist == 1) return 500
            if (bestDist == 2 && normalizedQuery.length >= 5) return 380
        }

        // 7. Ordered character subsequence in label (e.g. "ntflx" -> "netflix", "ytb" -> "youtube")
        val subseqPenalty = subsequenceGapPenalty(label, normalizedQuery)
        if (subseqPenalty != null) {
            return (450 - subseqPenalty * 8).coerceAtLeast(150)
        }

        // 8. Category or package name substring match
        if (category.contains(normalizedQuery)) return 220
        if (pkg.contains(normalizedQuery)) return 180

        return 0
    }

    /**
     * Computes optimal string alignment Damerau-Levenshtein distance (insertions, deletions,
     * substitutions, and adjacent transpositions), bounded by [maxDistance].
     * Returns `maxDistance + 1` if distance exceeds [maxDistance].
     */
    fun computeDamerauLevenshteinDistance(
        source: String,
        target: String,
        maxDistance: Int = 2
    ): Int {
        if (source == target) return 0
        val n = source.length
        val m = target.length
        if (abs(n - m) > maxDistance) return maxDistance + 1
        if (n == 0) return if (m <= maxDistance) m else maxDistance + 1
        if (m == 0) return if (n <= maxDistance) n else maxDistance + 1

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..m) {
                val cost = if (source[i - 1] == target[j - 1]) 0 else 1
                var value = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 &&
                    source[i - 1] == target[j - 2] &&
                    source[i - 2] == target[j - 1]
                ) {
                    value = min(value, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > maxDistance) return maxDistance + 1
        }

        val result = dp[n][m]
        return if (result <= maxDistance) result else maxDistance + 1
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
