package io.averkhogliad.ai.challenge.week6.infrastructure.git

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ProcessGitAdapter : GitPort {

    override suspend fun getCurrentBranch(rootPath: Path): DomainResult<String> {
        val gitDir = rootPath.resolve(".git")
        if (!Files.exists(gitDir)) {
            return DomainResult.Failure(DomainError.repository("Not a git repository: ${rootPath}"))
        }

        return runGitCommand(rootPath, listOf("rev-parse", "--abbrev-ref", "HEAD"))
    }

    override suspend fun getCurrentCommit(rootPath: Path): DomainResult<String> {
        val gitDir = rootPath.resolve(".git")
        if (!Files.exists(gitDir)) {
            return DomainResult.Failure(DomainError.repository("Not a git repository: $rootPath"))
        }
        return runGitCommand(rootPath, listOf("rev-parse", "HEAD"))
    }

    override suspend fun checkGitStatus(rootPath: Path): DomainResult<Boolean> {
        val gitDir = rootPath.resolve(".git")
        if (!Files.exists(gitDir)) {
            return DomainResult.Success(false)
        }

        return when (val result = runGitCommand(rootPath, listOf("status", "--porcelain"))) {
            is DomainResult.Success -> DomainResult.Success(result.value.isNotEmpty())
            is DomainResult.Failure -> DomainResult.Failure(result.error)
        }
    }

    private fun runGitCommand(directory: Path, args: List<String>): DomainResult<String> {
        return try {
            val process = ProcessBuilder()
                .command(listOf("git") + args)
                .directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val completed = process.waitFor(10, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return DomainResult.Failure(DomainError.repository("Git command timed out"))
            }

            if (process.exitValue() != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                return DomainResult.Failure(DomainError.repository("Git error: ${errorOutput.ifEmpty { "exit code ${process.exitValue()}" }}"))
            }

            DomainResult.Success(output)
        } catch (e: java.io.IOException) {
            if (e.message?.contains("Cannot run program \"git\"") == true ||
                e.message?.contains("No such file") == true
            ) {
                DomainResult.Failure(DomainError.repository("Git is not installed or not found in PATH"))
            } else {
                DomainResult.Failure(DomainError.repository("Failed to execute git: ${e.message}"))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            DomainResult.Failure(DomainError.repository("Git command was interrupted"))
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository("Unexpected error running git: ${e.message}"))
        }
    }
}
