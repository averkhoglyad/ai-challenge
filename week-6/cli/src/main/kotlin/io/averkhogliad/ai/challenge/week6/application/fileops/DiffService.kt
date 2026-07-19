package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.domain.fileops.model.*

class DiffService {

    companion object {
        private const val MAX_LCS_LINES = 5000
    }

    fun buildDiffs(changes: List<FileChange>): List<Diff> {
        return changes.map { change -> buildDiff(change) }
    }

    private fun buildDiff(change: FileChange): Diff {
        val oldLines = change.oldContent?.lines() ?: emptyList()
        val newLines = change.newContent.lines()
        val hunks = computeHunks(oldLines, newLines)
        return Diff(
            path = change.path,
            oldContent = change.oldContent,
            newContent = change.newContent,
            hunks = hunks,
        )
    }

    private fun computeHunks(oldLines: List<String>, newLines: List<String>): List<Hunk> {
        if (oldLines.isEmpty() && newLines.isEmpty()) return emptyList()

        // Use LCS-based diff algorithm
        val lcs = longestCommonSubsequence(oldLines, newLines)
        val diffLines = buildDiffLines(oldLines, newLines, lcs)
        return groupIntoHunks(diffLines, oldLines, newLines)
    }

    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val m = a.size
        val n = b.size

        // Guard against O(m×n) memory blowup
        if (m.toLong() * n > MAX_LCS_LINES.toLong() * MAX_LCS_LINES) {
            // Fallback: simple line-by-line comparison without LCS
            val result = mutableListOf<Pair<Int, Int>>()
            val maxLen = minOf(m, n)
            for (k in 0 until maxLen) {
                if (a[k] == b[k]) result.add(Pair(k, k))
                else break
            }
            return result
        }

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val result = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    result.add(Pair(i - 1, j - 1))
                    i--
                    j--
                }

                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }
        return result.reversed()
    }

    private fun buildDiffLines(
        oldLines: List<String>,
        newLines: List<String>,
        lcs: List<Pair<Int, Int>>,
    ): List<DiffLine> {
        val raw = mutableListOf<DiffLine>()
        var oldIdx = 0
        var newIdx = 0

        for ((lcsOld, lcsNew) in lcs) {
            while (oldIdx < lcsOld) {
                raw.add(DiffLine.Removed(oldLines[oldIdx]))
                oldIdx++
            }
            while (newIdx < lcsNew) {
                raw.add(DiffLine.Added(newLines[newIdx]))
                newIdx++
            }
            raw.add(DiffLine.Context(newLines[newIdx]))
            oldIdx++
            newIdx++
        }

        while (oldIdx < oldLines.size) {
            raw.add(DiffLine.Removed(oldLines[oldIdx]))
            oldIdx++
        }

        while (newIdx < newLines.size) {
            raw.add(DiffLine.Added(newLines[newIdx]))
            newIdx++
        }

        // Merge adjacent Removed→Added pairs into Modified (for word-level diff)
        return mergeRemovedAdded(raw)
    }

    private fun mergeRemovedAdded(lines: List<DiffLine>): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            if (current is DiffLine.Removed && i + 1 < lines.size && lines[i + 1] is DiffLine.Added) {
                val removed = current
                val added = lines[i + 1] as DiffLine.Added
                val wordDiffs = computeWordDiffs(removed.text, added.text)
                result.add(DiffLine.Modified(removed.text, added.text, wordDiffs))
                i += 2
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    private fun computeWordDiffs(oldLine: String, newLine: String): List<WordDiff> {
        // Skip word-level diff for large lines (>2KB)
        if (oldLine.length > 2048 || newLine.length > 2048) return emptyList()

        val oldWords = oldLine.split(Regex("(?<=\\s)|(?=\\s)"))
        val newWords = newLine.split(Regex("(?<=\\s)|(?=\\s)"))

        val wordLcs = longestCommonSubsequenceWords(oldWords, newWords)
        return buildWordDiffs(oldWords, newWords, wordLcs)
    }

    private fun longestCommonSubsequenceWords(
        a: List<String>,
        b: List<String>,
    ): List<Pair<Int, Int>> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val result = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    result.add(Pair(i - 1, j - 1))
                    i--
                    j--
                }

                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }
        return result.reversed()
    }

    private fun buildWordDiffs(
        oldWords: List<String>,
        newWords: List<String>,
        lcs: List<Pair<Int, Int>>,
    ): List<WordDiff> {
        val result = mutableListOf<WordDiff>()
        var oldIdx = 0
        var newIdx = 0

        for ((lcsOld, lcsNew) in lcs) {
            while (oldIdx < lcsOld) {
                result.add(WordDiff.WordRemoved(oldWords[oldIdx]))
                oldIdx++
            }
            while (newIdx < lcsNew) {
                result.add(WordDiff.WordAdded(newWords[newIdx]))
                newIdx++
            }
            result.add(WordDiff.WordContext(newWords[newIdx]))
            oldIdx++
            newIdx++
        }

        while (oldIdx < oldWords.size) {
            result.add(WordDiff.WordRemoved(oldWords[oldIdx]))
            oldIdx++
        }
        while (newIdx < newWords.size) {
            result.add(WordDiff.WordAdded(newWords[newIdx]))
            newIdx++
        }

        return result
    }

    private fun groupIntoHunks(
        diffLines: List<DiffLine>,
        oldLines: List<String>,
        newLines: List<String>,
    ): List<Hunk> {
        if (diffLines.isEmpty()) return emptyList()

        val hunks = mutableListOf<Hunk>()
        var i = 0
        var oldLineNum = 1
        var newLineNum = 1
        var currentHunkLines = mutableListOf<DiffLine>()
        var hunkOldStart = oldLineNum
        var hunkNewStart = newLineNum

        while (i < diffLines.size) {
            val line = diffLines[i]
            when (line) {
                is DiffLine.Context -> {
                    if (currentHunkLines.isNotEmpty()) {
                        currentHunkLines.add(line)
                        // End current hunk
                        hunks.add(Hunk(hunkOldStart, hunkNewStart, currentHunkLines.toList()))
                        currentHunkLines = mutableListOf()
                    }
                    oldLineNum++
                    newLineNum++
                }

                is DiffLine.Removed -> {
                    if (currentHunkLines.isEmpty()) {
                        hunkOldStart = oldLineNum
                        hunkNewStart = newLineNum
                    }
                    currentHunkLines.add(line)
                    oldLineNum++
                }

                is DiffLine.Added -> {
                    if (currentHunkLines.isEmpty()) {
                        hunkOldStart = oldLineNum
                        hunkNewStart = newLineNum
                    }
                    currentHunkLines.add(line)
                    newLineNum++
                }

                is DiffLine.Modified -> {
                    if (currentHunkLines.isEmpty()) {
                        hunkOldStart = oldLineNum
                        hunkNewStart = newLineNum
                    }
                    currentHunkLines.add(line)
                    oldLineNum++
                    newLineNum++
                }
            }
            i++
        }

        // Add remaining hunk
        if (currentHunkLines.isNotEmpty()) {
            hunks.add(Hunk(hunkOldStart, hunkNewStart, currentHunkLines.toList()))
        }

        return hunks
    }
}
