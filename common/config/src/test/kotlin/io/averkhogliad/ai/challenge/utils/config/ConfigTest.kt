package io.averkhogliad.ai.challenge.utils.config

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.writeText
import kotlin.test.*

internal class ConfigTest {

    // ── PropertiesConfig ──────────────────────────────────────────────

    @Test
    fun `PropertiesConfig fromStream reads properties`() {
        val content = "api.key=secret\napi.model=gpt-4"
        val stream = ByteArrayInputStream(content.toByteArray())
        val config = PropertiesConfig.fromStream(stream)

        assertEquals("secret", config.getOrNull("api.key"))
        assertEquals("gpt-4", config.getOrNull("api.model"))
        assertNull(config.getOrNull("nonexistent"))
    }

    @Test
    fun `PropertiesConfig fromFile reads file`() {
        val tempFile = Files.createTempFile("config-test", ".properties")
        try {
            tempFile.writeText("db.host=localhost\ndb.port=5432")
            val config = PropertiesConfig.fromFile(tempFile)

            assertEquals("localhost", config.get("db.host"))
            assertEquals("5432", config.get("db.port"))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `PropertiesConfig fromFile throws on missing file`() {
        val missing = Path.of("/tmp/nonexistent-config-file-${System.nanoTime()}.properties")
        assertFailsWith<java.nio.file.NoSuchFileException> {
            PropertiesConfig.fromFile(missing)
        }
    }

    @Test
    fun `PropertiesConfig empty returns null for all keys`() {
        val config = PropertiesConfig.empty()
        assertNull(config.getOrNull("any.key"))
        assertTrue(config.keys().isEmpty())
    }

    @Test
    fun `PropertiesConfig fromProperties wraps existing Properties`() {
        val props = Properties().apply {
            setProperty("x", "1")
            setProperty("y", "2")
        }
        val config = PropertiesConfig.fromProperties(props)

        assertEquals("1", config.get("x"))
        assertEquals("2", config.get("y"))
        assertEquals(setOf("x", "y"), config.keys())
    }

    // ── Config interface defaults ─────────────────────────────────────

    @Test
    fun `Config get throws NoSuchElementException for missing key`() {
        val config = PropertiesConfig.empty()
        assertFailsWith<NoSuchElementException> {
            config.get("missing")
        }
    }

    @Test
    fun `Config getOrDefault returns default for missing key`() {
        val config = PropertiesConfig.empty()
        assertEquals("fallback", config.getOrDefault("missing", "fallback"))
    }

    @Test
    fun `Config getOrDefault returns value when key exists`() {
        val stream = ByteArrayInputStream("key=value".toByteArray())
        val config = PropertiesConfig.fromStream(stream)
        assertEquals("value", config.getOrDefault("key", "fallback"))
    }

    @Test
    fun `Config contains returns correct results`() {
        val stream = ByteArrayInputStream("a=1".toByteArray())
        val config = PropertiesConfig.fromStream(stream)
        assertTrue(config.contains("a"))
        assertFalse(config.contains("b"))
    }

    // ── MergedConfig ──────────────────────────────────────────────────

    @Test
    fun `MergedConfig later sources override earlier ones`() {
        val low = propsConfig("key" to "low", "only-low" to "a")
        val high = propsConfig("key" to "high", "only-high" to "b")

        val merged = MergedConfig(low, high)

        assertEquals("high", merged.get("key"))
        assertEquals("a", merged.get("only-low"))
        assertEquals("b", merged.get("only-high"))
    }

    @Test
    fun `MergedConfig three levels of priority`() {
        val defaults = propsConfig("a" to "1", "b" to "1", "c" to "1")
        val user = propsConfig("b" to "2", "c" to "2")
        val cli = propsConfig("c" to "3")

        val merged = MergedConfig(defaults, user, cli)

        assertEquals("1", merged.get("a"))
        assertEquals("2", merged.get("b"))
        assertEquals("3", merged.get("c"))
    }

    @Test
    fun `MergedConfig keys returns union of all sources`() {
        val a = propsConfig("x" to "1")
        val b = propsConfig("y" to "2")
        val merged = MergedConfig(a, b)

        assertEquals(setOf("x", "y"), merged.keys())
    }

    @Test
    fun `MergedConfig with empty sources returns null for all keys`() {
        val merged = MergedConfig(emptyList())
        assertNull(merged.getOrNull("any.key"))
        assertTrue(merged.keys().isEmpty())
    }

    // ── ConfigSource implementations ──────────────────────────────────

    @Test
    fun `ClasspathConfigSource loads from classpath`() {
        val propsContent = "classpath.key=classpath-value\n"
        val classLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?) =
                if (name == "application.properties")
                    ByteArrayInputStream(propsContent.toByteArray())
                else null
        }
        val source = ClasspathConfigSource("application.properties", classLoader = classLoader)
        val config = source.load()

        assertNotNull(config)
        assertEquals("classpath-value", config.get("classpath.key"))
    }

    @Test
    fun `ClasspathConfigSource returns null when resource not found`() {
        val emptyClassLoader = object : ClassLoader() {
            override fun getResource(name: String?) = null
        }
        val source = ClasspathConfigSource("application.properties", classLoader = emptyClassLoader)
        val config = source.load()

        assertNull(config)
    }

    @Test
    fun `FileConfigSource loads from file`() {
        val tempFile = Files.createTempFile("file-source-test", ".properties")
        try {
            tempFile.writeText("file.key=file-value")
            val source = FileConfigSource(tempFile)
            val config = source.load()

            assertNotNull(config)
            assertEquals("file-value", config.get("file.key"))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `FileConfigSource returns null when file not found and not required`() {
        val missing = Path.of("/tmp/nonexistent-${System.nanoTime()}.properties")
        val source = FileConfigSource(missing, required = false)
        val config = source.load()

        assertNull(config)
    }

    @Test
    fun `FileConfigSource throws when file not found and required`() {
        val missing = Path.of("/tmp/nonexistent-${System.nanoTime()}.properties")
        val source = FileConfigSource(missing, required = true)

        assertFailsWith<IllegalStateException> {
            source.load()
        }
    }

    @Test
    fun `FileConfigSource accepts string path`() {
        val tempFile = Files.createTempFile("string-path-test", ".properties")
        try {
            tempFile.writeText("string.key=string-value")
            val source = FileConfigSource(tempFile.toString())
            val config = source.load()

            assertNotNull(config)
            assertEquals("string-value", config.get("string.key"))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // ── ConfigProvider ────────────────────────────────────────────────

    @Test
    fun `ConfigProvider loads from multiple sources`() {
        val tempFile1 = Files.createTempFile("provider-test-1", ".properties")
        val tempFile2 = Files.createTempFile("provider-test-2", ".properties")
        try {
            tempFile1.writeText("key1=value1\nshared=from-file1")
            tempFile2.writeText("key2=value2\nshared=from-file2")

            val provider = ConfigProvider()
                .addSource(FileConfigSource(tempFile1))
                .addSource(FileConfigSource(tempFile2))

            val config = provider.load()

            assertEquals("value1", config.get("key1"))
            assertEquals("value2", config.get("key2"))
            assertEquals("from-file2", config.get("shared")) // Later source wins
        } finally {
            Files.deleteIfExists(tempFile1)
            Files.deleteIfExists(tempFile2)
        }
    }

    @Test
    fun `ConfigProvider returns empty config when no sources`() {
        val provider = ConfigProvider()
        val config = provider.load()

        assertTrue(config.keys().isEmpty())
    }

    @Test
    fun `ConfigProvider skips null sources`() {
        val tempFile = Files.createTempFile("provider-null-test", ".properties")
        try {
            tempFile.writeText("key=value")
            val missing = Path.of("/tmp/nonexistent-${System.nanoTime()}.properties")

            val provider = ConfigProvider()
                .addSource(FileConfigSource(missing, required = false)) // Returns null
                .addSource(FileConfigSource(tempFile))

            val config = provider.load()

            assertEquals("value", config.get("key"))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `ConfigProvider sourceCount returns correct count`() {
        val provider = ConfigProvider()
        assertEquals(0, provider.sourceCount())

        provider.addSource(FileConfigSource("dummy1.properties"))
        assertEquals(1, provider.sourceCount())

        provider.addSource(FileConfigSource("dummy2.properties"))
        assertEquals(2, provider.sourceCount())
    }

    @Test
    fun `ConfigProvider clearSources removes all sources`() {
        val provider = ConfigProvider()
            .addSource(FileConfigSource("dummy1.properties"))
            .addSource(FileConfigSource("dummy2.properties"))

        assertEquals(2, provider.sourceCount())

        provider.clearSources()
        assertEquals(0, provider.sourceCount())
    }

    @Test
    fun `ConfigProvider addSource accepts multiple sources as vararg`() {
        val provider = ConfigProvider().addSource(
            FileConfigSource("dummy1.properties"),
            FileConfigSource("dummy2.properties"),
            FileConfigSource("dummy3.properties")
        )
        assertEquals(3, provider.sourceCount())
    }

    @Test
    fun `ConfigProvider later sources override earlier ones`() {
        val tempFile1 = Files.createTempFile("order-test-1", ".properties")
        val tempFile2 = Files.createTempFile("order-test-2", ".properties")
        val tempFile3 = Files.createTempFile("order-test-3", ".properties")
        try {
            tempFile1.writeText("key=first\nonly-first=a")
            tempFile2.writeText("key=second\nonly-second=b")
            tempFile3.writeText("key=third\nonly-third=c")

            val provider = ConfigProvider()
                .addSource(FileConfigSource(tempFile1))
                .addSource(FileConfigSource(tempFile2))
                .addSource(FileConfigSource(tempFile3))

            val config = provider.load()

            assertEquals("third", config.get("key")) // Last added wins
            assertEquals("a", config.get("only-first"))
            assertEquals("b", config.get("only-second"))
            assertEquals("c", config.get("only-third"))
        } finally {
            Files.deleteIfExists(tempFile1)
            Files.deleteIfExists(tempFile2)
            Files.deleteIfExists(tempFile3)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun propsConfig(vararg pairs: Pair<String, String>): Config {
        val props = Properties()
        pairs.forEach { (k, v) -> props.setProperty(k, v) }
        return PropertiesConfig.fromProperties(props)
    }
}
