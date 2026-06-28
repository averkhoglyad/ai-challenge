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
 * Executes SQL scripts from classpath resources.
 * 
 * Usage:
 * ```kotlin
 * beforeEach { executeSql("/init.sql") }
 * afterEach { executeSql("/clear.sql") }
 * ```
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
 * Creates a TestCaseExtension that executes SQL scripts before/after each test.
 * 
 * Usage:
 * ```kotlin
 * class MyTest : FreeSpec({
 *     extension(sql(before = listOf("/init.sql"), after = listOf("/clear.sql")))
 *     
 *     "test case" { ... }
 * })
 * ```
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
