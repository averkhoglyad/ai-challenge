package io.averkhogliad.ai.challenge.utils.config

/**
 * Провайдер конфигурации с поддержкой каскадного переопределения из нескольких источников.
 *
 * Источники обрабатываются в порядке их добавления.
 * Каждый следующий источник переопределяет значения из предыдущих.
 *
 * Метод [addSource] поддерживает vararg, позволяя добавлять несколько источников за один вызов.
 *
 * Пример использования:
 * ```kotlin
 * val provider = ConfigProvider()
 *     .addSource(
 *         ClasspathConfigSource("application.properties"),
 *         FileConfigSource(Path.of(System.getProperty("user.home"), ".my-app", "application.properties")),
 *         FileConfigSource("application.properties")
 *     )
 *
 * val config = provider.load()
 * ```
 *
 * Если ни один источник не доступен (все вернули `null`), метод [load] возвращает
 * пустой [Config] на основе [MergedConfig] с пустым списком источников.
 */
class ConfigProvider {

    private val sources = mutableListOf<ConfigSource>()

    /**
     * Добавляет один или несколько источников конфигурации в провайдер.
     *
     * @param sources источники конфигурации для добавления.
     * @return этот провайдер для цепочки вызовов.
     */
    fun addSource(vararg sources: ConfigSource): ConfigProvider {
        this.sources.addAll(sources)
        return this
    }

    /**
     * Загружает и объединяет конфигурацию из всех добавленных источников.
     *
     * Источники обрабатываются в порядке их добавления.
     * Каждый следующий источник переопределяет значения из предыдущих.
     *
     * @return объединённый [Config] с каскадным переопределением.
     *         Если источников нет или все они вернули `null`, возвращает пустой [Config].
     */
    fun load(): Config =
        MergedConfig(sources.mapNotNull { it.load() })

    /**
     * Возвращает количество добавленных источников.
     */
    fun sourceCount(): Int = sources.size

    /**
     * Очищает список добавленных источников.
     *
     * @return этот провайдер для цепочки вызовов.
     */
    fun clearSources(): ConfigProvider {
        sources.clear()
        return this
    }
}
