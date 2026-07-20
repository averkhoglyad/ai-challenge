package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.application.fileops.ProjectSettingsRepository
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.error.asFailure
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CollectDefaultSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.IndexSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.AppStateRepository
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.logging.Logger

class OpenProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val appStateRepository: AppStateRepository,
    private val sourceRepository: IndexedSourceRepository? = null,
    private val collectDefaultSourcesUseCase: CollectDefaultSourcesUseCase? = null,
    private val indexSourcesUseCase: IndexSourcesUseCase? = null,
    private val projectSettingsRepository: ProjectSettingsRepository? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val logger = Logger.getLogger(OpenProjectUseCase::class.java.name)

    companion object {
        private const val ACTIVE_PROJECT_KEY = "active_project_id"
    }

    suspend fun execute(path: String): DomainResult<Project> {
        val normalizedPath = Path.of(path).toAbsolutePath().normalize()

        if (!Files.exists(normalizedPath)) {
            return DomainResult.Failure(DomainError.pathNotFound(normalizedPath.toString()))
        }
        if (!Files.isDirectory(normalizedPath)) {
            return DomainResult.Failure(DomainError.notDirectory(normalizedPath.toString()))
        }

        val existing = projectRepository.findByPath(normalizedPath.toString())
        if (existing.isFailure) return existing.asFailure()

        val p = existing.getOrNull()
        val project: Project = if (p != null) {
            p
        } else {
            val name = resolveProjectName(normalizedPath)
            val now = Instant.now()
            val newProject = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                rootPath = normalizedPath,
                createdAt = now,
                updatedAt = now,
            )
            when (val saved = projectRepository.save(newProject)) {
                is DomainResult.Success -> {
                    projectSettingsRepository?.let { repo ->
                        val fromProps = loadExclusionsFromProperties()
                        repo.saveExclusions(newProject.id, fromProps)
                    }
                    saved.value
                }
                is DomainResult.Failure -> return DomainResult.Failure(saved.error)
            }
        }

        val setResult = appStateRepository.setValue(ACTIVE_PROJECT_KEY, project.id)
        if (setResult.isFailure) return setResult.asFailure()

        val srcRepo = sourceRepository
        val defaultsUC = collectDefaultSourcesUseCase
        val indexUC = indexSourcesUseCase
        if (srcRepo != null && defaultsUC != null && indexUC != null) {
            scope.launch {
                try {
                    val defaults = defaultsUC.execute(project.id, project.rootPath)
                    defaults.forEach { srcRepo.addSource(it) }
                    indexUC.execute(defaults, project.id, project.rootPath).collect { /* background */ }
                } catch (e: Exception) {
                    logger.warning("Background indexing failed: ${e.message}")
                }
            }
        }

        return DomainResult.Success(project)
    }

    private fun loadExclusionsFromProperties(): List<String> {
        return try {
            javaClass.classLoader.getResourceAsStream("app.properties")?.use { stream ->
                val props = java.util.Properties()
                props.load(stream)
                val raw = props.getProperty("fileops.exclusions", "")
                raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveProjectName(path: Path): String {
        val gitConfig = path.resolve(".git").resolve("config")
        return try {
            val content = Files.readString(gitConfig)
            val urlLine = content.lines().firstOrNull { "url" in it }
            if (urlLine != null) {
                val url = urlLine.substringAfter("=").trim()
                url.substringAfterLast("/").removeSuffix(".git")
            } else {
                path.fileName.toString()
            }
        } catch (_: Exception) {
            path.fileName.toString()
        }
    }
}
