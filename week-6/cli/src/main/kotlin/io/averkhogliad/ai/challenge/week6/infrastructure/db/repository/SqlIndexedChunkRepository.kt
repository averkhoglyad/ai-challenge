package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Embedding
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedChunkRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.IndexChunksTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class SqlIndexedChunkRepository : IndexedChunkRepository {

    override suspend fun save(projectId: String, chunks: List<IndexedChunk>) {
        transaction {
            IndexChunksTable.deleteWhere { IndexChunksTable.projectId eq projectId }
            chunks.forEach { chunk ->
                IndexChunksTable.insert {
                    it[IndexChunksTable.id] = chunk.chunk.id.toString()
                    it[IndexChunksTable.projectId] = projectId
                    it[IndexChunksTable.chunkText] = chunk.chunk.text
                    it[IndexChunksTable.sourcePath] = chunk.chunk.source
                    it[IndexChunksTable.embedding] = floatArrayToBytes(chunk.embedding.vector)
                    it[IndexChunksTable.model] = chunk.embedding.model
                    it[IndexChunksTable.createdAt] = Instant.now().toEpochMilli()
                }
            }
        }
    }

    override suspend fun findByProjectId(projectId: String): List<IndexedChunk> = transaction {
        IndexChunksTable.selectAll()
            .where { IndexChunksTable.projectId eq projectId }
            .mapNotNull { row ->
                val id = try {
                    UUID.fromString(row[IndexChunksTable.id])
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                IndexedChunk(
                    chunk = Chunk(
                        id = id,
                        text = row[IndexChunksTable.chunkText],
                        source = row[IndexChunksTable.sourcePath],
                    ),
                    embedding = Embedding(
                        chunkId = id,
                        vector = bytesToFloatArray(row[IndexChunksTable.embedding]),
                        model = row[IndexChunksTable.model],
                    ),
                )
            }
    }

    override suspend fun deleteByProjectId(projectId: String) {
        transaction {
            IndexChunksTable.deleteWhere { IndexChunksTable.projectId eq projectId }
        }
    }

    private fun floatArrayToBytes(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * Float.SIZE_BYTES)
        array.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        val floats = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }
}
