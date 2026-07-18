package io.averkhogliad.cli.repl.mordant.common

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertTrue

class TableRendererTest {

    @Test
    fun `table renderer formats data correctly`() {
        val terminal = Terminal(AnsiLevel.NONE)
        val renderer = object : TableRenderer<List<Pair<String, Int>>>(terminal) {
            override fun headers() = listOf("Name", "Value")

            override fun rows(data: List<Pair<String, Int>>) =
                data.map { listOf(it.first, it.second.toString()) }
        }

        val result = renderer.render(listOf("A" to 1, "B" to 2))

        assertTrue(result.contains("Name"), "Expected header 'Name' in: $result")
        assertTrue(result.contains("Value"), "Expected header 'Value' in: $result")
        assertTrue(result.contains("A"), "Expected row value 'A' in: $result")
        assertTrue(result.contains("1"), "Expected row value '1' in: $result")
        val namePos = result.indexOf("Name")
        val valuePos = result.indexOf("Value")
        val firstDataPos = result.indexOf("A")
        assertTrue(namePos < firstDataPos, "Headers should appear before data in: $result")
        assertTrue(valuePos < firstDataPos, "Headers should appear before data in: $result")
    }

    @Test
    fun `empty data renders headers only`() {
        val terminal = Terminal(AnsiLevel.NONE)
        val renderer = object : TableRenderer<List<Pair<String, Int>>>(terminal) {
            override fun headers() = listOf("Name", "Value")

            override fun rows(data: List<Pair<String, Int>>) =
                data.map { listOf(it.first, it.second.toString()) }
        }

        val result = renderer.render(emptyList())

        assertTrue(result.contains("Name"), "Expected header 'Name' in: $result")
        assertTrue(result.contains("Value"), "Expected header 'Value' in: $result")
    }
}
