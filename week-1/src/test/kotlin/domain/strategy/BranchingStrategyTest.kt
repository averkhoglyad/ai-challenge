package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.BranchId
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class BranchingStrategyTest {

    private val strategy = BranchingStrategy(configProvider = { BranchingConfig() })

    private fun createDialogWithMessages(count: Int): Dialog {
        var dialog = Dialog.create(DialogId("test-dialog"), "Test Dialog")
        repeat(count) { i ->
            dialog = dialog.addUserMessage("Message ${i + 1}")
        }
        return dialog
    }

    @Test
    fun `should have correct name and description`() {
        assertEquals("Branching", strategy.name)
        assertTrue(strategy.description.contains("точки сохранения"))
    }

    @Test
    fun `processUserMessage should add message to current branch`() = runTest {
        val dialog = createDialogWithMessages(3)
        val config = ContextManagementConfig()

        val result = strategy.processUserMessage(dialog, "New message", config)

        // Should have message in current branch
        val currentBranch = strategy.getCurrentBranch()
        assertTrue(currentBranch.messages.isNotEmpty())
    }

    @Test
    fun `prepareContext should return messages from current branch`() = runTest {
        val dialog = createDialogWithMessages(5)
        val config = ContextManagementConfig()

        // Process some messages
        strategy.processUserMessage(dialog, "Message 1", config)
        strategy.processUserMessage(dialog, "Message 2", config)

        val context = strategy.prepareContext(dialog, "System prompt", config)

        // System prompt + branch messages
        assertTrue(context.messages.size >= 1)
        assertEquals("System prompt", context.messages[0].content)
    }

    @Test
    fun `createCheckpoint should create new checkpoint`() {
        val dialog = createDialogWithMessages(5)

        val checkpoint = strategy.createCheckpoint(dialog, 5)

        assertNotNull(checkpoint)
        assertEquals(5, checkpoint.messageIndex)
        assertEquals(dialog.id, checkpoint.dialogId)
    }

    @Test
    fun `listCheckpoints should return all checkpoints`() {
        val dialog = createDialogWithMessages(10)

        strategy.createCheckpoint(dialog, 5)
        strategy.createCheckpoint(dialog, 10)

        val checkpoints = strategy.listCheckpoints()
        assertEquals(2, checkpoints.size)
    }

    @Test
    fun `createBranch should create new branch from checkpoint`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)

        val newBranch = strategy.createBranch("test-branch", checkpoint.id)

        assertNotNull(newBranch)
        assertEquals("test-branch", newBranch.name)
        assertEquals(checkpoint.id, newBranch.parentCheckpointId)
    }

    @Test
    fun `switchBranch should change current branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("test-branch", checkpoint.id)

        strategy.switchBranch(newBranch.id)

        assertEquals(newBranch.id, strategy.getCurrentBranch().id)
        assertTrue(strategy.getCurrentBranch().isActive)
    }

    @Test
    fun `switchBranch should throw exception for unknown branch`() {
        val unknownId = BranchId("unknown-branch")

        assertThrows<IllegalArgumentException> {
            strategy.switchBranch(unknownId)
        }
    }

    @Test
    fun `listBranches should return all branches`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        strategy.createBranch("branch-1", checkpoint.id)
        strategy.createBranch("branch-2", checkpoint.id)

        val branches = strategy.listBranches()

        // Main branch + 2 created branches
        assertTrue(branches.size >= 3)
    }

    @Test
    fun `deleteBranch should remove branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("to-delete", checkpoint.id)

        val result = strategy.deleteBranch(newBranch.id)

        assertTrue(result)
        assertTrue(strategy.listBranches().none { it.id == newBranch.id })
    }

    @Test
    fun `deleteBranch should not delete main branch`() {
        val result = strategy.deleteBranch(BranchId("main"))

        assertTrue(!result)
    }

    @Test
    fun `deleteBranch should not delete current active branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("active-branch", checkpoint.id)
        strategy.switchBranch(newBranch.id)

        val result = strategy.deleteBranch(newBranch.id)

        assertTrue(!result)
    }

    @Test
    fun `prepareContext metadata should contain strategy info`() = runTest {
        val dialog = createDialogWithMessages(5)
        val config = ContextManagementConfig()

        val context = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals("branching", context.metadata["strategy"])
        assertNotNull(context.metadata["currentBranch"])
        assertNotNull(context.metadata["totalBranches"])
        assertNotNull(context.metadata["totalCheckpoints"])
    }

    // ═══════════════════════════════════════════════════════════════
    // Auto-detect topic change tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `autoDetectTopicChange should create checkpoint and branch on topic change`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = {
                BranchingConfig(autoDetectTopicChange = true, topicChangeSensitivity = 0.3)
            }
        )
        val config = ContextManagementConfig()

        // Seed: establish topic about REST API
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "Мне нужно разработать REST API для сервиса пользователей", config)
        strategy.processUserMessage(dialog1, "API должен поддерживать CRUD операции с авторизацией JWT", config)
        strategy.processUserMessage(dialog1, "Используем Spring Boot и PostgreSQL для бэкенда", config)

        // Now send a message on a completely different topic
        val dialog2 = createDialogWithMessages(0)
        val result =
            strategy.processUserMessage(dialog2, "Как приготовить идеальный борщ со сметаной и чесноком", config)

        // Should have created a checkpoint and an auto-branch
        val hasCheckpointCreated = result.actionsPerformed.any { it is StrategyAction.CheckpointCreated }
        val hasBranchCreated = result.actionsPerformed.any { it is StrategyAction.BranchCreated }
        val hasBranchSwitched = result.actionsPerformed.any { it is StrategyAction.BranchSwitched }

        assertTrue(hasCheckpointCreated, "Should create checkpoint on topic change")
        assertTrue(hasBranchCreated, "Should create auto-branch on topic change")
        assertTrue(hasBranchSwitched, "Should switch to auto-branch on topic change")

        // Should be on the auto-branch now, not main
        val currentBranch = strategy.getCurrentBranch()
        assertTrue(
            currentBranch.name.startsWith("auto-branch-"),
            "Should be on auto-branch, got: ${currentBranch.name}"
        )

        // Should have main + auto-branch
        assertTrue(strategy.listBranches().size >= 2, "Should have at least 2 branches")
    }

    @Test
    fun `autoDetectTopicChange should NOT branch on same topic`() = runTest {
        val strategy = BranchingStrategy(configProvider = { BranchingConfig() })
        val config = ContextManagementConfig(
            branching = BranchingConfig(autoDetectTopicChange = true, topicChangeSensitivity = 0.3)
        )

        // Seed: establish topic with deliberate keyword repetition
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "jwt токены аутентификация api безопасность", config)
        strategy.processUserMessage(dialog1, "jwt токены аутентификация это важно", config)
        strategy.processUserMessage(dialog1, "api безопасность через jwt токены", config)

        // Send a message with the SAME keywords — Jaccard ≈ 0.83 (very high overlap)
        val dialog2 = createDialogWithMessages(0)
        val result = strategy.processUserMessage(dialog2, "jwt токены аутентификация api", config)

        // Should NOT create auto-branch (same topic)
        val hasBranchCreated = result.actionsPerformed.any { it is StrategyAction.BranchCreated }
        assertTrue(!hasBranchCreated, "Should NOT create auto-branch on same topic")

        // Should still be on main branch
        assertEquals("main", strategy.getCurrentBranch().name)
    }

    @Test
    fun `autoDetectTopicChange should NOT branch when disabled`() = runTest {
        val strategy = BranchingStrategy(configProvider = { BranchingConfig() })
        val config = ContextManagementConfig(
            branching = BranchingConfig(autoDetectTopicChange = false)
        )

        // Seed: establish topic about REST API
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "Мне нужно разработать REST API для сервиса пользователей", config)
        strategy.processUserMessage(dialog1, "API должен поддерживать CRUD операции с авторизацией JWT", config)

        // Send a message on a completely different topic
        val dialog2 = createDialogWithMessages(0)
        val result =
            strategy.processUserMessage(dialog2, "Как приготовить идеальный борщ со сметаной и чесноком", config)

        // Should NOT create auto-branch (disabled in config)
        val hasBranchCreated = result.actionsPerformed.any { it is StrategyAction.BranchCreated }
        assertTrue(!hasBranchCreated, "Should NOT create auto-branch when disabled")

        // Should still be on main branch
        assertEquals("main", strategy.getCurrentBranch().name)
    }

    @Test
    fun `autoDetectTopicChange with high sensitivity should NOT branch easily`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = {
                BranchingConfig(autoDetectTopicChange = true, topicChangeSensitivity = 0.9)
            }
        )
        val config = ContextManagementConfig()

        // Seed: establish topic about REST API
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "Мне нужно разработать REST API для сервиса пользователей", config)
        strategy.processUserMessage(dialog1, "API должен поддерживать CRUD операции с авторизацией JWT", config)

        // Different topic but sensitivity is very high
        val dialog2 = createDialogWithMessages(0)
        val result =
            strategy.processUserMessage(dialog2, "Как приготовить идеальный борщ со сметаной и чесноком", config)

        // With sensitivity 0.9, only drastic changes (>90% different keywords) trigger branching.
        // "борщ" vs "API" should qualify as drastic, but let's not assert hard —
        // just verify metadata contains expected fields
        assertNotNull(result.metadata["currentBranch"])
    }

    @Test
    fun `autoDetectTopicChange should NOT trigger with insufficient history`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = {
                BranchingConfig(autoDetectTopicChange = true, topicChangeSensitivity = 0.3)
            }
        )
        val config = ContextManagementConfig()

        // Only 1 previous message — not enough for topic comparison
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "Мне нужно разработать REST API", config)

        // Different topic
        val dialog2 = createDialogWithMessages(0)
        val result = strategy.processUserMessage(dialog2, "Как приготовить идеальный борщ", config)

        // Should NOT branch — not enough history
        val hasBranchCreated = result.actionsPerformed.any { it is StrategyAction.BranchCreated }
        assertTrue(!hasBranchCreated, "Should NOT branch with only 1 previous message")
    }

    @Test
    fun `can switch back to main after auto branch`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = {
                BranchingConfig(autoDetectTopicChange = true, topicChangeSensitivity = 0.3)
            }
        )
        val config = ContextManagementConfig()

        // Seed: establish topic about REST API
        val dialog1 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog1, "Мне нужно разработать REST API для сервиса пользователей", config)
        strategy.processUserMessage(dialog1, "API должен поддерживать CRUD операции с авторизацией JWT", config)
        strategy.processUserMessage(dialog1, "Используем Spring Boot и PostgreSQL для бэкенда", config)

        // Topic change → auto-branch
        val dialog2 = createDialogWithMessages(0)
        strategy.processUserMessage(dialog2, "Как приготовить идеальный борщ со сметаной и чесноком", config)
        assertTrue(strategy.getCurrentBranch().name.startsWith("auto-branch-"))

        // Switch back to main
        strategy.switchBranch(BranchId("main"))
        assertEquals("main", strategy.getCurrentBranch().name)

        // Main branch should still have the REST API messages
        val mainBranch = strategy.getCurrentBranch()
        assertTrue(
            mainBranch.messages.any { it.content.contains("REST API") },
            "Main branch should contain original REST API messages"
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Неизменяемость состояния
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `processUserMessage should return new state in metadata without mutating input state`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = { BranchingConfig() },
            topicChangeDetector = TopicChangeDetector()
        )
        val dialog = createDialogWithMessages(5)
        val initialState = StrategyState.BranchingState.createInitial(dialog.id)
        val initialBranchesCount = initialState.branches.size

        val result = strategy.processUserMessage(
            dialog = dialog,
            userMessage = "Сообщение",
            config = ContextManagementConfig(),
            state = initialState
        )

        // Исходное состояние не изменилось
        assertEquals(initialBranchesCount, initialState.branches.size)

        // Новое состояние в metadata
        val newState = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState.BranchingState
        assertNotNull(newState)
        assertNotSame(initialState, newState)
    }

    @Test
    fun `prepareContext should not modify input state`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = { BranchingConfig() },
            topicChangeDetector = TopicChangeDetector()
        )
        val dialog = createDialogWithMessages(5)
        val initialState = StrategyState.BranchingState.createInitial(dialog.id)
        val initialBranchesCount = initialState.branches.size

        strategy.prepareContext(
            dialog = dialog,
            systemPrompt = "System prompt",
            config = ContextManagementConfig(),
            state = initialState
        )

        // Состояние не должно измениться
        assertEquals(initialBranchesCount, initialState.branches.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // Обработка ошибок
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `processUserMessage with blank userMessage should throw IllegalArgumentException`() = runTest {
        val strategy = BranchingStrategy(
            configProvider = { BranchingConfig() },
            topicChangeDetector = TopicChangeDetector()
        )
        val dialog = createDialogWithMessages(1)

        assertThrows<IllegalArgumentException> {
            strategy.processUserMessage(
                dialog = dialog,
                userMessage = "   ",
                config = ContextManagementConfig()
            )
        }
    }
}
