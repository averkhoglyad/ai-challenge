package io.averkhogliad.ai.challenge.week6.domain.fileops.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.*

interface FileOpsPort {

    suspend fun read(path: RelativePath): DomainResult<FileContent>

    suspend fun write(path: RelativePath, content: String): DomainResult<Unit>

    suspend fun search(query: SearchQuery): DomainResult<List<SearchHit>>

    suspend fun list(dir: RelativePath, filter: FileFilter): DomainResult<List<FileMetadata>>

    suspend fun info(path: RelativePath): DomainResult<FileMetadata>

    suspend fun exists(path: RelativePath): DomainResult<Boolean>

    suspend fun delete(path: RelativePath): DomainResult<Unit>
}
