package io.averkhogliad.ai.challenge.week6

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

object GitHookInstaller {

    private const val HOOK_NAME = "post-commit"
    private const val CONFIG_FILE = "copilot-config"
    private const val BLOCK_BEGIN = "# >>> AI Challenge review hook >>>"
    private const val BLOCK_END = "# <<< AI Challenge review hook <<<"

    fun install(projectRoot: Path, launcherPath: Path): Result {
        val gitDir = projectRoot.resolve(".git")
        if (!Files.isDirectory(gitDir)) {
            return Result.Failure("Not a git repository: $projectRoot")
        }

        val hooksDir = gitDir.resolve("hooks")
        if (!Files.isDirectory(hooksDir)) {
            Files.createDirectories(hooksDir)
        }

        val hookFile = hooksDir.resolve(HOOK_NAME)
        val existing = if (Files.exists(hookFile)) Files.readString(hookFile) else ""

        val beginCount = existing.lines().count { it.trim() == BLOCK_BEGIN }
        val endCount = existing.lines().count { it.trim() == BLOCK_END }
        if (beginCount != endCount || beginCount > 1) {
            return Result.Failure(
                "Cannot safely modify $hookFile: AI Challenge markers are corrupted. " +
                        "Manually check the file before reinstalling the hook."
            )
        }

        val blockBody = reviewBlock(launcherPath)
        val newContent = if (beginCount == 1) {
            replaceBlock(existing, blockBody)
        } else {
            appendBlock(existing, blockBody)
        }

        val tmpFile = Files.createTempFile(hooksDir, ".post-commit", null)
        try {
            Files.writeString(tmpFile, newContent)
            Files.move(tmpFile, hookFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(tmpFile, hookFile, StandardCopyOption.REPLACE_EXISTING)
        }

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
        Files.writeString(configFile, launcherPath.toAbsolutePath().toString())

        return Result.Success("Hook installed successfully: $hookFile")
    }

    private fun reviewBlock(launcherPath: Path): String {
        val cmd = reviewCommand(launcherPath)
        return """$BLOCK_BEGIN

# Skip merge commits
if git rev-parse HEAD^2 >/dev/null 2>&1; then
    exit 0
fi

# Skip if no parent commit
if ! git rev-parse HEAD~1 >/dev/null 2>&1; then
    exit 0
fi

# Run review
$cmd
$BLOCK_END"""
    }

    private fun replaceBlock(existing: String, block: String): String {
        val before = existing.substringBefore(BLOCK_BEGIN)
        val after = existing.substringAfter(BLOCK_END)
        return before + block + after
    }

    private fun appendBlock(existing: String, block: String): String {
        val base = if (existing.isBlank()) {
            "#!/bin/bash\n"
        } else if (!existing.endsWith("\n")) {
            existing + "\n"
        } else {
            existing
        }
        return base + "\n" + block + "\n"
    }

    private fun removeBlock(existing: String): String? {
        val before = existing.substringBefore(BLOCK_BEGIN).trimEnd('\n')
        val after = existing.substringAfter(BLOCK_END).trimStart('\n')
        val result = (before + if (after.isNotEmpty()) "\n$after" else "").trim()
        return result.ifEmpty { null }
    }

    private fun reviewCommand(launcherPath: Path): String {
        val path = launcherPath.toAbsolutePath().toString()
        return if (path.endsWith(".bat", ignoreCase = true)) {
            "cmd.exe /c \"\\\"$path\\\" --review\""
        } else {
            "'${path.replace("'", "'\\\"'\\\"'")}' --review"
        }
    }

    fun remove(projectRoot: Path): Result {
        val hookFile = projectRoot.resolve(".git/hooks/$HOOK_NAME")
        val configFile = projectRoot.resolve(".git/$CONFIG_FILE")

        val existing = if (Files.exists(hookFile)) Files.readString(hookFile) else null
        if (existing == null || !existing.contains(BLOCK_BEGIN)) {
            Files.deleteIfExists(configFile)
            return Result.Success("No hook installed")
        }

        val stripped = removeBlock(existing)
        val tmpFile = Files.createTempFile(hookFile.parent, ".post-commit", null)
        try {
            if (stripped != null) {
                Files.writeString(tmpFile, stripped + "\n")
                Files.move(tmpFile, hookFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(tmpFile)
                Files.delete(hookFile)
            }
        } catch (_: Exception) {
            if (stripped != null) {
                Files.move(tmpFile, hookFile, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(tmpFile)
                Files.deleteIfExists(hookFile)
            }
        }

        Files.deleteIfExists(configFile)
        return Result.Success("Hook removed successfully")
    }

    fun isInstalled(projectRoot: Path): Boolean {
        val hookFile = projectRoot.resolve(".git/hooks/$HOOK_NAME")
        return Files.exists(hookFile) && Files.readString(hookFile).contains(BLOCK_BEGIN)
    }

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }
}
