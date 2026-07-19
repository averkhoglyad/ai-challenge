package io.averkhogliad.ai.challenge.week6

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

object GitHookInstaller {

    private const val HOOK_NAME = "post-commit"
    private const val CONFIG_FILE = "copilot-config"

    fun install(projectRoot: Path, binPath: Path): Result {
        val gitDir = projectRoot.resolve(".git")
        if (!Files.isDirectory(gitDir)) {
            return Result.Failure("Not a git repository: $projectRoot")
        }

        val hooksDir = gitDir.resolve("hooks")
        if (!Files.isDirectory(hooksDir)) {
            Files.createDirectories(hooksDir)
        }

        val hookScript = """#!/bin/bash
# AI Challenge Code Review Hook

# Skip merge commits
if git rev-parse HEAD^2 >/dev/null 2>&1; then
    exit 0
fi

# Skip if no parent commit
if ! git rev-parse HEAD~1 >/dev/null 2>&1; then
    exit 0
fi

# Run review
"$binPath" --review
"""

        val hookFile = hooksDir.resolve(HOOK_NAME)
        Files.writeString(hookFile, hookScript)

        try {
            val perms = Files.getPosixFilePermissions(hookFile)
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(hookFile, perms)
        } catch (_: Exception) {
            // Windows doesn't support POSIX permissions
        }

        val configFile = gitDir.resolve(CONFIG_FILE)
        Files.writeString(configFile, binPath.toString())

        return Result.Success("Hook installed successfully: $hookFile")
    }

    fun remove(projectRoot: Path): Result {
        val hookFile = projectRoot.resolve(".git/hooks/$HOOK_NAME")
        val configFile = projectRoot.resolve(".git/$CONFIG_FILE")

        var removed = false
        if (Files.exists(hookFile)) {
            Files.delete(hookFile)
            removed = true
        }
        if (Files.exists(configFile)) {
            Files.delete(configFile)
            removed = true
        }

        return if (removed) {
            Result.Success("Hook removed successfully")
        } else {
            Result.Success("No hook installed")
        }
    }

    fun isInstalled(projectRoot: Path): Boolean {
        return Files.exists(projectRoot.resolve(".git/hooks/$HOOK_NAME"))
    }

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }
}
