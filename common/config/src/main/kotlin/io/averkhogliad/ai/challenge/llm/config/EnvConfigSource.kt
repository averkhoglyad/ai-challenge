package io.averkhogliad.ai.challenge.llm.config

/**
 * Источник конфигурации из переменных окружения.
 *
 * Загружает переменные окружения, начинающиеся с заданного префикса,
 * и преобразует их в ключи конфигурации:
 * - отбрасывается префикс
 * - переводится в нижний регистр
 * - `_` заменяется на `.`
 *
 * Пример с префиксом `LLM_`:
 * - `LLM_BASE_URL` → ключ `base.url`
 * - `LLM_API_KEY`  → ключ `api.key`
 * - `LLM_MODEL`    → ключ `model`
 *
 * @param prefix префикс, по которому отбираются переменные окружения.
 *               Если `null` — загружаются все переменные окружения без преобразования имён.
 */
class EnvConfigSource(
    private val prefix: String? = null,
) : ConfigSource {

    override fun load(): Config? {
        val env = System.getenv()
        if (env.isEmpty()) return null

        val props = java.util.Properties()
        for ((key, value) in env) {
            val configKey = if (prefix != null) {
                if (!key.startsWith(prefix)) continue
                key.removePrefix(prefix).lowercase().replace('_', '.')
            } else {
                key
            }
            props.setProperty(configKey, value)
        }
        return if (props.isEmpty) null else PropertiesConfig.fromProperties(props)
    }

    override fun toString(): String =
        "EnvConfigSource(prefix=$prefix)"
}
