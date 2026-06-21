CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
