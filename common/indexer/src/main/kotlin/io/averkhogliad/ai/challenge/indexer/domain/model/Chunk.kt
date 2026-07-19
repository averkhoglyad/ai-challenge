package io.averkhogliad.ai.challenge.indexer.domain.model

import java.util.*

data class Chunk(
    val id: UUID,
    val text: String,
    val source: String,
    val title: String? = null,
    val section: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)
