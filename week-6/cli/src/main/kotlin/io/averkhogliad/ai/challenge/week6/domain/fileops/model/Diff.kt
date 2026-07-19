package io.averkhogliad.ai.challenge.week6.domain.fileops.model

data class Diff(
    val path: RelativePath,
    val oldContent: String?,
    val newContent: String,
    val hunks: List<Hunk>,
)

data class Hunk(
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>,
)

sealed interface DiffLine {
    data class Added(val text: String) : DiffLine
    data class Removed(val text: String) : DiffLine
    data class Context(val text: String) : DiffLine
    data class Modified(
        val oldText: String,
        val newText: String,
        val wordDiffs: List<WordDiff> = emptyList(),
    ) : DiffLine
}

sealed interface WordDiff {
    data class WordAdded(val text: String) : WordDiff
    data class WordRemoved(val text: String) : WordDiff
    data class WordContext(val text: String) : WordDiff
}
