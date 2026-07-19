package io.averkhogliad.ai.challenge.llm.config

/**
 * Источник конфигурации из classpath-ресурса.
 *
 * Загружает файл конфигурации из classpath приложения (например, из JAR-файла).
 *
 * @param resourceName имя ресурса для загрузки.
 * @param classLoader ClassLoader для загрузки ресурса (по умолчанию — контекстный ClassLoader текущего потока).
 */
class ClasspathConfigSource(
    private val resourceName: String,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) : ConfigSource {

    override fun load(): Config? {
        val stream = classLoader.getResourceAsStream(resourceName) ?: return null
        return stream.use { PropertiesConfig.fromStream(it) }
    }

    override fun toString(): String =
        "ClasspathConfigSource(resourceName='$resourceName')"
}
