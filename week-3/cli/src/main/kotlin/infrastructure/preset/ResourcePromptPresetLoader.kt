package io.averkhogliad.ai.challenge.week3.cli.infrastructure.preset

import io.averkhogliad.ai.challenge.week3.cli.domain.model.PromptPreset
import io.averkhogliad.ai.challenge.week3.cli.domain.model.PromptSource
import io.averkhogliad.ai.challenge.week3.cli.domain.service.PromptPresetProvider
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.preset.ResourcePromptPresetLoader.Companion.PRESET_PATHS

/**
 * Загружает BUILTIN preset'ы из resources/presets/.
 *
 * Использует явный список известных файлов (jar-safe) вместо directory scanning.
 * При добавлении нового preset'а — добавить путь в [PRESET_PATHS].
 */
class ResourcePromptPresetLoader : PromptPresetProvider {

    companion object {
        /** Известные BUILTIN preset'ы. Добавлять сюда новые .md файлы из resources/presets/. */
        private val PRESET_PATHS = listOf("presets/smart-task-planner.md")
    }

    override suspend fun load(): List<PromptPreset> {
        val result = mutableListOf<PromptPreset>()
        for (path in PRESET_PATHS) {
            try {
                val content = readResource(path)
                if (content != null) {
                    FrontMatterParser.parse(content, PromptSource.BUILTIN)?.let { result.add(it) }
                } else {
                    System.err.println("[PRESET-LOADER] Пресет не найден в resources: $path")
                }
            } catch (e: Exception) {
                System.err.println("[PRESET-LOADER] Ошибка чтения пресета $path: ${e.message}")
            }
        }
        return result
    }

    /** Читает ресурс через classLoader (работает из jar и exploded). */
    private fun readResource(path: String): String? {
        return javaClass.classLoader.getResourceAsStream(path)?.use { stream ->
            stream.bufferedReader().readText()
        }
    }
}
