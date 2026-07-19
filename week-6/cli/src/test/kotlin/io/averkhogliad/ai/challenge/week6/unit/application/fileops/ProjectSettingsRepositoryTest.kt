package io.averkhogliad.ai.challenge.week6.unit.application.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.ProjectSettingsRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class ProjectSettingsRepositoryTest : FreeSpec({

    lateinit var repo: ProjectSettingsRepository
    lateinit var db: Database
    val tempDirs = mutableListOf<Path>()

    beforeSpec {
        val tmpRoot = Path.of(System.getProperty("java.io.tmpdir"))
        Files.list(tmpRoot).use { stream ->
            stream.filter { it.fileName.toString().startsWith("project-settings-test-") }
                .forEach { runCatching { it.toFile().deleteRecursively() } }
        }
    }

    beforeTest {
        val tempDir = createTempDirectory("project-settings-test-")
        tempDirs.add(tempDir)
        val dbPath = tempDir.resolve("test.db")
        db = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            exec("PRAGMA journal_mode=DELETE")
            SchemaUtils.create(ProjectsTable)
        }
        repo = ProjectSettingsRepository()
    }

    afterTest {
        try {
            transaction(db) { SchemaUtils.drop(ProjectsTable) }
        } catch (_: Exception) {
        }

        try {
            TransactionManager.closeAndUnregister(db)
        } catch (_: Exception) {
        }
    }

    afterSpec {
        tempDirs.forEach {
            runCatching { it.toFile().deleteRecursively() }
        }
    }

    "loadExclusions" - {
        "returns empty list when project has no exclusions" {
            val projectId = "proj-no-excl"
            transaction(db) {
                ProjectsTable.insert {
                    it[id] = projectId
                    it[name] = "Test Project"
                    it[rootPath] = "/tmp/test"
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                    it[exclusions] = null
                }
            }
            val result = repo.loadExclusions(projectId)
            result.shouldBeEmpty()
        }

        "returns saved exclusions" {
            val projectId = "proj-with-excl"
            transaction(db) {
                ProjectsTable.insert {
                    it[id] = projectId
                    it[name] = "Test Project"
                    it[rootPath] = "/tmp/test"
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                    it[exclusions] = "foo\nbar"
                }
            }
            val result = repo.loadExclusions(projectId)
            result shouldHaveSize 2
            result shouldBe listOf("foo", "bar")
        }

        "returns empty list for non-existent project" {
            val result = repo.loadExclusions("nonexistent")
            result.shouldBeEmpty()
        }
    }

    "saveExclusions" - {
        "stores exclusions" {
            val projectId = "proj-save"
            transaction(db) {
                ProjectsTable.insert {
                    it[id] = projectId
                    it[name] = "Test Project"
                    it[rootPath] = "/tmp/test"
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                    it[exclusions] = null
                }
            }
            repo.saveExclusions(projectId, listOf("build", "dist"))
            val loaded = repo.loadExclusions(projectId)
            loaded shouldBe listOf("build", "dist")
        }

        "overwrites existing exclusions" {
            val projectId = "proj-overwrite"
            transaction(db) {
                ProjectsTable.insert {
                    it[id] = projectId
                    it[name] = "Test Project"
                    it[rootPath] = "/tmp/test"
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                    it[exclusions] = "old1\nold2"
                }
            }
            repo.saveExclusions(projectId, listOf("new1"))
            val loaded = repo.loadExclusions(projectId)
            loaded shouldHaveSize 1
            loaded shouldBe listOf("new1")
        }

        "sets null for empty list" {
            val projectId = "proj-empty"
            transaction(db) {
                ProjectsTable.insert {
                    it[id] = projectId
                    it[name] = "Test Project"
                    it[rootPath] = "/tmp/test"
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                    it[exclusions] = "some\nstuff"
                }
            }
            repo.saveExclusions(projectId, emptyList())
            val result = repo.loadExclusions(projectId)
            result.shouldBeEmpty()
        }
    }
})
