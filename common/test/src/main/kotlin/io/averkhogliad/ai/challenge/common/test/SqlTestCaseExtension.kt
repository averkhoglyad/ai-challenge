package io.averkhogliad.ai.challenge.common.test

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.spring.testContextManager
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX
import javax.sql.DataSource

/**
 * Выполняет SQL-скрипты из classpath в контексте текущего Spring-теста.
 *
 * Используется в интеграционных тестах модулей `week-3`, когда нужно быстро
 * подготовить или очистить состояние БД без дублирования boilerplate-кода.
 */
suspend fun executeSql(vararg scripts: String) {
    executeSql(listOf(*scripts))
}

suspend fun executeSql(scripts: Iterable<String>) {
    withTestApplicationContext {
        useDataSource {
            populateDataSourceWithScripts(it, scripts)
        }
    }
}

/**
 * Создаёт [TestCaseExtension], который выполняет SQL-скрипты до и после теста.
 *
 * Подходит для сценариев вида "поднять тестовые данные → выполнить кейс → очистить БД".
 */
fun sql(before: List<String> = emptyList(), after: List<String> = emptyList()): TestCaseExtension {
    return SqlTestCaseExtension(before, after)
}

private class SqlTestCaseExtension(
    private val before: List<String> = emptyList(),
    private val after: List<String> = emptyList(),
) : TestCaseExtension {

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult {
        return withTestApplicationContext {
            useDataSource { ds ->
                populateDataSourceWithScripts(ds, before)
                val result = execute(testCase)
                populateDataSourceWithScripts(ds, after)
                result
            }
        }
    }
}

private suspend inline fun <R> withTestApplicationContext(block: suspend ApplicationContext.() -> R): R {
    return testContextManager()
        .testContext
        .applicationContext
        .block()
}

private suspend inline fun <R> ApplicationContext.useDataSource(block: suspend (DataSource) -> R): R {
    return block(getBean(DataSource::class.java))
}

private fun ApplicationContext.resourceDatabasePopulatorFor(scripts: Iterable<String>): ResourceDatabasePopulator {
    return scripts
        .map { environment.resolveRequiredPlaceholders(it) }
        .map { getResource("${CLASSPATH_URL_PREFIX}$it") }
        .toTypedArray()
        .let { ResourceDatabasePopulator(*it) }
}

private fun ApplicationContext.populateDataSourceWithScripts(dataSource: DataSource, scripts: Iterable<String>) {
    val populator = resourceDatabasePopulatorFor(scripts)
    DatabasePopulatorUtils.execute(populator, dataSource)
}
