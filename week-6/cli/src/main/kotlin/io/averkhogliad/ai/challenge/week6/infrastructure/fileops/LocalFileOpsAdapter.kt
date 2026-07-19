package io.averkhogliad.ai.challenge.week6.infrastructure.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.FileOperation
import io.averkhogliad.ai.challenge.week6.application.fileops.SandboxPolicy
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.*
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

class LocalFileOpsAdapter(
    private val rootPath: Path,
    private val sandboxPolicy: SandboxPolicy = SandboxPolicy(),
) : FileOpsPort {

    companion object {
        const val MAX_FILE_SIZE = 64L * 1024 // 64 KB

        @Suppress("UNCHECKED_CAST")
        private fun <T> reject(checkResult: DomainResult<*>): DomainResult<T> = checkResult as DomainResult<T>
    }

    override suspend fun read(path: RelativePath): DomainResult<FileContent> {
        val checkResult = sandboxPolicy.check(path, FileOperation.Read)
        if (checkResult.isFailure) return reject(checkResult)

        val resolved = path.toAbsolutePath(rootPath)
        return try {
            if (!Files.exists(resolved)) {
                return DomainResult.Failure(DomainError.FileNotFound(path))
            }
            if (!Files.isRegularFile(resolved)) {
                return DomainResult.Failure(DomainError.FileNotFound(path))
            }

            val canonical = resolved.toRealPath()
            if (!canonical.startsWith(rootPath.toRealPath())) {
                return DomainResult.Failure(DomainError.FileOutsideSandbox(path, rootPath))
            }

            if (BinaryFileDetector.isBinary(resolved)) {
                return DomainResult.Failure(DomainError.FileBinaryFile(path))
            }

            val size = Files.size(resolved)
            val truncated = size > MAX_FILE_SIZE
            val bytes = if (truncated) {
                Files.readAllBytes(resolved).copyOf(MAX_FILE_SIZE.toInt())
            } else {
                Files.readAllBytes(resolved)
            }
            val content = String(bytes, StandardCharsets.UTF_8)

            DomainResult.Success(
                FileContent(
                    path = path,
                    content = content,
                    encoding = StandardCharsets.UTF_8,
                    sizeBytes = size,
                    truncated = truncated,
                )
            )
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(path, e))
        }
    }

    override suspend fun write(path: RelativePath, content: String): DomainResult<Unit> {
        val checkResult = sandboxPolicy.check(path, FileOperation.Write)
        if (checkResult.isFailure) return checkResult

        val resolved = path.toAbsolutePath(rootPath)
        return try {
            Files.createDirectories(resolved.parent)

            val normalized = resolved.toAbsolutePath().normalize()
            val rootNormalized = rootPath.toAbsolutePath().normalize()
            if (!normalized.startsWith(rootNormalized)) {
                return DomainResult.Failure(DomainError.FileOutsideSandbox(path, rootPath))
            }

            Files.writeString(resolved, content, StandardCharsets.UTF_8)
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(path, e))
        }
    }

    override suspend fun search(query: SearchQuery): DomainResult<List<SearchHit>> {
        val directory = query.inDirectory?.let { it.toAbsolutePath(rootPath) } ?: rootPath
        val results = mutableListOf<SearchHit>()

        return try {
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                return DomainResult.Success(emptyList())
            }

            val ext = query.extension?.let { if (it.startsWith(".")) it else ".$it" }

            Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (results.size >= 50) return FileVisitResult.TERMINATE

                    val relStr = rootPath.relativize(file).toString().replace('\\', '/')
                    val relPathResult = RelativePath.from(relStr, rootPath)
                    if (relPathResult.isFailure) return FileVisitResult.CONTINUE
                    val relPath = relPathResult.getOrThrow()

                    if (sandboxPolicy.check(relPath, FileOperation.Search).isFailure) {
                        return FileVisitResult.CONTINUE
                    }

                    if (ext != null && !file.fileName.toString().endsWith(ext, ignoreCase = true)) {
                        return FileVisitResult.CONTINUE
                    }

                    if (BinaryFileDetector.isBinary(file)) return FileVisitResult.CONTINUE

                    try {
                        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
                        for ((index, line) in lines.withIndex()) {
                            val matched = if (query.ignoreCase) {
                                line.contains(query.query, ignoreCase = true)
                            } else {
                                line.contains(query.query)
                            }
                            if (matched) {
                                val contextBefore = lines.subList(maxOf(0, index - 2), index)
                                val contextAfter = lines.subList(index + 1, minOf(lines.size, index + 3))
                                results.add(
                                    SearchHit(
                                        path = relPath,
                                        line = index + 1,
                                        snippet = line.take(200),
                                        contextBefore = contextBefore,
                                        contextAfter = contextAfter,
                                    )
                                )
                                if (results.size >= 50) break
                            }
                        }
                    } catch (_: Exception) {
                        // Skip files that can't be read as text
                    }

                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relStr = rootPath.relativize(dir).toString().replace('\\', '/')
                    if (relStr.isEmpty()) return FileVisitResult.CONTINUE
                    val relPathResult = RelativePath.from(relStr, rootPath)
                    if (relPathResult.isFailure) return FileVisitResult.SKIP_SUBTREE
                    val relPath = relPathResult.getOrThrow()

                    if (sandboxPolicy.check(relPath, FileOperation.Search).isFailure) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }
            })

            DomainResult.Success(results)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(null, e))
        }
    }

    override suspend fun list(dir: RelativePath, filter: FileFilter): DomainResult<List<FileMetadata>> {
        val checkResult = sandboxPolicy.check(dir, FileOperation.List)
        if (checkResult.isFailure) return reject(checkResult)

        val resolved = dir.toAbsolutePath(rootPath)
        return try {
            if (!Files.exists(resolved) || !Files.isDirectory(resolved)) {
                return DomainResult.Success(emptyList())
            }

            val allFiles: List<Path> = Files.list(resolved).use { stream ->
                stream.collect(Collectors.toList())
            }

            val entries = allFiles
                .sortedBy { it.fileName.toString().lowercase() }
                .filter { file ->
                    val relStr = rootPath.relativize(file).toString().replace('\\', '/')
                    val relResult = RelativePath.from(relStr, rootPath)
                    if (relResult.isFailure) false
                    else sandboxPolicy.check(relResult.getOrThrow(), FileOperation.List).isSuccess
                }
                .filter { file ->
                    filter.extension?.let { ext ->
                        val normalizedExt = if (ext.startsWith(".")) ext else ".$ext"
                        file.fileName.toString().endsWith(normalizedExt, ignoreCase = true)
                    } ?: true
                }
                .take(200)
                .mapNotNull { file ->
                    val relStr = rootPath.relativize(file).toString().replace('\\', '/')
                    val relPath = RelativePath.from(relStr, rootPath).getOrNull() ?: return@mapNotNull null
                    val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
                    FileMetadata(
                        path = relPath,
                        sizeBytes = attrs.size(),
                        isDirectory = attrs.isDirectory,
                        lastModified = attrs.lastModifiedTime().toInstant(),
                        isBinary = if (attrs.isRegularFile) BinaryFileDetector.isBinary(file) else false,
                    )
                }

            DomainResult.Success(entries)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(dir, e))
        }
    }

    override suspend fun info(path: RelativePath): DomainResult<FileMetadata> {
        val checkResult = sandboxPolicy.check(path, FileOperation.Read)
        if (checkResult.isFailure) return reject(checkResult)

        val resolved = path.toAbsolutePath(rootPath)
        return try {
            if (!Files.exists(resolved)) {
                return DomainResult.Failure(DomainError.FileNotFound(path))
            }

            val attrs = Files.readAttributes(resolved, BasicFileAttributes::class.java)
            DomainResult.Success(
                FileMetadata(
                    path = path,
                    sizeBytes = attrs.size(),
                    isDirectory = attrs.isDirectory,
                    lastModified = attrs.lastModifiedTime().toInstant(),
                    isBinary = if (attrs.isRegularFile) BinaryFileDetector.isBinary(resolved) else false,
                )
            )
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(path, e))
        }
    }

    override suspend fun exists(path: RelativePath): DomainResult<Boolean> {
        val checkResult = sandboxPolicy.check(path, FileOperation.Read)
        if (checkResult.isFailure) return reject(checkResult)

        val resolved = path.toAbsolutePath(rootPath)
        return try {
            val normalized = resolved.toAbsolutePath().normalize()
            val rootNormalized = rootPath.toAbsolutePath().normalize()
            if (!normalized.startsWith(rootNormalized)) {
                return DomainResult.Failure(DomainError.FileOutsideSandbox(path, rootPath))
            }
            DomainResult.Success(Files.exists(normalized))
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(path, e))
        }
    }

    override suspend fun delete(path: RelativePath): DomainResult<Unit> {
        val checkResult = sandboxPolicy.check(path, FileOperation.Write)
        if (checkResult.isFailure) return reject(checkResult)

        val resolved = path.toAbsolutePath(rootPath)
        return try {
            val normalized = resolved.toAbsolutePath().normalize()
            val rootNormalized = rootPath.toAbsolutePath().normalize()
            if (!normalized.startsWith(rootNormalized)) {
                return DomainResult.Failure(DomainError.FileOutsideSandbox(path, rootPath))
            }

            Files.deleteIfExists(normalized)
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.FileIOError(path, e))
        }
    }
}
