package io.averkhogliad.ai.challenge.utils.llm

import kotlin.test.*

class DefaultLlmClientTest {

    private fun createTestConfig(): LlmClientConfig = LlmClientConfig(
        baseUrl = "https://api.example.com",
        apiKey = "test-key",
        model = "test-model",
        connectTimeout = kotlin.time.Duration.parse("PT10S"),
        requestTimeout = kotlin.time.Duration.parse("PT30S"),
        rateLimitEnabled = false,
        minInterval = kotlin.time.Duration.parse("PT0.5S"),
        maxRequestsPerMinute = 60
    )

    private fun createClient(): DefaultLlmClient = DefaultLlmClient(createTestConfig())

    // ==================== parseResponse tests ====================

    @Test
    fun `parseResponse - valid response with usage`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "Hello, world!"
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 20,
                    "total_tokens": 30
                }
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertEquals("Hello, world!", response.content)
        assertEquals("stop", response.finishReason)
        assertNotNull(response.usage)
        assertEquals(10, response.usage!!.promptTokens)
        assertEquals(20, response.usage!!.completionTokens)
        assertEquals(30, response.usage!!.totalTokens)
    }

    @Test
    fun `parseResponse - valid response without usage`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "Simple response"
                        },
                        "finish_reason": "length"
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertEquals("Simple response", response.content)
        assertEquals("length", response.finishReason)
        assertNull(response.usage)
    }

    @Test
    fun `parseResponse - finish_reason is null`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "No finish reason"
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertEquals("No finish reason", response.content)
        assertNull(response.finishReason)
    }

    @Test
    fun `parseResponse - missing choices field throws`() {
        val client = createClient()
        val json = """{"no_choices": []}"""

        val exception = assertFailsWith<LlmException> {
            client.parseResponse(json)
        }
        assertTrue(exception.message!!.contains("choices"))
    }

    @Test
    fun `parseResponse - empty choices array throws`() {
        val client = createClient()
        val json = """{"choices": []}"""

        val exception = assertFailsWith<LlmException> {
            client.parseResponse(json)
        }
        assertTrue(exception.message!!.contains("empty"))
    }

    @Test
    fun `parseResponse - missing message field throws`() {
        val client = createClient()
        val json = """{"choices": [{"no_message": "oops"}]}"""

        val exception = assertFailsWith<LlmException> {
            client.parseResponse(json)
        }
        assertTrue(exception.message!!.contains("message"))
    }

    @Test
    fun `parseResponse - missing content field throws`() {
        val client = createClient()
        val json = """{"choices": [{"message": {"no_content": "oops"}}]}"""

        val exception = assertFailsWith<LlmException> {
            client.parseResponse(json)
        }
        assertTrue(exception.message!!.contains("content"))
    }

    @Test
    fun `parseResponse - invalid JSON throws`() {
        val client = createClient()
        val json = "this is not json"

        val exception = assertFailsWith<LlmException> {
            client.parseResponse(json)
        }
        assertTrue(exception.message!!.contains("JSON"))
    }

    @Test
    fun `parseResponse - usage with zero tokens`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": ""
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 0,
                    "completion_tokens": 0,
                    "total_tokens": 0
                }
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertEquals("", response.content)
        assertNotNull(response.usage)
        assertEquals(0, response.usage!!.totalTokens)
    }

    @Test
    fun `parseResponse - multiple choices uses first one`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "first response"
                        },
                        "finish_reason": "stop"
                    },
                    {
                        "message": {
                            "content": "second response"
                        },
                        "finish_reason": "length"
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertEquals("first response", response.content)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun `parseResponse - usage with missing fields defaults to zero`() {
        val client = createClient()
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "test"
                        }
                    }
                ],
                "usage": {}
            }
        """.trimIndent()

        val response = client.parseResponse(json)

        assertNotNull(response.usage)
        assertEquals(0, response.usage!!.promptTokens)
        assertEquals(0, response.usage!!.completionTokens)
        assertEquals(0, response.usage!!.totalTokens)
    }

    // ==================== sanitizeError tests ====================

    @Test
    fun `sanitizeError - masks Bearer token`() {
        val client = createClient()
        val error = "Authorization: Bearer sk-abc123def456ghijklmnopqrstuvwxyz"

        val sanitized = client.sanitizeError(error)

        assertTrue(sanitized.contains("Bearer ***"))
        assertTrue(!sanitized.contains("sk-abc123"))
    }

    @Test
    fun `sanitizeError - masks api_key in JSON`() {
        val client = createClient()
        val error = """{"api_key": "my-secret-key-12345"}"""

        val sanitized = client.sanitizeError(error)

        assertTrue(sanitized.contains("\"api_key\": \"***\""))
        assertTrue(!sanitized.contains("my-secret-key"))
    }

    @Test
    fun `sanitizeError - masks key field with sk- prefix in JSON`() {
        val client = createClient()
        val error = """{"key": "sk-abcdef1234567890"}"""

        val sanitized = client.sanitizeError(error)

        assertTrue(sanitized.contains("\"key\": \"***\""))
        assertTrue(!sanitized.contains("sk-abcdef1234567890"))
    }

    @Test
    fun `sanitizeError - truncates long messages`() {
        val client = createClient()
        val longError = "x".repeat(500)

        val sanitized = client.sanitizeError(longError)

        assertTrue(sanitized.startsWith("x".repeat(200)))
        assertTrue(sanitized.length > 200 && sanitized.length < 220) // 200 + "... (truncated)"
        assertTrue(sanitized.endsWith("... (truncated)"))
    }

    @Test
    fun `sanitizeError - does not truncate short messages`() {
        val client = createClient()
        val shortError = "Short error message"

        val sanitized = client.sanitizeError(shortError)

        assertEquals("Short error message", sanitized)
    }

    @Test
    fun `sanitizeError - handles empty string`() {
        val client = createClient()
        val sanitized = client.sanitizeError("")

        assertEquals("", sanitized)
    }

    @Test
    fun `sanitizeError - multiple patterns in one string`() {
        val client = createClient()
        val error = """
            Authorization: Bearer sk-token12345
            {"api_key": "secret-api-key", "key": "sk-secret-key-value"}
        """.trimIndent()

        val sanitized = client.sanitizeError(error)

        assertTrue(!sanitized.contains("sk-token12345"))
        assertTrue(!sanitized.contains("secret-api-key"))
        assertTrue(!sanitized.contains("sk-secret-key-value"))
        assertTrue(sanitized.contains("Bearer ***"))
        assertTrue(sanitized.contains("\"api_key\": \"***\""))
        assertTrue(sanitized.contains("\"key\": \"***\""))
    }

    @Test
    fun `sanitizeError - key field without sk- prefix is not masked`() {
        val client = createClient()
        val error = """
            {"key": "rate_limit_exceeded"}
        """.trimIndent()

        val sanitized = client.sanitizeError(error)

        // Значения без префикса sk- не маскируются,
        // чтобы не терять диагностическую информацию в сообщениях об ошибках
        assertTrue(sanitized.contains("rate_limit_exceeded"))
        assertTrue(!sanitized.contains("***"))
    }

    @Test
    fun `sanitizeError - text without sensitive data passes through`() {
        val client = createClient()
        val error = "HTTP 500: Internal Server Error"

        val sanitized = client.sanitizeError(error)

        assertEquals(error, sanitized)
    }
}
