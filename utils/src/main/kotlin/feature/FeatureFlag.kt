package io.averkhogliad.ai.challenge.utils.feature

/**
 * # Feature Flag Infrastructure
 *
 * Инфраструктура для управления функциональными флагами (Feature Flags) в проекте.
 * Предоставляет механизм включения и отключения функциональности в runtime
 * без необходимости перекомпиляции или перезапуска приложения.
 *
 * ## Назначение
 *
 * Feature Flags позволяют:
 * - **Постепенно внедрять** новые функции (canary releases)
 * - **A/B тестировать** различные варианты поведения
 * - **Быстро отключать** проблемную функциональность без деплоя (kill switch)
 * - **Управлять окружениями** — разное поведение для dev/staging/production
 * - **Изолировать незавершённый код** от production-пользователей
 *
 * ## Компоненты
 *
 * | Компонент | Назначение |
 * |-----------|------------|
 * | [FeatureFlag] | Enum всех флагов с ключами, значениями по умолчанию и описанием |
 * | [FeatureFlagProvider] | Абстрактный интерфейс получения значений флагов |
 * | [ConfigFeatureFlagProvider] | Реализация на основе [Config] (чтение из application.properties) |
 * | [FeatureFlagManager] | Менеджер с поддержкой runtime-переопределения (приоритет: override -> config -> default) |
 *
 * ## Использование
 *
 * ```kotlin
 * // 1. Определение флага (в FeatureFlag enum)
 * enum class FeatureFlag(...) {
 *     ENABLE_NEW_FEATURE("feature.enable-new-feature", false, "Enables the new feature"),
 * }
 *
 * // 2. Конфигурация в application.properties
 * // feature.enable-new-feature=true
 *
 * // 3. Проверка в коде
 * val flagManager = FeatureFlagManager(config)
 * if (flagManager.isEnabled(FeatureFlag.ENABLE_NEW_FEATURE)) {
 *     // новая логика
 * } else {
 *     // старая логика
 * }
 *
 * // 4. Runtime-переопределение (например, в тестах)
 * flagManager.setOverride(FeatureFlag.ENABLE_NEW_FEATURE, true)
 * ```
 *
 * ## Приоритет разрешения значений
 *
 * 1. **Runtime override** (установлен через [FeatureFlagManager.setOverride]) — наивысший приоритет
 * 2. **Значение из конфигурации** (application.properties / [Config])
 * 3. **Значение по умолчанию** ([FeatureFlag.defaultValue])
 *
 * Эта инфраструктура является частью модуля `utils` и может использоваться
 * всеми другими модулями проекта для управления функциональными флагами.
 *
 * @see FeatureFlag
 * @see FeatureFlagProvider
 * @see FeatureFlagManager
 */
import io.averkhogliad.ai.challenge.utils.config.Config

/**
 * Перечисление всех Feature Flags в проекте.
 *
 * Каждый флаг имеет:
 * - [key] — строковый ключ для чтения из конфигурации (с префиксом `feature.`)
 * - [defaultValue] — значение по умолчанию, используемое когда флаг не задан в конфигурации
 * - [description] — человекочитаемое описание назначения флага
 *
 * Пример конфигурации в application.properties:
 * ```
 * feature.enable-decompose-gui=false
 * ```
 */
enum class FeatureFlag(
    val key: String,
    val defaultValue: Boolean,
    val description: String,
) {
    /**
     * Включение GUI на базе Decompose (в будущем).
     *
     * `false` (по умолчанию) — используется текущий GUI.
     * `true` — используется GUI на Decompose.
     */
    ENABLE_DECOMPOSE_GUI(
        key = "feature.enable-decompose-gui",
        defaultValue = false,
        description = "Enables Decompose-based GUI (future)",
    ),

    /**
     * Включение оптимизированных промптов для LLM.
     *
     * `false` (по умолчанию) — используются стандартные промпты.
     * `true` — используются оптимизированные промпты.
     */
    ENABLE_OPTIMIZED_PROMPTS(
        key = "feature.enable-optimized-prompts",
        defaultValue = false,
        description = "Enables optimized prompts for LLM interactions",
    ),
}

/**
 * Абстрактный провайдер значений Feature Flags.
 *
 * Отделяет логику получения значений флагов от конкретного источника
 * (конфигурационный файл, переменные окружения, база данных и т.д.).
 *
 * Реализации:
 * - [ConfigFeatureFlagProvider] — чтение из [Config]
 * - [FeatureFlagManager] — чтение с поддержкой runtime-переопределения
 */
interface FeatureFlagProvider {

    /**
     * Возвращает текущее значение [flag].
     *
     * @return `true` если флаг включён, `false` если выключен.
     */
    fun isEnabled(flag: FeatureFlag): Boolean
}

/**
 * Реализация [FeatureFlagProvider] на основе существующего [Config].
 *
 * Читает значения флагов из конфигурации по ключу [FeatureFlag.key].
 * Если ключ отсутствует в конфигурации, возвращает [FeatureFlag.defaultValue].
 *
 * Использование:
 * ```kotlin
 * val config: Config = configProvider.load()
 * val flagProvider = ConfigFeatureFlagProvider(config)
 *
 * if (flagProvider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI)) {
 *     // использовать Decompose GUI
 * } else {
 *     // использовать текущий GUI
 * }
 * ```
 */
class ConfigFeatureFlagProvider(
    private val config: Config,
) : FeatureFlagProvider {

    override fun isEnabled(flag: FeatureFlag): Boolean =
        config.getOrNull(flag.key)?.toBooleanStrictOrNull() ?: flag.defaultValue

    companion object {
        /**
         * Парсит строку в Boolean.
         * Принимает `"true"` (case-insensitive) и `"false"` (case-insensitive).
         * Возвращает `null` для всех остальных значений.
         */
        private fun String.toBooleanStrictOrNull(): Boolean? =
            when (lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
    }
}

/**
 * Менеджер Feature Flags с поддержкой runtime-переопределения.
 *
 * Позволяет временно включать/выключать флаги во время работы приложения
 * без перезагрузки конфигурации. Это особенно полезно для:
 * - тестирования (включение/выключение флагов в тестах)
 * - A/B тестирования
 * - оперативного включения/выключения функциональности
 *
 * Приоритет значений (от высшего к низшему):
 * 1. Runtime override (установлен через [setOverride])
 * 2. Значение из [Config] (через [ConfigFeatureFlagProvider])
 * 3. [FeatureFlag.defaultValue]
 *
 * Использование:
 * ```kotlin
 * val manager = FeatureFlagManager(config)
 *
 * // Чтение значения
 * if (manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI)) { ... }
 *
 * // Runtime переопределение
 * manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
 * // Теперь isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI) == true
 *
 * // Сброс переопределения
 * manager.clearOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI)
 * // Возврат к значению из конфигурации
 * ```
 */
class FeatureFlagManager(
    config: Config,
) : FeatureFlagProvider {

    private val configProvider = ConfigFeatureFlagProvider(config)
    private val overrides: MutableMap<FeatureFlag, Boolean> =
        java.util.concurrent.ConcurrentHashMap<FeatureFlag, Boolean>()

    /**
     * Возвращает текущее значение [flag] с учётом runtime-переопределений.
     */
    override fun isEnabled(flag: FeatureFlag): Boolean {
        overrides[flag]?.let { return it }
        return configProvider.isEnabled(flag)
    }

    /**
     * Устанавливает runtime-переопределение для [flag].
     *
     * После вызова [isEnabled] для этого флага будет возвращать [value],
     * независимо от значения в конфигурации.
     */
    fun setOverride(flag: FeatureFlag, value: Boolean) {
        overrides[flag] = value
    }

    /**
     * Удаляет runtime-переопределение для [flag].
     *
     * После вызова [isEnabled] для этого флага будет возвращать значение
     * из конфигурации (или default, если в конфигурации отсутствует).
     */
    fun clearOverride(flag: FeatureFlag) {
        overrides.remove(flag)
    }

    /**
     * Удаляет все runtime-переопределения.
     */
    fun clearAllOverrides() {
        overrides.clear()
    }

    /**
     * Возвращает `true`, если для [flag] установлено runtime-переопределение.
     */
    fun hasOverride(flag: FeatureFlag): Boolean =
        flag in overrides

    /**
     * Возвращает неизменяемую копию всех текущих переопределений.
     */
    fun getOverrides(): Map<FeatureFlag, Boolean> =
        overrides.toMap()
}
