CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    event_id TEXT,
    due_date TEXT
);

CREATE INDEX IF NOT EXISTS idx_tasks_event_id ON tasks(event_id);
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date);

CREATE TABLE IF NOT EXISTS task_steps (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    text TEXT NOT NULL,
    is_completed INTEGER NOT NULL DEFAULT 0,
    step_order INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mcp_servers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    transport_type TEXT NOT NULL CHECK(transport_type IN ('STDIO', 'STREAMABLE_HTTP')),
    transport_config TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
