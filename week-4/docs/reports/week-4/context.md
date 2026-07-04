# AI Challenge — Week 4: Application Context (LLM-optimised)

> **Purpose**: Single source of truth about the week-4 CLI application.
> **Architecture**: Clean Architecture (Hexagonal) with Domain, Application, Infrastructure, CLI layers.
> **Language**: Kotlin, Gradle (KTS), JVM.
> **Generated**: For LLM consumption — complete, self-contained, no prior context needed.

---

## 1. Project Overview

```
Package base: io.averkhogliad.ai.challenge.week4.cli
Main class:    AppKt (in App.kt)
Module:        week-4/cli
```

The application is an **interactive CLI chat agent** that connects to LLM APIs (OpenAI-compatible) and provides a rich
set of tools for task management, planning, RAG (Retrieval-Augmented Generation), MCP (Model Context Protocol)
integration, and more.

### 1.1 Build & Dependencies

| Dependency                                                   | Purpose                                                    |
|--------------------------------------------------------------|------------------------------------------------------------|
| `kotlinx-serialization-json`                                 | JSON encoding/decoding for all DTOs and tool calls         |
| `kotlinx-coroutines`                                         | Async operations (LLM calls, DB I/O)                       |
| `mordant`                                                    | Rich console UI (ANSI colors, spinners, tables)            |
| `common-core` (project)                                      | Shared utilities, `Config`/`ConfigProvider`, `LlmClient`   |
| `sqlite-jdbc`                                                | SQLite persistence with WAL mode                           |
| `mcp-sdk-client`                                             | MCP protocol client for external tool servers              |
| `ktor-client-cio`                                            | HTTP client (CIO engine, used by MCP SDK and REST clients) |
| `ktor-client-content-negotiation`                            | JSON content negotiation for REST clients                  |
| Testing: `common-test`, `kotest`, `mockk`, `coroutines-test` |

---

## 2. Architecture: Layered

### 2.1 Layer map

```
┌──────────────────────────────────────────────────────────────┐
│                    CLI Layer (Imperative Shell)              │
│  CliApplication  CliCommandDispatcher  CliRenderer           │
│  CommandParser   CommandHandler (CLI)  15 handler classes    │
├──────────────────────────────────────────────────────────────┤
│                  Application Layer (Orchestration)           │
│  DialogService   TodoTaskService   TaskStepService           │
│  MCPService      MemoryService    ProfileService             │
│  Task1Executor   Task2Executor     PlanCommandHandler         │
│  RagQueryProcessor  IndexingPipeline   ToolCallRouter         │
│  CachingInvariantService   CreateEventUseCase                │
├──────────────────────────────────────────────────────────────┤
│                  Domain Layer (Business Logic)               │
│  Models: Task, TaskStep, Profile, Fact, Message, DialogSession │
│  Ports: LlmPort, TaskRepository, MemoryService, MCPConnectionManager │
│  Configs: AppConfig, LlmConfig, TaskExecutionConfig          │
│  Indexer: IndexerConfig, Chunk, Document, Embedding, IndexingRun │
│  RAG: RagQueryProcessor, RagSessionState, VectorSearchPort   │
├──────────────────────────────────────────────────────────────┤
│              Infrastructure Layer (Adapters)                 │
│  SqliteTaskRepository, SqliteDialogSessionRepository, ...    │
│  LlmAdapter (→ LlmPort), ConfigAdapter (→ ConfigPort)        │
│  OllamaEmbedder, OpenAiEmbedder, FixedSizeChunker            │
│  DefaultMCPConnectionManager, RestEventsClient               │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Composition Root

**`ApplicationBootstrap.createApplication(config)`** — single place where all dependencies are wired.

Order: Infrastructure → Application services → CLI handlers → CliApplication.

Key wiring steps:

1. `ConfigAdapter` → `AppConfig` (domain config)
2. `LlmAdapter` (optional, null if no API key)
3. `SqliteDatabase` (shared connection) → all SQLite repositories
4. `MemoryService`, `TodoTaskService`, `ProfileService`, `MCPService`, `ToolCallRouter`, etc.
5. `DialogService` (core orchestrator — ties LLM + memory + tools + profiles + MCP)
6. `Task1Executor`, `Task2Executor` → `CommandHandler`
7. CLI handlers → `CliCommandDispatcher` → `CliApplication`

---

## 3. CLI Layer (Imperative Shell)

### 3.1 Main entry point

**`App.kt`** — `fun main()`:

- Loads config via `ConfigProvider` (classpath → user home → working dir → CLI arg `--config=`)
- Calls `ApplicationBootstrap.createApplication(config)`
- Runs `app.use { it.run() }` — AutoCloseable lifecycle

### 3.2 `CliApplication` — REPL loop (week-4/cli/src/main/kotlin/cli/CliApplication.kt)

- `run()` — runs `repl()` inside `runBlocking`
- `repl()` loop: render prompt → readLine → `CommandParser.parse()` → `dispatcher.handle()` → update state
- `CliState` — immutable state of the CLI:
    - `currentTaskId`, `currentTodoTaskId`, `currentDialogId`
    - `executionConfig` (temperature, maxTokens, stopSequences)
    - `ragState` (enabled, topK, similarityThreshold)
    - `taskListMode`, `viewMode`
    - `pendingProfileCreation`, `pendingProfileEdit`, `multilineInputBuffer`

### 3.3 `CliRenderer` (interface) / `ConsoleCliRenderer` (impl)

Methods:

- `renderMenu()`, `renderTaskHeader()`, `renderPrompt()`
- `renderResult()`, `renderError()`, `renderInfo()`
- `renderHelp()`, `renderParameters()`
- `renderLoadingStart()` / `renderLoadingStop()` (animated spinner with frames)
- Delegates specialised rendering to: `ProfileRenderer`, `TaskRenderer`, `InvariantRenderer`, `FsmRenderer`

### 3.4 `Command` — sealed interface (week-4/cli/src/main/kotlin/cli/commands/Command.kt)

**~70 command types** organised by category:

| Category          | Examples                                                                                                         |
|-------------------|------------------------------------------------------------------------------------------------------------------|
| Global            | `Help`, `Back`, `Quit`, `SelectTask`                                                                             |
| LLM params        | `SetTemperature`, `SetMaxTokens`, `SetStopSequences`, `ResetParameters`                                          |
| Task CRUD         | `AddTask`, `ListTasks`, `EditTask`, `DropTask`, `OpenTask`, `CloseTask`, `CancelTask`                            |
| Task Steps        | `AddStep`, `ListSteps`, `CompleteStep`                                                                           |
| Memory            | `ClearMemory`, `ShowStatus`                                                                                      |
| LTM               | `SaveFact`, `ListLtmFacts`, `ForgetFact`, `SearchFacts`                                                          |
| Planning          | `Plan`, `PlanSteps`                                                                                              |
| FSM               | `ShowState`, `Abort`, `Goto`, `GotoState`                                                                        |
| Debug             | `Debug(on\|off)`                                                                                                 |
| Profiles          | `ProfileNew`, `ProfileList`, `ProfileUse`, `ProfileEdit`, `ProfileDelete`, `ProfileShow`                         |
| MCP               | `McpAddServer`, `McpRemoveServer`, `McpListServers`, `McpConnectServer`, `McpDisconnectServer`, `McpToolsServer` |
| Invariants        | `InvariantAdd`, `InvariantList`, `InvariantRemove`                                                               |
| Events            | `CreateEvent`, `ListNotes`                                                                                       |
| Indexer           | `Index`, `IndexRuns`, `IndexSwitch`, `IndexStats`, `IndexCompare`, `IndexDelete`, `IndexClear`                   |
| RAG               | `Rag(status\|list\|...)`                                                                                         |
| User Input        | `UserInput(text)` — plain text prompt to LLM                                                                     |
| Legacy (disabled) | Strategy, Branch, Checkpoint, Compression, Dialog commands → show "temporarily unavailable"                      |

### 3.5 `CommandParser` (week-4/cli/src/main/kotlin/cli/commands/CommandParser.kt)

- Pure function: `parse(input: String, context: CommandContext) → Command`
- If input starts with `:` → parse as command; else → `UserInput` or `SelectTask`
- `CommandContext` defines available commands per state (`TASK_SELECTION`, `ACTIVE_COMMANDS`)
- Global commands always available: `help`, `quit`, `back`, `debug`, `state`, `abort`, MCP commands, `notes`

### 3.6 `CliCommandDispatcher` (week-4/cli/src/main/kotlin/cli/CliCommandDispatcher.kt)

- Big `when (command)` exhaustive dispatch to **all** handlers
- Maintains 4 fields: `renderer`, `handlers` (aggregate), `userInputFlowHandler`, `planFlowHandler`

### 3.7 `CliCommandHandlers` — data class (week-4/cli/src/main/kotlin/cli/CliCommandHandlers.kt)

Contains 13 handler references:

- `command` (CommandHandler), `debug`, `todoTask`, `taskStep`, `memory`, `ltm`, `fsm`, `invariant`, `profile`, `mcp`,
  `events`, `indexer`, `rag`

### 3.8 Flow Handlers

**`UserInputFlowHandler`** (week-4/cli/src/main/kotlin/cli/handlers/UserInputFlowHandler.kt):

- Orchestrates the prompt → response flow
- If has active FSM command → delegates to `PlanCommandHandler.handleUserInput()`
- If in todo-task mode → calls `DialogService.chat()`
- If Task 2 with RAG enabled → calls `RagQueryProcessor.process()` directly
- Otherwise → delegates to `CommandHandler.executeUserInput()` → executor

**`PlanFlowHandler`** (week-4/cli/src/main/kotlin/cli/handlers/PlanFlowHandler.kt):

- `handlePlan()` — starts FSM-based planning via `PlanCommandHandler.execute()`
- `handlePlanSteps()` — calls `DialogService.planSteps()` for one-shot plan generation

**`CommandHandler`** (week-4/cli/src/main/kotlin/cli/handlers/CommandHandler.kt):

- `handle(Command, CliState) → CliState` — processes state-only commands (Quit, SelectTask, SetTemperature, etc.)
- `executeUserInput()` — routes prompt to active `TaskExecutor`
- Holds `Map<TaskId, TaskExecutor>` and provides `getAllExecutors()`

---

## 4. Application Layer

### 4.1 `DialogService` (week-4/cli/src/main/kotlin/application/DialogService.kt)

**Central orchestrator** for LLM conversations. Injected into Task1Executor, Task2Executor.

Dependencies:

- `LlmPort?` — nullable; if null → returns error "LLM не настроен"
- `MemoryService` — for getting/storing dialog history
- `PromptBuilder` — builds chat messages with context
- `ProfileRepository` — current active profile
- `InvariantService` — active guard rules
- `MCPService` — MCP prompts and tools
- `ToolCallRouter` — routes tool calls to builtin or MCP handlers
- `ToolRegistry` — builtin tool definitions
- `PromptPresetAggregator` — merges builtin + MCP presets
- `TaskRepository` — task context for LLM

Key method: `chat(userInput, level, taskId)`:

1. Gets full memory context (STM + WM + relevant LTM facts)
2. Collects active profile, invariants, MCP prompts, presets
3. Builds chat messages via `PromptBuilder.buildChatMessages()`
4. Collects all tools (builtin + MCP)
5. Calls `LlmPort.chatWithMessages()` with tool definitions
6. Handles tool call iterations (max 20) — routes via `ToolCallRouter`
7. Saves assistant response to memory
8. Returns `TaskResult`

### 4.2 `TodoTaskService` (week-4/cli/src/main/kotlin/application/service/TodoTaskService.kt)

CRUD for todo tasks (Task model):

- `addTask(title, description?)` → generates UUID, saves
- `listTasks()` → `findAll()`
- `editTask(id?, title)`, `dropTask(id?)`, `openTask(id)`, `closeTask(id?)`, `cancelTask(id?)`
- `currentTaskId` — context-aware state (when no explicit ID, uses opened task)

### 4.3 `TaskStepService` (week-4/cli/src/main/kotlin/application/service/TaskStepService.kt)

Manages steps within a task:

- `addStep(taskId, text)`, `listSteps(taskId)`, `completeStep(stepId)`
- Steps have `TaskStepId`, `text`, `isCompleted`, `stepOrder`, `createdAt`

### 4.4 `MCPService` (week-4/cli/src/main/kotlin/application/service/MCPService.kt)

Manages MCP servers (add/remove/connect/disconnect/list):

- `addServer(name, transport)` — validates name format and transport requirements
- `removeServer(id)` — disconnects first, then deletes
- `connect(id)`, `disconnect(id)`, `getStatus(id)`, `listServers()`
- Collects tools/prompts from connected servers

### 4.5 `ProfileService` (week-4/cli/src/main/kotlin/application/ProfileService.kt)

User profiles (instructions/description injected into LLM prompts):

- `create(name)`, `list()`, `activate(name)`, `deactivate()`, `edit(name, description, instructions)`, `delete(name)`,
  `show(name?)`

### 4.6 Executors

**`TaskExecutor`** interface:

- `val taskId: TaskId`, `val metadata: TaskMetadata`
- `suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult`

**`Task1Executor`** — "Task 1: AI Agent with LLM"

- Delegates to `DialogService.chat(level=TASK_LIST, taskId=null)`

**`Task2Executor`** — "Task 2: LLM-диалог с поддержкой RAG"

- Also delegates to `DialogService.chat()` with same params
- RAG integration happens at CLI level via `UserInputFlowHandler` (checks `state.currentTaskId == 2`)

### 4.7 Planning

**`PlanCommandHandler`** — FSM-based planning flow:

- States: PLANNING → EXECUTION → VALIDATION → DONE
- Uses `FactCollector` + `LlmPlanner` + `StepParser`
- `execute(taskId?)` — starts FSM
- `handleUserInput(input)` — processes user input during different FSM stages

**`LlmPlanner`** — builds a planning prompt and calls LLM:

- Returns `PlanResult.Success`, `PlanResult.InvariantRefusal`, or `PlanResult.Error`
- Detects invariant conflicts in LLM responses

### 4.8 RAG System

**`RagQueryProcessor`** (week-4/cli/src/main/kotlin/application/rag/RagQueryProcessor.kt):
Full RAG pipeline:

1. Check `RagSessionState.enabled` — if disabled, fallback to plain LLM
2. Get active index run from `IndexRepository`
3. Generate query embedding via `EmbeddingGenerator`
4. Vector search via `VectorSearchPort` (cosine similarity, threshold `0.7`, top-K `5`)
5. Build augmented prompt via `RagPromptBuilder`
6. Send to LLM with context
7. Return `RagAnswer` with answer, sources, fallback flags

Graceful degradation: NO_ACTIVE_INDEX, EMPTY_SEARCH, EMBEDDING_ERROR, SEARCH_ERROR, LLM_ERROR → all fall back to plain
LLM.

### 4.9 Indexing System

**`IndexingPipeline`** (week-4/cli/src/main/kotlin/application/indexer/IndexingPipeline.kt):
Document indexing pipeline: `extract → chunk → embed → save`

Components:

- `DocumentLoader` — loads documents from filesystem
- `DocumentExtractor` (interface) — `TextExtractor`, `MarkdownExtractor`, `HtmlExtractor`
- `ChunkingStrategy` (interface) — `FixedSizeChunker` (500 chars, 50 overlap) + `StructuralChunker`
- `EmbeddingGenerator` (interface) — `OllamaEmbedder` / `OpenAiEmbedder`
- `IndexRepository` — `SqliteIndexRepository`

`IndexingRun` — tracks a single indexing operation with `RunStatus`, `totalChunks`, `totalDocuments`, `strategy`,
timestamps.

### 4.10 Tools System

**`ToolRegistry`** — holds 6 builtin tools:

1. `GetCurrentTaskTool` — returns current task context
2. `CreateTaskTool` — creates a new todo task
3. `UpdateTaskTool` — updates task properties
4. `AddTaskStepTool` — adds step to task
5. `ListTaskStepsTool` — lists steps of task
6. `LinkTaskToEventTool` — links task to calendar event

**`ToolCallRouter`** — routes tool calls by namespace:

- `cli::*` → builtin tools via `ToolRegistry`
- `weather::*`, `events::*`, `notifications::*` → MCP system servers
- Other → tries all connected MCP servers

**`BuiltinToolDefinition`** — name + description + JSON Schema params (for LLM function calling).

---

## 5. Domain Layer

### 5.1 Core Domain Models

| Model                           | Key Fields                                                                                           | Status                             |
|---------------------------------|------------------------------------------------------------------------------------------------------|------------------------------------|
| `Task`                          | `id: TaskId`, `title`, `description?`, `status: TaskStatus`, `eventId: UUID?`, `dueDate: LocalDate?` | OPEN, CLOSED, CANCELLED            |
| `TaskStep`                      | `id: TaskStepId`, `taskId`, `text`, `isCompleted`, `stepOrder`                                       | —                                  |
| `TaskId`                        | `value: String` (UUID) — inline value class                                                          | —                                  |
| `Profile`                       | `id: ProfileId`, `name`, `description`, `instructions`, `isActive`                                   | —                                  |
| `Fact`                          | `id: FactId`, `content`, `createdAt`                                                                 | LTM fact                           |
| `Message`                       | `id`, `sessionId`, `role: MessageRole`, `content`, `timestamp`                                       | SYSTEM/USER/ASSISTANT              |
| `DialogSession`                 | `id: SessionId`, `level: SessionLevel`, `taskId?`, `messages`, `createdAt`                           | —                                  |
| `MCPServerConfig`               | `id: ModelId`, `name`, `transport: MCPTransport`, `enabled`                                          | —                                  |
| `MCPTool`                       | `name`, `description?`, `parametersSchema` (JSON string)                                             | —                                  |
| `McpPrompt`                     | `name`, `description?`, `arguments`                                                                  | —                                  |
| `Invariant`                     | `id: InvariantId`, `rule: String`, `createdAt`                                                       | Guard rules                        |
| `DebugMode`                     | flag for verbose FSM output                                                                          | —                                  |
| `CommandState` / `CommandStage` | FSM states for plan command                                                                          | Planning/Execution/Validation/Done |

### 5.2 Domain Ports (Interfaces)

| Port                      | Purpose                                         | Infra Impl(s)                                          |
|---------------------------|-------------------------------------------------|--------------------------------------------------------|
| `LlmPort`                 | LLM chat (single/multi-message) + model listing | `LlmAdapter`                                           |
| `TaskRepository`          | CRUD for tasks                                  | `SqliteTaskRepository`                                 |
| `TaskStepRepository`      | CRUD for task steps                             | `SqliteTaskStepRepository`                             |
| `DialogSessionRepository` | Dialog session persistence                      | `SqliteDialogSessionRepository`                        |
| `FactRepository`          | LTM fact persistence                            | `SqliteFactRepository`                                 |
| `ProfileRepository`       | Profile persistence                             | `SqliteProfileRepository`, `InMemoryProfileRepository` |
| `InvariantRepository`     | Invariant persistence                           | `SqliteInvariantRepository`                            |
| `MCPServerRepository`     | MCP server config persistence                   | `SqliteMCPServerRepository`                            |
| `MCPConnectionManager`    | MCP connect/disconnect/tools/prompts            | `DefaultMCPConnectionManager`                          |
| `ConfigPort`              | Load AppConfig from properties                  | `ConfigAdapter`                                        |
| `IndexRepository`         | Indexing runs + chunks                          | `SqliteIndexRepository`                                |
| `DocumentExtractor`       | Text extraction                                 | `TextExtractor`, `MarkdownExtractor`, `HtmlExtractor`  |
| `ChunkingStrategy`        | Document chunking                               | `FixedSizeChunker`, `StructuralChunker`                |
| `EmbeddingGenerator`      | Embedding generation                            | `OllamaEmbedder`, `OpenAiEmbedder`                     |
| `VectorSearchPort`        | Cosine similarity search                        | `InMemoryCosineSearchAdapter`                          |
| `CommandEngine`           | FSM engine                                      | `DefaultCommandEngine`                                 |
| `EventsClient`            | REST client for calendar events                 | `RestEventsClient`                                     |
| `NotificationsClient`     | REST client for notifications                   | `RestNotificationsClient`                              |

### 5.3 Domain Configs

```kotlin
AppConfig(llm: LlmConfig, defaultExecution: TaskExecutionConfig, replTimeoutSeconds: Long)
LlmConfig(baseUrl, apiKey, defaultModelId, defaultTemperature=0.7, defaultMaxTokens=500, timeoutSeconds=60)
TaskExecutionConfig(temperature=0.7, maxTokens=500, stopSequences=[], modelId?)
IndexerConfig(chunkSize=500, overlap=50, embedding: EmbeddingConfig)
EmbeddingConfig(batchSize, timeoutSeconds, retryAttempts=3, retryInitialDelayMs=1000, providerConfig)
EmbeddingProviderConfig — sealed: Ollama(baseUrl, model) | OpenAi(baseUrl, model, apiKey)
ServicesConfig(eventsBaseUrl, notificationsBaseUrl)
BenchmarkConfig(...), ServerPaths(...), TaskExecutionConfig(...)
```

---

## 6. Infrastructure Layer

### 6.1 Persistence (SQLite)

All repositories share a single `SqliteDatabase` instance (shared JDBC connection):

- WAL journal mode, synchronous=NORMAL
- Path: `~/.ai-challenge/week4.db` (configurable via `app.database.path`)
- `AutoCloseable` — lifecycle managed by composition root

Tables (defined in `schema.sql` and per-repo initialisers):

- `tasks` — `id`, `title`, `status`, `created_at`, `updated_at`, `event_id`, `due_date`
- `task_steps` — `id`, `task_id`, `text`, `is_completed`, `step_order`, `created_at`
- `mcp_servers` — `id`, `name`, `transport_type`, `transport_config`, `enabled`, `created_at`
- `dialog_sessions` — `id`, `level`, `task_id`, `created_at`, `updated_at`
- `messages` — `id`, `session_id`, `role`, `content`, `timestamp`
- `facts` — `id`, `content`, `created_at`
- `profiles` — `id`, `name`, `description`, `instructions`, `is_active`, `created_at`, `updated_at`
- `invariants` — `id`, `rule`, `created_at`
- `index_runs`, `indexed_chunks`, `index_embeddings` — indexer tables

### 6.2 LLM Adapter

**`LlmAdapter`** — implements `LlmPort`:

- Wraps `DefaultLlmClient` (from common-core)
- Configures rate limiting (0.5s min interval, 60 req/min)
- Supports tool definitions in chat calls
- Converts between domain and infra DTOs

### 6.3 Config Adapter

**`ConfigAdapter`** — implements `ConfigPort`:

- Reads `application.properties` via `ConfigProvider`
- Maps to `AppConfig`, `LlmConfig`, `TaskExecutionConfig`

### 6.4 MCP

**`DefaultMCPConnectionManager`** — MCP protocol implementation:

- Uses MCP SDK client (`io.modelcontextprotocol.kotlin.sdk`)
- Supports `StreamableHttp` transport (for system servers) and `Stdio` transport (for user-added servers)
- System servers: `system-weather`, `system-events`, `system-notifications` (connected at startup, not saved to DB)
- Methods: `connect`, `connectSystem`, `disconnect`, `getTools`, `callTool`, `getPrompts`, `getPrompt`

**`MCPClientAdapter`** — wraps individual MCP client instances.

### 6.5 REST Clients

**`RestEventsClient`** — HTTP client for calendar events service:

- Base URL from `services.events.base-url` (default: `http://localhost:8081`)
- Uses Ktor CIO + ContentNegotiation + kotlinx.serialization

**`RestNotificationsClient`** — HTTP client for notifications service:

- Base URL from `services.notifications.base-url` (default: `http://localhost:8083`)

### 6.6 Indexer

- **Embedders**: `OllamaEmbedder` (health check via `/api/tags`), `OpenAiEmbedder` (health check via `/models`)
- **Chunkers**: `FixedSizeChunker` (configurable size + overlap), `StructuralChunker` (markdown heading-based)
- **Extractors**: `TextExtractor` (plain text), `MarkdownExtractor` (preserves structure), `HtmlExtractor` (JSoup-based)
- **IndexRepository**: `SqliteIndexRepository` — persists runs, chunks, embeddings in SQLite

### 6.7 Vector Search

**`InMemoryCosineSearchAdapter`** — implements `VectorSearchPort`:

- Loads all chunks for a run from `IndexRepository`
- Computes cosine similarity in-memory
- Filters by threshold (default 0.7), returns top-K
- Suitable for MVP (<10K chunks)

### 6.8 Tool Implementations

6 builtin tool classes in `infrastructure/tool/`:

- All extend `BaseBuiltinTool` and implement `BuiltinToolExecutor`
- Each provides: `definition` (name + JSON Schema), `execute(context)` returning `ToolCallResult`
- JSON Schema and task serialization via `TaskJsonSerializer`

---

## 7. Config Loading Strategy

Config files loaded in order (later overrides earlier):

1. `application.properties` from classpath (JAR defaults)
2. `~/.ai-challenge/application.properties` (user-level)
3. `./application.properties` (project working dir)
4. `./config/application.properties` (project config dir)
5. `--config=<path>` CLI argument (highest priority)

Key properties:

- `api.base-url`, `api.key`, `api.model` — LLM connection
- `api.connect-timeout=PT30S`, `api.request-timeout=PT60S`
- `api.context-window=16384`
- `models` — comma-separated list of available models
- `api.rate-limit.enabled=true`
- `feature.use-new-architecture=true`
- `services.events.base-url`, `services.notifications.base-url`
- Indexer: `indexer.*` embedding config

---

## 8. System Architecture Diagram (Data Flow)

```
User Input
    │
    ▼
CliApplication (REPL loop)
    │  readLine() → CommandParser.parse()
    ▼
CliCommandDispatcher.handle()
    │
    ├── Command → direct handler (e.g. :help, :quit, :add)
    │
    ├── UserInput → UserInputFlowHandler.handle()
    │       │
    │       ├── if Task 2 + RAG → RagQueryProcessor.process()
    │       │       │  embedding → vector search → augmented prompt → LLM → answer + sources
    │       │
    │       └── else → CommandHandler.executeUserInput() → TaskExecutor.execute()
    │               │
    │               └── DialogService.chat()
    │                       │  memory context + profile + invariants + tools → LLM
    │                       └── tool call loop (max 20): ToolCallRouter → builtin/MCP tools
    │
    └── Plan → PlanFlowHandler → PlanCommandHandler (FSM)
            PLANNING → EXECUTION → VALIDATION → DONE
```

---

## 9. Naming Conventions & Code Style

- Package: `io.averkhogliad.ai.challenge.week4.cli.{layer}.{subdomain}`
- All documentation in **Russian** (comments, error messages, help text)
- Architecture terms in English (domain, application, infrastructure, port, adapter)
- Clean Architecture layers: `domain/` (models + ports), `application/` (services + use cases), `infrastructure/` (
  adapters), `cli/` (UI)
- Models are **immutable** (data class with `copy()` for updates)
- Ports (interfaces) live in `domain/service/` or `domain/indexer/port/` or `domain/rag/port/`
- Repositories use `suspend fun` with `Dispatchers.IO` for blocking JDBC
- CLI handlers use imperative shell pattern (mutability isolated in `CliState`)