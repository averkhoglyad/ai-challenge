package io.averkhogliad.ai.challenge.llm.config

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Реализация [Config] на основе [java.util.Properties].
 *
 * Может быть создана из:
 * - [InputStream] (например, из classpath-ресурса);
 * - файла на диске ([Path]);
 * - готового объекта [Properties].
 */
class PropertiesConfig private constructor(
    private val properties: Properties,
) : Config {

    override fun getOrNull(key: String): String? =
        properties.getProperty(key)

    override fun keys(): Set<String> =
        properties.stringPropertyNames()

    override fun toString(): String =
        "PropertiesConfig(keys=${keys().size})"

    companion object {

        /**
         * Загружает конфигурацию из [InputStream].
         * Поток **не** закрывается — это ответственность вызывающего кода.
         */
        fun fromStream(stream: InputStream): PropertiesConfig {
            val props = Properties()
            props.load(stream)
            return PropertiesConfig(props)
        }

        /**
         * Загружает конфигурацию из файла по пути [path].
         *
         * @throws java.nio.file.NoSuchFileException если файл не существует.
         */
        fun fromFile(path: Path): PropertiesConfig {
            Files.newInputStream(path).use { stream ->
                return fromStream(stream)
            }
        }

        /**
         * Создаёт [PropertiesConfig] из готового объекта [Properties].
         */
        fun fromProperties(properties: Properties): PropertiesConfig =
            PropertiesConfig(properties)

        /**
         * Создаёт пустой [PropertiesConfig].
         */
        fun empty(): PropertiesConfig =
            PropertiesConfig(Properties())
    }
}
