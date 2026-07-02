package io.averkhogliad.ai.challenge.common.test

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.equals.beEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import java.time.Instant

/**
 * Общие Kotest-матчеры для тестовых модулей проекта.
 */

/**
 * Проверяет, что [Instant] попадает в диапазон включительно.
 */
fun betweenInclusive(fromInstant: Instant, toInstant: Instant): Matcher<Instant> =
    object : Matcher<Instant> {
        override fun test(value: Instant): MatcherResult {
            return MatcherResult(
                value in fromInstant..toInstant,
                { "$value should be after or equal to $fromInstant and before or equal to $toInstant" },
                { "$value should be after or equal to $fromInstant and before or equal to $toInstant" }
            )
        }
    }

/**
 * Сравнивает значения и корректно обрабатывает случай, когда ожидаемое значение равно `null`.
 */
infix fun <A> A.shouldBeEqual(expected: A?): A? {
    if (expected == null) this.shouldBeNull()
    else this should beEqual(expected)
    return this
}
