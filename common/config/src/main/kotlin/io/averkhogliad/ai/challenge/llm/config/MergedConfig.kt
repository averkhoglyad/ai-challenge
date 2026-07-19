package io.averkhogliad.ai.challenge.llm.config

/**
 * Объединяет несколько [Config] в один с каскадным переопределением.
 *
 * Источники перечислены в порядке **возрастания** приоритета:
 * каждый следующий источник переопределяет ключи из предыдущих.
 *
 * Допускается пустой список источников — в этом случае [getOrNull] возвращает `null`
 * для всех ключей, а [keys] возвращает пустое множество.
 *
 * Пример:
 * ```kotlin
 * val config = MergedConfig(
 *     defaults,      // приоритет 1 (низший)
 *     userConfig,    // приоритет 2
 *     projectConfig, // приоритет 3
 *     cliConfig,     // приоритет 4 (высший)
 * )
 * config.get("api.key") // ищется в cliConfig → projectConfig → userConfig → defaults
 *
 * // Пустой список источников
 * val emptyConfig = MergedConfig(emptyList())
 * emptyConfig.getOrNull("any.key") // null
 * ```
 */
class MergedConfig(
    private val sources: List<Config>,
) : Config {

    constructor(vararg sources: Config) : this(sources.toList())

    override fun getOrNull(key: String): String? {
        // Ищем с конца (высший приоритет) к началу (низший приоритет)
        for (i in sources.indices.reversed()) {
            val value = sources[i].getOrNull(key)
            if (value != null) return value
        }
        return null
    }

    override fun keys(): Set<String> =
        sources.flatMap { it.keys() }.toSet()

    override fun toString(): String =
        "MergedConfig(sources=${sources.size}, keys=${keys().size})"
}
