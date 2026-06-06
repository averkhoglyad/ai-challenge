package io.averkhogliad.ai.challenge.utils.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Источник конфигурации из файла на диске.
 *
 * Загружает конфигурацию из указанного файла. Если файл не существует,
 * возвращает `null` (опциональное поведение).
 *
 * @param path путь к файлу конфигурации.
 * @param required если `true`, выбрасывает исключение при отсутствии файла;
 *                 если `false` (по умолчанию), возвращает `null`.
 */
class FileConfigSource(
    private val path: Path,
    private val required: Boolean = false,
) : ConfigSource {

    /**
     * Создаёт источник из строкового пути.
     *
     * @param path строковый путь к файлу конфигурации.
     * @param required если `true`, выбрасывает исключение при отсутствии файла.
     */
    constructor(path: String, required: Boolean = false) :
        this(Path.of(path), required)

    override fun load(): Config? {
        if (!Files.exists(path)) {
            if (required) {
                throw IllegalStateException("Required config file not found: $path")
            }
            return null
        }
        return PropertiesConfig.fromFile(path)
    }

    override fun toString(): String =
        "FileConfigSource(path=$path, required=$required)"
}
